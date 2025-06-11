package server;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatLogDAO {

    public static void logMessage(String sender, String receiver, String message, String log_level) {
        String sql = "INSERT INTO chat_log(send_time, sender, receiver, message, log_level) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            pstmt.setString(1, time);
            pstmt.setString(2, sender);
            pstmt.setString(3, receiver); // 群发时为 null
            pstmt.setString(4, message);
            pstmt.setString(5, log_level);
            pstmt.executeUpdate();

        } catch (Exception e) {
            Server.logger.log(java.util.logging.Level.SEVERE, "写入聊天记录失败", e);
        }
    }

    public static List<String> getRecentChatHistory(int limit) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT send_time, sender, message FROM chat_log WHERE receiver IS NULL AND log_level != 'system' ORDER BY id DESC LIMIT ?";
        // 上面的查询假定只有群发消息(receiver为null)才被认为是聊天消息，上下线通知等作为系统通知不包括在内
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String time = rs.getString("send_time");
                String sender = rs.getString("sender");
                String message = rs.getString("message");

                history.add("[" + sender + "] [" + time + "]：" + message);
            }

        } catch (Exception e) {
            Server.logger.log(java.util.logging.Level.SEVERE, "获取聊天记录失败", e);
        }

        Collections.reverse(history);
        return history;
    }
}