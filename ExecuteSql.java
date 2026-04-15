import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExecuteSql {
    
    // 数据库连接配置
    private static final String URL = "jdbc:mysql://rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com:3306/aim_product_dev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "aim_user";
    private static final String PASSWORD = "Aim@2026";
    
    public static void main(String[] args) {
        System.out.println("=" .repeat(60));
        System.out.println("MySQL数据库操作开始");
        System.out.println("时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("=".repeat(60));
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            System.out.println("\n数据库连接成功!");
            
            // 1. 创建Prompt片段表
            executeUpdate(conn, 1, "创建表: ai_prompt_prompt_fragments", 
                "CREATE TABLE IF NOT EXISTS ai_prompt_prompt_fragments (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "psu_id BIGINT NOT NULL COMMENT '关联PSU ID'," +
                "fragment_key VARCHAR(100) NOT NULL COMMENT '片段标识'," +
                "content TEXT NOT NULL COMMENT 'Prompt内容'," +
                "editable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可编辑: 0-锁定, 1-可编辑'," +
                "type ENUM('CORE_RULES', 'MESSAGE_TEMPLATE') NOT NULL COMMENT '类型'," +
                "sort_order INT NOT NULL DEFAULT 0 COMMENT '排序'," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_prompt_fragments_psu_id (psu_id)," +
                "INDEX idx_prompt_fragments_type (type)," +
                "INDEX idx_prompt_fragments_editable (editable)," +
                "INDEX idx_prompt_fragments_sort (sort_order)," +
                "CONSTRAINT uk_prompt_fragments_psu_key UNIQUE (psu_id, fragment_key)" +
                ") COMMENT 'AI Prompt片段表'"
            );
            
            // 2. 创建版本审核表
            executeUpdate(conn, 2, "创建表: ai_prompt_version_reviews",
                "CREATE TABLE IF NOT EXISTS ai_prompt_version_reviews (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "psu_id BIGINT NOT NULL COMMENT '关联PSU ID'," +
                "major_version INT NOT NULL DEFAULT 1 COMMENT '主版本'," +
                "minor_version INT NOT NULL DEFAULT 0 COMMENT '次版本'," +
                "patch_version INT NOT NULL DEFAULT 0 COMMENT '修订版本'," +
                "status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '状态'," +
                "submitter_id BIGINT NOT NULL COMMENT '提交者ID'," +
                "reviewer_id BIGINT COMMENT '审核者ID'," +
                "rejection_reason TEXT COMMENT '驳回原因'," +
                "submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'," +
                "reviewed_at DATETIME COMMENT '审核时间'," +
                "git_commit_hash VARCHAR(64) COMMENT 'Git提交哈希'," +
                "code_content LONGTEXT COMMENT '生成的代码内容'," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_version_reviews_psu_id (psu_id)," +
                "INDEX idx_version_reviews_status (status)," +
                "INDEX idx_version_reviews_submitter (submitter_id)," +
                "INDEX idx_version_reviews_reviewer (reviewer_id)," +
                "INDEX idx_version_reviews_submitted (submitted_at)," +
                "INDEX idx_version_reviews_git_hash (git_commit_hash)," +
                "CONSTRAINT uk_version_reviews_version UNIQUE (psu_id, major_version, minor_version, patch_version)" +
                ") COMMENT 'AI Prompt版本审核表'"
            );
            
            // 3. 创建系统配置表
            executeUpdate(conn, 3, "创建表: ai_prompt_system_configs",
                "CREATE TABLE IF NOT EXISTS ai_prompt_system_configs (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键'," +
                "config_value TEXT NOT NULL COMMENT '配置值（加密存储）'," +
                "config_type ENUM('API_KEY', 'OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '配置类型'," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_system_configs_key (config_key)," +
                "INDEX idx_system_configs_type (config_type)" +
                ") COMMENT 'AI Prompt系统配置表（仅存储API密钥等核心配置）'"
            );
            
            // 4. 创建审计日志表
            executeUpdate(conn, 4, "创建表: ai_prompt_audit_logs",
                "CREATE TABLE IF NOT EXISTS ai_prompt_audit_logs (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT COMMENT '操作用户ID'," +
                "username VARCHAR(50) NOT NULL COMMENT '操作用户名'," +
                "operation VARCHAR(100) NOT NULL COMMENT '操作类型'," +
                "targetType VARCHAR(50) NOT NULL COMMENT '目标类型'," +
                "target_id BIGINT COMMENT '目标ID'," +
                "details JSON COMMENT '操作详情'," +
                "ip_address VARCHAR(45) COMMENT 'IP地址'," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_audit_logs_user_id (user_id)," +
                "INDEX idx_audit_logs_operation (operation)," +
                "INDEX idx_audit_logs_target_type (targetType)," +
                "INDEX idx_audit_logs_target_id (target_id)," +
                "INDEX idx_audit_logs_created (created_at)" +
                ") COMMENT 'AI Prompt审计日志表'"
            );
            
            // 5. 创建测试数据集表
            executeUpdate(conn, 5, "创建表: ai_prompt_test_datasets",
                "CREATE TABLE IF NOT EXISTS ai_prompt_test_datasets (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "psu_id BIGINT NOT NULL COMMENT '关联PSU ID'," +
                "name VARCHAR(200) NOT NULL COMMENT '数据集名称'," +
                "data_content TEXT NOT NULL COMMENT '测试数据内容（JSON格式）'," +
                "description VARCHAR(500) COMMENT '描述'," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_test_datasets_psu_id (psu_id)" +
                ") COMMENT 'AI Prompt测试数据集表'"
            );
            
            // 6. 插入默认配置数据
            executeUpdate(conn, 6, "插入默认配置数据",
                "INSERT IGNORE INTO ai_prompt_system_configs (config_key, config_value, config_type) " +
                "VALUES ('default_api_key', 'encrypted_value_placeholder', 'API_KEY')"
            );
            
            // 7. 验证所有表
            System.out.println("\n[7/7] 验证所有ai_prompt_开头的表...");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'ai_prompt_%'")) {
                
                System.out.println("\n找到的表:");
                System.out.println("-".repeat(40));
                int count = 0;
                while (rs.next()) {
                    System.out.println("  ✓ " + rs.getString(1));
                    count++;
                }
                
                System.out.println("\n" + "=".repeat(60));
                System.out.println("✓ 共找到 " + count + " 个ai_prompt_开头的表");
                System.out.println("=".repeat(60));
            }
            
        } catch (SQLException e) {
            System.err.println("\n数据库错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 执行更新语句的辅助方法
    private static void executeUpdate(Connection conn, int step, String description, String sql) throws SQLException {
        System.out.println("\n[" + step + "/7] " + description);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("  ✓ 执行成功");
        } catch (SQLException e) {
            System.err.println("  ✗ 执行失败: " + e.getMessage());
        }
    }
}
