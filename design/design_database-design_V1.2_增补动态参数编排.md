# PSU全生命周期管理平台 - 数据库设计文档 V1.2（增补：动态参数编排容器 & 测试运行）

> 说明：本文档在原《database-design.md》基础上增补“动态参数编排容器（Composition）”相关表结构建议。  
> 权限/安全不是当前第一优先级，但“可追溯/可复现”是编排与审核闭环的基础，因此必须做“提交时快照”。

---

## 1. 新增表：编排草稿与快照

### 1.1 编排草稿表：ai_prompt_compositions

用途：保存每个 PSU 的“当前可编辑草稿”（通常 1 条最新草稿，也可允许多条草稿并用 is_latest 标记）。

```sql
CREATE TABLE ai_prompt_compositions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  psu_id BIGINT NOT NULL COMMENT '关联PSU(ID)',
  schema_version INT NOT NULL COMMENT '提交/保存时绑定的schema版本（便于复现）',
  status ENUM('DRAFT','SUBMITTED','DEV_REVIEWING','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT',

  content LONGTEXT NOT NULL COMMENT '编辑器原始文本（可回显）',
  spec_json JSON NOT NULL COMMENT '结构化编排规格（tokens/injectionPlan等）',

  created_by BIGINT NOT NULL,
  updated_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_compositions_psu_id (psu_id),
  INDEX idx_compositions_status (status)
) COMMENT 'PSU动态参数编排草稿表';
```

> 说明：若 MySQL JSON 性能/兼容性有顾虑，可先用 LONGTEXT 存 spec_json（但字段名仍建议保留）。

### 1.2 编排快照表：ai_prompt_composition_revisions

用途：在“提交审核/审核通过/驳回”等关键节点固化快照，保证可复现与审计对齐。

```sql
CREATE TABLE ai_prompt_composition_revisions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  composition_id BIGINT NOT NULL,
  psu_id BIGINT NOT NULL,
  revision_no INT NOT NULL COMMENT '自增版本号，从1开始',
  status_at_time ENUM('DRAFT','SUBMITTED','DEV_REVIEWING','APPROVED','REJECTED') NOT NULL,
  schema_version_at_time INT NOT NULL,

  content_snapshot LONGTEXT NOT NULL,
  spec_json_snapshot JSON NOT NULL,

  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_comp_rev (composition_id, revision_no),
  INDEX idx_comp_rev_psu_id (psu_id)
) COMMENT '编排快照表（提交/审核节点固化）';
```

---

## 2. 审核表扩展建议（可选，但建议做）

你现有 `ai_prompt_version_reviews` 已能记录 codeContent/gitCommitHash。为了让审核与编排绑定更清晰，建议增加：
- `composition_id`
- `composition_revision_no`
- `rejection_type`（BACK_TO_DEV / BACK_TO_BIZ）

示例：
```sql
ALTER TABLE ai_prompt_version_reviews
  ADD COLUMN composition_id BIGINT NULL,
  ADD COLUMN composition_revision_no INT NULL,
  ADD COLUMN rejection_type ENUM('BACK_TO_DEV','BACK_TO_BIZ') NULL;
```

---

## 3. 测试运行留存（可选，增强调试体验）

如果你希望“线上调试结果可追溯”，建议新增：

### 3.1 测试运行主表：ai_prompt_test_runs
```sql
CREATE TABLE ai_prompt_test_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  psu_id BIGINT NOT NULL,
  dataset_id BIGINT NOT NULL,
  composition_id BIGINT NOT NULL,
  composition_revision_no INT NULL COMMENT '若基于快照运行则填写',

  status ENUM('RUNNING','DONE','FAILED') NOT NULL DEFAULT 'RUNNING',
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_test_runs_psu_id (psu_id),
  INDEX idx_test_runs_dataset_id (dataset_id)
) COMMENT '测试运行记录';
```

### 3.2 测试运行明细表：ai_prompt_test_run_items
```sql
CREATE TABLE ai_prompt_test_run_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  case_id VARCHAR(100) NOT NULL,

  input_json JSON NOT NULL,
  rendered_prompt LONGTEXT NULL,
  model_output LONGTEXT NULL,

  success TINYINT(1) NOT NULL DEFAULT 1,
  error_message TEXT NULL,
  latency_ms INT NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_test_run_items_run_id (run_id),
  INDEX idx_test_run_items_case_id (case_id)
) COMMENT '测试运行明细';
```

