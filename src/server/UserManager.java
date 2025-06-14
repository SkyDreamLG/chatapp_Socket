// 文件路径：server/UserManager.java

// 定义包名 server，表示该类属于服务器模块
package server;

// 导入必要的类
// 用于生成安全的随机数（虽然当前类未使用）

import java.sql.Connection;            // 数据库连接
import java.sql.PreparedStatement;     // 预编译 SQL 语句
import java.sql.ResultSet;             // 存储数据库查询结果
// 处理数据库异常
// 编码/解码数据（虽然当前类未使用）


/**
 * UserManager 是一个用户管理类。
 * 主要功能：
 * - 用户注册
 * - 获取盐值
 * - 验证用户名和密码
 * - 判断用户名是否存在
 */
public class UserManager {

    /**
     * 注册新用户到数据库中。
     *
     * @param username       用户名
     * @param hashedPassword 哈希后的密码（已加盐）
     * @param salt           加密用的盐值
     * @return 成功插入返回 true，失败返回 false
     */
    public static boolean register(String username, String hashedPassword, byte[] salt) {
        // SQL 插入语句，将用户名、哈希密码和盐值存入数据库
        String sql = "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)";

        try (
                // 使用 DBUtil 获取数据库连接
                Connection conn = DBUtil.getConnection();
                // 创建预编译语句，防止 SQL 注入
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            // 设置参数
            pstmt.setString(1, username);          // 用户名
            pstmt.setString(2, hashedPassword);    // 哈希后的密码
            pstmt.setBytes(3, salt);               // 盐值

            // 执行插入操作，获取影响的行数
            int rowsAffected = pstmt.executeUpdate();

            // 如果至少有一行被影响，说明插入成功
            return rowsAffected > 0;

        } catch (Exception e) {
            // 捕获异常并处理
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                // 如果是用户名重复错误
                System.err.println("用户名已存在：" + username);
            } else {
                // 其他错误打印堆栈信息
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * 根据用户名查询对应的盐值（salt）。
     * 登录时需要先获取盐值，才能正确校验密码。
     *
     * @param username 用户名
     * @return 返回 salt 字节数组，如果找不到返回 null
     */
    public static byte[] getSaltByUsername(String username) {
        // SQL 查询语句，查找指定用户名的盐值
        String sql = "SELECT salt FROM users WHERE username = ? AND is_active = TRUE";

        try (
                Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            pstmt.setString(1, username);  // 设置用户名参数
            ResultSet rs = pstmt.executeQuery();  // 执行查询

            // 如果有结果，取出盐值
            if (rs.next()) {
                return rs.getBytes("salt");  // 返回 salt 字段的内容
            }

        } catch (Exception e) {
            e.printStackTrace();  // 出错则打印错误信息
        }

        return null;  // 默认返回 null 表示没找到
    }

    /**
     * 根据 UID 获取用户名。
     * 注意字段名是 Username（首字母大写），不是 username。
     *
     * @param UID 用户唯一标识符
     * @return 返回用户名，如果找不到返回 null
     */
    public static String getUsernameByUID(String UID) {
        // SQL 查询语句，根据 uid 查找用户名
        String sql = "SELECT Username FROM users WHERE uid = ? AND is_active = TRUE";

        try (
                Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            pstmt.setString(1, UID);  // 设置 UID 参数
            ResultSet rs = pstmt.executeQuery();  // 执行查询

            if (rs.next()) {
                return rs.getString("Username");  // 返回用户名
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 验证用户名和密码是否匹配。
     *
     * @param username       用户名
     * @param hashedPassword 经过加盐哈希处理后的密码
     * @return 匹配返回 true，否则 false
     */
    public static boolean authenticate(String username, String hashedPassword) {
        // SQL 查询语句，查找用户的密码哈希值
        String sql = "SELECT password_hash FROM users WHERE username = ? AND is_active = TRUE";

        try (
                Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            pstmt.setString(1, username);  // 设置用户名参数
            ResultSet rs = pstmt.executeQuery();  // 执行查询

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");  // 获取存储的哈希值
                return storedHash.equals(hashedPassword);  // 比较哈希值是否一致
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 判断用户名是否已经存在于数据库中。
     *
     * @param username 要检查的用户名
     * @return 如果存在返回 true，否则返回 false
     */
    public static boolean usernameExists(String username) {
        // SQL 查询语句，检查用户名是否存在
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (
                Connection conn = DBUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            pstmt.setString(1, username);  // 设置用户名参数
            ResultSet rs = pstmt.executeQuery();  // 执行查询
            return rs.next();  // 如果能取到一行，说明存在

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}