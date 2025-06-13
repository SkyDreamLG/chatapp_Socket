// ChatLogDAO.java 位于 server 包中
package server;

import java.sql.*; // JDBC 相关类，用于连接数据库和执行 SQL 操作
import java.time.LocalDateTime; // 用于获取当前时间
import java.time.format.DateTimeFormatter; // 时间格式化工具
import java.util.*; // 使用 List、ArrayList 等集合类

/**
 * ChatLogDAO 类是数据访问对象（Data Access Object），专门负责操作聊天记录。
 * 它有两个主要功能：
 * 1. 把聊天消息保存到数据库中（logMessage）
 * 2. 从数据库中读取历史聊天记录（getRecentChatHistory）
 */
public class ChatLogDAO {

    /**
     * 将一条聊天消息写入数据库。
     *
     * @param sender   发送者用户名（比如："张三"）
     * @param receiver 接收者用户名（如果是群发，则为 null）
     * @param message  消息内容（比如："你好！"）
     * @param log_level 日志级别（比如："user" 表示用户消息，"system" 表示系统通知）
     */
    public static void logMessage(String sender, String receiver, String message, String log_level) {
        // SQL 插入语句：将聊天记录插入到 chat_log 数据表中
        String sql = "INSERT INTO chat_log(send_time, sender, receiver, message, log_level) VALUES (?, ?, ?, ?, ?)";

        try (
                // 获取数据库连接（DBUtil 是一个自定义的数据库工具类）
                Connection conn = DBUtil.getConnection();
                // 准备 SQL 语句，防止 SQL 注入攻击
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            // 获取当前时间并格式化成字符串（如 2025-06-12 02:30:45）
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 填充 SQL 中的占位符 ?
            pstmt.setString(1, time);           // 第1个问号填发送时间
            pstmt.setString(2, sender);         // 第2个问号填发送人
            pstmt.setString(3, receiver);       // 第3个问号填接收人（群聊时为空）
            pstmt.setString(4, message);        // 第4个问号填消息内容
            pstmt.setString(5, log_level);      // 第5个问号填日志类型（用户消息或系统消息）

            // 执行插入操作，把这条消息存进数据库
            pstmt.executeUpdate();

        } catch (Exception e) {
            // 如果插入失败，记录错误日志
            Server.logger.log(java.util.logging.Level.SEVERE, "写入聊天记录失败", e);
        }
    }

    /**
     * 获取最近的聊天历史记录（包括群聊和与当前用户的私聊）。
     *
     * @param limit        要获取的消息条数（比如：20 条）
     * @param currentUser 当前登录的用户名（比如："李四"）
     * @return 包含聊天记录的字符串列表，每条记录是一个格式化的字符串
     */
    public static List<String> getRecentChatHistory(int limit, String currentUser) {
        List<String> history = new ArrayList<>();  // 创建一个列表来保存聊天记录

        // 查询语句说明：
        // 查找所有群发消息（receiver 为 NULL），
        // 或者是发给当前用户的私信（sender = 当前用户 或 receiver = 当前用户），
        // 并且排除系统消息，
        // 然后按消息 ID 倒序排列（最新的在前面），
        // 最多取 limit 条记录。
        String sql = "SELECT send_time, sender, receiver, message FROM chat_log " +
                "WHERE (receiver IS NULL OR sender = ? OR receiver = ?) AND log_level != 'system' " +
                "ORDER BY id DESC LIMIT ?";

        try (
                // 获取数据库连接
                Connection conn = DBUtil.getConnection();
                // 准备 SQL 查询语句
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            // 填充查询参数
            pstmt.setString(1, currentUser);  // 当前用户作为发送者
            pstmt.setString(2, currentUser);  // 当前用户作为接收者
            pstmt.setInt(3, limit);           // 最多取多少条记录

            ResultSet rs = pstmt.executeQuery();  // 执行查询，得到结果集

            // 遍历结果集中的每一行数据
            while (rs.next()) {
                String time = rs.getString("send_time");     // 取出发送时间
                String sender = rs.getString("sender");       // 取出发送者
                String receiver = rs.getString("receiver");   // 取出接收者
                String message = rs.getString("message");     // 取出消息内容

                // 如果是有接收人的消息，说明是私信
                if (receiver != null && !receiver.isEmpty()) {
                    message = "[私信] " + message;  // 在消息前加上“[私信]”标识
                }

                // 把这条消息格式化成带用户名、时间和内容的字符串
                history.add("[" + sender + "] [" + time + "]：" + message);
            }

        } catch (Exception e) {
            // 如果查询失败，记录错误日志
            Server.logger.log(java.util.logging.Level.SEVERE, "获取聊天记录失败", e);
        }

        // 因为我们是从最新的开始查出来的（倒序），所以要反转一下顺序，
        // 让最老的消息排在最前面，最新的消息排在最后面，这样更符合阅读习惯
        Collections.reverse(history);

        return history;
    }
}