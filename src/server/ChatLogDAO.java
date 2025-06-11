package server;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public class ChatLogDAO {

    public static void logMessage(String sender, String receiver, String message) {
        String sql = "INSERT INTO chat_log(send_time, sender, receiver, message) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            pstmt.setString(1, time);
            pstmt.setString(2, sender);
            pstmt.setString(3, receiver); // 群发时为 null
            pstmt.setString(4, message);
            pstmt.executeUpdate();

        } catch (Exception e) {
            Server.logger.log(Level.SEVERE, "写入聊天记录失败", e);
        }
    }
}