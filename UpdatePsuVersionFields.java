import java.sql.*;

public class UpdatePsuVersionFields {
    
    // 数据库连接配置
    private static final String URL = "jdbc:mysql://rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com:3306/aim_product_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "aim_user";
    private static final String PASSWORD = "Aim@2026";
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("PSU全生命周期管理平台 - 数据库结构更新工具");
        System.out.println("更新PSU表结构，添加版本号字段");
        System.out.println("=".repeat(60));
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            System.out.println("\n数据库连接成功!");
            
            // 检查PSU表是否已存在版本号字段
            System.out.println("\n检查PSU表结构...");
            if (checkVersionFieldsExist(conn)) {
                System.out.println("  ✓ PSU表已包含版本号字段，无需更新");
            } else {
                // 添加版本号字段
                System.out.println("\n正在添加版本号字段到ai_prompt_psu表...");
                
                String[] alterSqls = {
                    "ALTER TABLE ai_prompt_psu ADD COLUMN major_version INT NOT NULL DEFAULT 0 COMMENT '主版本号' AFTER creator_id;",
                    "ALTER TABLE ai_prompt_psu ADD COLUMN minor_version INT NOT NULL DEFAULT 0 COMMENT '次版本号' AFTER major_version;",
                    "ALTER TABLE ai_prompt_psu ADD COLUMN patch_version INT NOT NULL DEFAULT 0 COMMENT '修订版本号' AFTER minor_version;"
                };
                
                for (String sql : alterSqls) {
                    executeUpdateWithCheck(conn, sql);
                }
                
                System.out.println("\n  ✓ PSU表版本号字段添加成功！");
            }
            
            // 验证更新结果
            System.out.println("\n验证PSU表结构更新结果...");
            verifyPsuStructure(conn);
            
        } catch (SQLException e) {
            System.err.println("\n数据库错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 检查版本号字段是否已存在
    private static boolean checkVersionFieldsExist(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE ai_prompt_psu")) {
            
            boolean hasMajorVersion = false;
            boolean hasMinorVersion = false;
            boolean hasPatchVersion = false;
            
            while (rs.next()) {
                String columnName = rs.getString("Field");
                if ("major_version".equals(columnName)) hasMajorVersion = true;
                if ("minor_version".equals(columnName)) hasMinorVersion = true;
                if ("patch_version".equals(columnName)) hasPatchVersion = true;
            }
            
            return hasMajorVersion && hasMinorVersion && hasPatchVersion;
        }
    }
    
    // 执行更新语句并检查结果
    private static void executeUpdateWithCheck(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("  ✓ 执行成功: " + sql.substring(0, Math.min(60, sql.length())) + "...");
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate column")) {
                System.out.println("  - 字段已存在，跳过: " + sql.substring(0, Math.min(60, sql.length())) + "...");
            } else {
                System.err.println("  ✗ 执行失败: " + sql.substring(0, Math.min(60, sql.length())) + "..." + " - " + e.getMessage());
            }
        }
    }
    
    // 验证PSU表结构
    private static void verifyPsuStructure(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE ai_prompt_psu")) {
            
            System.out.println("\nPSU表当前字段结构:");
            System.out.println("-".repeat(60));
            System.out.println(String.format("%-20s %-30s", "字段名", "类型"));
            System.out.println("-".repeat(60));
            
            while (rs.next()) {
                String field = rs.getString("Field");
                String type = rs.getString("Type");
                System.out.println(String.format("%-20s %-30s", field, type));
            }
            
            System.out.println("-".repeat(60));
            
            // 检查版本号字段
            boolean hasMajor = checkColumnExists(conn, "ai_prompt_psu", "major_version");
            boolean hasMinor = checkColumnExists(conn, "ai_prompt_psu", "minor_version");
            boolean hasPatch = checkColumnExists(conn, "ai_prompt_psu", "patch_version");
            
            System.out.println("\n版本号字段检查结果:");
            System.out.println("  主版本号 (major_version): " + (hasMajor ? "✓ 存在" : "✗ 缺失"));
            System.out.println("  次版本号 (minor_version): " + (hasMinor ? "✓ 存在" : "✗ 缺失"));
            System.out.println("  修订版本号 (patch_version): " + (hasPatch ? "✓ 存在" : "✗ 缺失"));
            
            if (hasMajor && hasMinor && hasPatch) {
                System.out.println("\n✓ PSU表结构更新验证成功！所有版本号字段均已存在。");
            } else {
                System.out.println("\n✗ PSU表结构更新验证失败！部分版本号字段缺失。");
            }
        }
    }
    
    // 检查列是否存在
    private static boolean checkColumnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
}