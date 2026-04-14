package com.example.psu.controller;

import com.example.psu.entity.SystemConfig;
import com.example.psu.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器（仅管理员使用）
 */
@RestController
@RequestMapping("/api/configs")
public class SystemConfigController {
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    /**
     * 获取所有系统配置（仅管理员）
     * @return 配置列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        List<SystemConfig> configs = systemConfigService.getAllConfigs();
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 根据配置键获取配置（仅管理员）
     * @param configKey 配置键
     * @return 系统配置
     */
    @GetMapping("/{configKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfig> getConfigByKey(@PathVariable String configKey) {
        SystemConfig config = systemConfigService.getConfigByKey(configKey);
        return ResponseEntity.ok(config);
    }
    
    /**
     * 创建或更新系统配置（仅管理员）
     * @param requestBody 配置请求体
     * @return 系统配置
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfig> saveConfig(@RequestBody Map<String, String> requestBody) {
        String configKey = requestBody.get("configKey");
        String configValue = requestBody.get("configValue");
        String configType = requestBody.getOrDefault("configType", "OTHER");
        
        SystemConfig config = systemConfigService.saveConfig(
                configKey, 
                configValue, 
                SystemConfig.ConfigType.valueOf(configType.toUpperCase())
        );
        return ResponseEntity.ok(config);
    }
    
    /**
     * 删除系统配置（仅管理员）
     * @param id 配置ID
     * @return 无内容
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        systemConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}
