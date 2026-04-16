package com.example.psu.service;

import com.example.psu.dto.request.CompositionRenderRequest;
import com.example.psu.dto.request.CompositionSaveRequest;
import com.example.psu.dto.response.CompositionRenderResponse;
import com.example.psu.dto.response.CompositionResponse;
import com.example.psu.dto.response.CompositionValidateResponse;
import com.example.psu.entity.JsonSchema;
import com.example.psu.entity.PromptComposition;
import com.example.psu.entity.PromptCompositionRevision;
import com.example.psu.entity.PsuUnit;
import com.example.psu.enums.CompositionStatus;
import com.example.psu.enums.RejectionType;
import com.example.psu.repository.JsonSchemaRepository;
import com.example.psu.repository.PromptCompositionRepository;
import com.example.psu.repository.PromptCompositionRevisionRepository;
import com.example.psu.repository.PsuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编排服务
 */
@Service
public class CompositionService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    @Autowired
    private PromptCompositionRepository compositionRepository;

    @Autowired
    private PromptCompositionRevisionRepository revisionRepository;

    @Autowired
    private PsuRepository psuRepository;

    @Autowired
    private JsonSchemaRepository jsonSchemaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取当前草稿
     */
    public CompositionResponse getDraft(Long psuId) {
        // 校验PSU存在，避免返回脏数据。
        psuRepository.findById(psuId).orElseThrow(() -> new RuntimeException("PSU not found: " + psuId));
        PromptComposition composition = compositionRepository.findByPsuId(psuId).orElse(null);
        if (composition == null) {
            return null;
        }
        return toResponse(composition);
    }

    /**
     * 保存草稿
     */
    @Transactional
    public CompositionResponse saveDraft(Long psuId, CompositionSaveRequest request, Long userId) {
        // 校验PSU存在，避免产生悬挂记录。
        psuRepository.findById(psuId).orElseThrow(() -> new RuntimeException("PSU not found: " + psuId));
        PromptComposition composition = compositionRepository.findByPsuId(psuId).orElseGet(PromptComposition::new);

        // 非草稿状态禁止保存，但允许业务驳回后回到新草稿继续编辑。
        if (composition.getId() != null && composition.getStatus() != CompositionStatus.DRAFT) {
            if (!(composition.getStatus() == CompositionStatus.REJECTED && composition.getRejectionType() == RejectionType.BACK_TO_BIZ)) {
                throw new RuntimeException("当前编排已锁定，不能保存草稿");
            }
        }

        JsonSchema latestSchema = jsonSchemaRepository.findTopByPsuIdOrderByVersionDesc(psuId)
            .orElseThrow(() -> new RuntimeException("Schema not found for PSU: " + psuId));

        Map<String, Object> normalizedSpec = buildSpec(request);
        CompositionValidateResponse validation = validateSpec(psuId, normalizedSpec);
        if (!validation.isOk()) {
            throw new RuntimeException("编排校验失败，无法保存草稿");
        }

        // 写入草稿主记录并更新最新版本号。
        composition.setPsuId(psuId);
        composition.setStatus(CompositionStatus.DRAFT);
        composition.setSchemaVersion(latestSchema.getVersion());
        composition.setContent(request.getContent() == null ? "" : request.getContent());
        composition.setSpecJson(writeJson(normalizedSpec));
        composition.setUpdatedBy(userId);
        if (composition.getId() == null) {
            composition.setCreatedBy(userId);
        }

        return toResponse(compositionRepository.save(composition));
    }

    /**
     * 校验编排
     */
    public CompositionValidateResponse validate(Long psuId, CompositionSaveRequest request) {
        // 组装统一spec后复用核心校验逻辑。
        Map<String, Object> spec = buildSpec(request);
        return validateSpec(psuId, spec);
    }

    /**
     * 渲染预览
     */
    public CompositionRenderResponse render(Long psuId, CompositionRenderRequest request) {
        // 优先取入参指定版本，未指定时取当前草稿。
        PromptComposition composition;
        if (request.getCompositionId() != null) {
            composition = compositionRepository.findById(request.getCompositionId())
                .orElseThrow(() -> new RuntimeException("Composition not found: " + request.getCompositionId()));
            if (!composition.getPsuId().equals(psuId)) {
                throw new RuntimeException("Composition does not belong to PSU");
            }
        } else {
            composition = compositionRepository.findByPsuId(psuId)
                .orElseThrow(() -> new RuntimeException("Composition not found for PSU: " + psuId));
        }

        String content = Optional.ofNullable(composition.getContent()).orElse("");
        Matcher matcher = VAR_PATTERN.matcher(content);
        Set<String> usedVars = new LinkedHashSet<>();
        List<String> missingVars = new ArrayList<>();
        StringBuilder rendered = new StringBuilder();
        int last = 0;
        Map<String, Object> input = request.getInput() == null ? Collections.emptyMap() : request.getInput();

        // 扫描占位符并按输入参数替换，缺失变量会被记录。
        while (matcher.find()) {
            rendered.append(content, last, matcher.start());
            String path = matcher.group(1).trim();
            usedVars.add(path);
            Object value = getPathValue(input, path);
            if (value == null) {
                missingVars.add(path);
                rendered.append("{{").append(path).append("}}");
            } else {
                rendered.append(value);
            }
            last = matcher.end();
        }
        rendered.append(content.substring(last));

        CompositionRenderResponse response = new CompositionRenderResponse();
        response.setRenderedPrompt(rendered.toString());
        response.setMissingVars(missingVars);
        response.setUsedVars(new ArrayList<>(usedVars));
        return response;
    }

    /**
     * 提交审核
     */
    @Transactional
    public CompositionResponse submit(Long psuId, Long userId) {
        // 仅草稿态允许提交，提交时会固化快照并锁定。
        PromptComposition composition = compositionRepository.findByPsuId(psuId)
            .orElseThrow(() -> new RuntimeException("Composition not found for PSU: " + psuId));
        if (composition.getStatus() != CompositionStatus.DRAFT) {
            throw new RuntimeException("仅草稿态允许提交");
        }

        CompositionSaveRequest request = parseSaveRequest(composition);
        CompositionValidateResponse validation = validate(psuId, request);
        if (!validation.isOk()) {
            throw new RuntimeException("校验未通过，不能提交审核");
        }

        JsonSchema latestSchema = jsonSchemaRepository.findTopByPsuIdOrderByVersionDesc(psuId)
            .orElseThrow(() -> new RuntimeException("Schema not found for PSU: " + psuId));
        composition.setSchemaVersion(latestSchema.getVersion());
        composition.setStatus(CompositionStatus.SUBMITTED);
        composition.setUpdatedBy(userId);
        composition.setRejectionReason(null);
        composition.setRejectionType(null);
        PromptComposition saved = compositionRepository.save(composition);
        saveRevision(saved, userId);
        return toResponse(saved);
    }

    /**
     * 审核状态回写
     */
    @Transactional
    public void updateStatusFromReview(Long psuId, CompositionStatus newStatus) {
        updateStatusFromReview(psuId, newStatus, null, null);
    }

    /**
     * 审核状态回写（含驳回信息）
     */
    @Transactional
    public void updateStatusFromReview(Long psuId, CompositionStatus newStatus, String rejectionReason, RejectionType rejectionType) {
        // 审核流事件驱动编排状态变化，保持前后端状态一致。
        PromptComposition composition = compositionRepository.findByPsuId(psuId).orElse(null);
        if (composition == null) {
            return;
        }
        composition.setStatus(newStatus);
        if (newStatus == CompositionStatus.REJECTED) {
            composition.setRejectionReason(rejectionReason);
            composition.setRejectionType(rejectionType);
        } else {
            composition.setRejectionReason(null);
            composition.setRejectionType(null);
        }
        compositionRepository.save(composition);
    }

    private CompositionValidateResponse validateSpec(Long psuId, Map<String, Object> spec) {
        // 基于schema做变量存在性校验，同时检查注入计划一致性。
        CompositionValidateResponse response = new CompositionValidateResponse();
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        JsonSchema schema = jsonSchemaRepository.findTopByPsuIdOrderByVersionDesc(psuId)
            .orElseThrow(() -> new RuntimeException("Schema not found for PSU: " + psuId));

        Set<String> schemaPaths = extractSchemaPaths(schema.getSchemaContent());
        List<Map<String, Object>> tokens = castList(spec.get("tokens"));
        Set<String> tokenVars = new LinkedHashSet<>();
        for (Map<String, Object> token : tokens) {
            if (!"VAR".equals(String.valueOf(token.get("type")))) {
                continue;
            }
            String path = String.valueOf(token.get("path"));
            tokenVars.add(path);
            if (!schemaPaths.contains(path) && !containsRootPath(schemaPaths, path)) {
                errors.add(error("VAR_NOT_FOUND", "变量不存在于Schema", path));
            }
        }

        List<Map<String, Object>> planList = castList(spec.get("injectionPlan"));
        Set<String> planVars = new LinkedHashSet<>();
        for (Map<String, Object> item : planList) {
            if (item.get("path") != null) {
                planVars.add(String.valueOf(item.get("path")));
            }
        }

        if (!tokenVars.equals(planVars)) {
            errors.add(error("PLAN_TOKEN_MISMATCH", "injectionPlan与tokens变量集合不一致", null));
        }

        if (tokenVars.isEmpty()) {
            warnings.add(error("EMPTY_TOKENS", "当前Prompt未引用任何变量", null));
        }

        response.setErrors(errors);
        response.setWarnings(warnings);
        response.setOk(errors.isEmpty());
        return response;
    }

    private void saveRevision(PromptComposition composition, Long userId) {
        // 按提交节点生成不可变快照，支持后续审计和复现。
        int revisionNo = revisionRepository.findTopByCompositionIdOrderByRevisionNoDesc(composition.getId())
            .map(PromptCompositionRevision::getRevisionNo)
            .orElse(0) + 1;

        PromptCompositionRevision revision = new PromptCompositionRevision();
        revision.setCompositionId(composition.getId());
        revision.setPsuId(composition.getPsuId());
        revision.setRevisionNo(revisionNo);
        revision.setStatusAtTime(composition.getStatus());
        // 兼容历史库字段schema_version（非空），避免提交快照时插入失败。
        revision.setSchemaVersion(composition.getSchemaVersion());
        revision.setSchemaVersionAtTime(composition.getSchemaVersion());
        revision.setContentSnapshot(Optional.ofNullable(composition.getContent()).orElse(""));
        revision.setSpecJsonSnapshot(Optional.ofNullable(composition.getSpecJson()).orElse("{}"));
        revision.setCreatedBy(userId);
        revisionRepository.save(revision);
    }

    private CompositionResponse toResponse(PromptComposition composition) {
        // 输出DTO时反序列化specJson，前端可直接消费。
        CompositionResponse response = new CompositionResponse();
        response.setId(composition.getId());
        response.setPsuId(composition.getPsuId());
        response.setStatus(composition.getStatus());
        response.setSchemaVersion(composition.getSchemaVersion());
        response.setContent(composition.getContent());
        response.setSpecJson(readJsonMap(composition.getSpecJson()));
        response.setUpdatedAt(composition.getUpdatedAt());
        response.setRejectionReason(composition.getRejectionReason());
        response.setRejectionType(composition.getRejectionType());
        return response;
    }

    private Map<String, Object> buildSpec(CompositionSaveRequest request) {
        // 统一兼容两种前端入参：直接specJson或扁平字段。
        if (request.getSpecJson() != null && !request.getSpecJson().isEmpty()) {
            return request.getSpecJson();
        }
        Map<String, Object> spec = new HashMap<>();
        spec.put("tokens", request.getTokens() == null ? new ArrayList<>() : request.getTokens());
        spec.put("injectionPlan", request.getInjectionPlan() == null ? new ArrayList<>() : request.getInjectionPlan());
        spec.put("assembledFragments", request.getAssembledFragments() == null ? new ArrayList<>() : request.getAssembledFragments());
        if (request.getContent() != null) {
            spec.put("content", request.getContent());
        }
        return spec;
    }

    private CompositionSaveRequest parseSaveRequest(PromptComposition composition) {
        // 从已存草稿恢复请求结构，供提交前复用校验。
        CompositionSaveRequest request = new CompositionSaveRequest();
        request.setContent(composition.getContent());
        Map<String, Object> spec = readJsonMap(composition.getSpecJson());
        request.setSpecJson(spec);
        return request;
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Set<String> extractSchemaPaths(String schemaContent) {
        Set<String> paths = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(schemaContent);
            if (root.has("properties")) {
                walkSchemaNode(root.get("properties"), "", paths);
            } else if (root.isObject()) {
                walkSchemaNode(root, "", paths);
            }
        } catch (Exception ignored) {
            // Schema异常时由上游统一报错，这里不重复抛出。
        }
        return paths;
    }

    private void walkSchemaNode(JsonNode node, String prefix, Set<String> paths) {
        // 递归遍历对象与数组，生成path索引集合。
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            String currentPath = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(currentPath);
            if (value.has("properties")) {
                walkSchemaNode(value.get("properties"), currentPath, paths);
            } else if (value.has("items") && value.get("items").has("properties")) {
                walkSchemaNode(value.get("items").get("properties"), currentPath + "[*]", paths);
            } else if (value.isObject() && !value.has("type")) {
                walkSchemaNode(value, currentPath, paths);
            }
        });
    }

    private boolean containsRootPath(Set<String> schemaPaths, String path) {
        // 兼容前端只引用根字段时的校验口径。
        for (String schemaPath : schemaPaths) {
            if (schemaPath.equals(path) || schemaPath.startsWith(path + ".") || schemaPath.startsWith(path + "[*]")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> error(String code, String message, String path) {
        Map<String, Object> item = new HashMap<>();
        item.put("code", code);
        item.put("message", message);
        if (path != null) {
            item.put("path", path);
        }
        return item;
    }

    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?>) {
            List<?> source = (List<?>) value;
            List<Map<String, Object>> target = new ArrayList<>();
            for (Object item : source) {
                if (item instanceof Map<?, ?> mapItem) {
                    Map<String, Object> map = new HashMap<>();
                    mapItem.forEach((k, v) -> map.put(String.valueOf(k), v));
                    target.add(map);
                }
            }
            return target;
        }
        return new ArrayList<>();
    }

    private Object getPathValue(Map<String, Object> input, String path) {
        // 按点路径读取输入参数，缺失则返回null。
        if (!path.contains(".")) {
            return input.get(path);
        }
        String[] segments = path.split("\\.");
        Object current = input;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
