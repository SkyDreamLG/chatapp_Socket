// src/shared/Message.java
package shared;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public String type; // 消息类型：chat, private, user_list, history, system
    public Map<String, Object> data = new HashMap<>();

    // 构造方法示例
    public static Message chat(String sender, String content) {
        Message msg = new Message();
        msg.type = "chat";
        msg.data.put("sender", sender);
        msg.data.put("content", content);
        return msg;
    }

    public static Message privateMsg(String sender, String to, String content) {
        Message msg = new Message();
        msg.type = "private";
        msg.data.put("sender", sender);
        msg.data.put("to", to);
        msg.data.put("content", content);
        return msg;
    }

    public static Message userList(Map<String, Object> users) {
        Message msg = new Message();
        msg.type = "user_list";
        msg.data.put("users", users);
        return msg;
    }

    public static Message history(String log) {
        Message msg = new Message();
        msg.type = "history";
        msg.data.put("log", log);
        return msg;
    }

    public static Message system(String content) {
        Message msg = new Message();
        msg.type = "system";
        msg.data.put("content", content);
        return msg;
    }
}