import java.sql.*;

public class CheckMysqlTables {
    // 数据库连接信息
    private static final String URL = "jdbc:mysql://rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com:3306/aim_product_dev?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
    private static final String USERNAME = "aim_user";
    private static final String PASSWORD = "Aim@2026";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("正在连接MySQL数据库...");
        System.out.println("主机: rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com");
        System.out.println("数据库: aim_product_dev");
        System.out.println("========================================\n");

        try (Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            System.out.println("数据库连接成功！\n");

            // 1. 查询所有ai_prompt_前缀的表
            System.out.println("========================================");
            System.out.println("1. 查询所有ai_prompt_前缀的表");
            System.out.println("========================================");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'ai_prompt_%'");

            java.util.List<String> tables = new java.util.ArrayList<>();
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
