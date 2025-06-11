package server;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ChatLogDAO 类用于操作聊天记录数据库。
 * DAO 是 Data Access Object 的缩写，表示数据访问对象。
 * 这个类负责将聊天记录保存到数据库，并从数据库中读取历史记录。
 */
public class ChatLogDAO {

    /**
     * 将一条聊天消息记录写入数据库。
     *
     * @param sender   发送者用户名
     * @param receiver 接收者用户名（群发时为 null）
     * @param message  消息内容
     * @param log_level 日志级别（例如 "user" 表示普通消息，"system" 表示系统消息）
     */
    public static void logMessage(String sender, String receiver, String message, String log_level) {
        // SQL 插入语句：将聊天记录插入到 chat_log 数据表中
        String sql = "INSERT INTO chat_log(send_time, sender, receiver, message, log_level) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();  // 获取数据库连接
             PreparedStatement pstmt = conn.prepareStatement(sql)) {  // 准备 SQL 语句

            // 获取当前时间并格式化成字符串（如 2025-06-12 02:30:45）
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 填充 SQL 中的占位符 ?
            pstmt.setString(1, time);           // 发送时间
            pstmt.setString(2, sender);         // 发送人
            pstmt.setString(3, receiver);       // 接收人（如果是群聊则为空）
            pstmt.setString(4, message);        // 消息内容
            pstmt.setString(5, log_level);      // 日志类型（用户消息或系统消息）

            // 执行插入操作
            pstmt.executeUpdate();

        } catch (Exception e) {
            // 如果插入失败，记录错误日志
            Server.logger.log(java.util.logging.Level.SEVERE, "写入聊天记录失败", e);
        }
    }

    /**
     * 获取最近的聊天历史记录（包括群聊和与当前用户的私聊）。
     *
     * @param limit        要获取的消息条数
     * @param currentUser 当前登录的用户名
     * @return 包含聊天记录的字符串列表
     */
    public static List<String> getRecentChatHistory(int limit, String currentUser) {
        List<String> history = new ArrayList<>();  // 用于存储聊天记录

        // 查询语句：
        // 查找所有群发消息（receiver 为 NULL），以及发送给或来自当前用户的消息，
        // 并排除系统消息，按时间倒序排列，取前 limit 条
        String sql = "SELECT send_time, sender, receiver, message FROM chat_log " +
                "WHERE (receiver IS NULL OR sender = ? OR receiver = ?) AND log_level != 'system' " +
                "ORDER BY id DESC LIMIT ?";

        try (Connection conn = DBUtil.getConnection();  // 获取数据库连接
             PreparedStatement pstmt = conn.prepareStatement(sql)) {  // 准备 SQL 语句

            // 填充查询参数
            pstmt.setString(1, currentUser);  // 当前用户是发送者
            pstmt.setString(2, currentUser);  // 当前用户是接收者
            pstmt.setInt(3, limit);           // 最多获取多少条记录

            ResultSet rs = pstmt.executeQuery();  // 执行查询

            // 遍历结果集，把每条记录转换成一个字符串加入列表
            while (rs.next()) {
                String time = rs.getString("send_time");     // 发送时间
                String sender = rs.getString("sender");       // 发送者
                String receiver = rs.getString("receiver");   // 接收者
                String message = rs.getString("message");     // 消息内容

                // 如果是有接收人的消息，说明是私信
                if (receiver != null && !receiver.isEmpty()) {
                    message = "[私信] " + message;  // 在消息前加上“[私信]”标识
                }

                // 格式化成带用户名、时间和内容的字符串
                history.add("[" + sender + "] [" + time + "]：" + message);
            }

        } catch (Exception e) {
            // 如果查询失败，记录错误日志
            Server.logger.log(java.util.logging.Level.SEVERE, "获取聊天记录失败", e);
        }

        // 因为我们是从最新的开始查出来的（倒序），所以要反转一下顺序，让老消息在前面
        Collections.reverse(history);

        return history;
    }
}