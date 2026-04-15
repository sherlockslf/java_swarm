import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CheckMysqlTablesUpdated {
    // 数据库连接信息
    private static final String URL = "jdbc:mysql://rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com:3306/aim_product_ai?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
    private static final String USERNAME = "aim_user";
    private static final String PASSWORD = "Aim@2026";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("正在连接MySQL数据库...");
        System.out.println("主机: rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com");
        System.out.println("数据库: aim_product_ai");
        System.out.println("========================================\n");

        try (Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            System.out.println("数据库连接成功！\n");

            // 1. 查询所有ai_prompt_前缀的表
            System.out.println("========================================");
            System.out.println("1. 查询所有ai_prompt_前缀的表");
            System.out.println("========================================");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'ai_prompt_%'");

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            rs.close();

            System.out.println("找到 " + tables.size() + " 个ai_prompt_前缀的表:");
            for (String table : tables) {
                System.out.println("  - " + table);
            }

            // 2. 检查每个表的数据量
            System.out.println("\n========================================");
            System.out.println("2. 检查每个表的数据量");
            System.out.println("========================================");

            String[] targetTables = {
                "ai_prompt_users",
                "ai_prompt_psu",
                "ai_prompt_json_schemas",
                "ai_prompt_prompt_fragments",
                "ai_prompt_version_reviews",
                "ai_prompt_system_configs",
                "ai_prompt_audit_logs",
                "ai_prompt_test_datasets"
            };

            for (String table : targetTables) {
                try {
                    ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    if (countRs.next()) {
                        long count = countRs.getLong(1);
                        boolean exists = tables.contains(table);
                        String status = exists ? "存在" : "不存在";
                        System.out.println(table + " : " + count + " 条记录 (" + status + ")");
                    }
                    countRs.close();
                } catch (SQLException e) {
                    System.out.println(table + " : 查询失败 - " + e.getMessage());
                }
            }

            // 3. 特别检查PSU表结构，验证版本号字段
            System.out.println("\n========================================");
            System.out.println("3. 验证PSU表结构（版本号字段）");
            System.out.println("========================================");

            try {
                ResultSet descRs = stmt.executeQuery("DESCRIBE ai_prompt_psu");
                System.out.println("ai_prompt_psu 表结构:");
                System.out.println("  " + String.format("%-20s %-30s", "字段名", "类型"));
                System.out.println("  " + "-".repeat(50));

                boolean hasMajorVersion = false;
                boolean hasMinorVersion = false;
                boolean hasPatchVersion = false;

                while (descRs.next()) {
                    String field = descRs.getString("Field");
                    String type = descRs.getString("Type");
                    System.out.println("  " + String.format("%-20s %-30s", field, type));

                    if ("major_version".equals(field)) hasMajorVersion = true;
                    if ("minor_version".equals(field)) hasMinorVersion = true;
                    if ("patch_version".equals(field)) hasPatchVersion = true;
                }
                descRs.close();

                System.out.println("\n版本号字段检查:");
                System.out.println("  主版本号 (major_version): " + (hasMajorVersion ? "✓ 存在" : "✗ 缺失"));
                System.out.println("  次版本号 (minor_version): " + (hasMinorVersion ? "✓ 存在" : "✗ 缺失"));
                System.out.println("  修订版本号 (patch_version): " + (hasPatchVersion ? "✓ 存在" : "✗ 缺失"));

                if (hasMajorVersion && hasMinorVersion && hasPatchVersion) {
                    System.out.println("\n✓ PSU表结构验证成功！版本号字段已正确添加。");
                } else {
                    System.out.println("\n✗ PSU表结构验证失败！版本号字段缺失。");
                }
            } catch (SQLException e) {
                System.out.println("ai_prompt_psu 表结构检查失败: " + e.getMessage());
            }

            stmt.close();
            System.out.println("\n========================================");
            System.out.println("数据库检查完成！");
            System.out.println("========================================");

        } catch (SQLException e) {
            System.err.println("数据库连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}