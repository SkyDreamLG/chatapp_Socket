// 消息类定义在 shared 包中，用于客户端和服务器端共享
package shared;

import java.io.Serial;
import java.io.Serializable; // 使对象可以被序列化（用于网络传输）
import java.util.HashMap;     // 使用 HashMap 存储消息数据
import java.util.Map;          // Map 接口，键值对结构

/**
 * Message 类是客户端和服务器之间通信的基本单位。
 * 它封装了不同类型的消息内容，例如聊天、私信、用户列表等。
 */
public class Message implements Serializable {
    // 序列化版本号，确保不同版本兼容性
    @Serial
    private static final long serialVersionUID = 1L;
    public Map<String, byte[]> binaryData = new HashMap<>();

    /**
     * 消息类型字段：
     * 表示这条消息是什么类型的，比如群聊、私聊、用户列表更新等。
     * 可选值包括："chat", "private", "user_list", "history", "system", "login", "register"
     */
    public String type;

    /**
     * 消息内容字段：
     * 是一个键值对集合，用来存储具体的消息数据。
     * 比如：发送者是谁、内容是什么、发给谁等。
     */
    public Map<String, Object> data = new HashMap<>();

    /**
     * 构造一条群聊消息的方法
     * @param sender 发送者的用户名
     * @param content 聊天内容
     * @return 返回一个 Message 对象，表示一条群聊消息
     */
    public static Message chat(String sender, String content) {
        Message msg = new Message();   // 创建一个新的消息对象
        msg.type = "chat";             // 设置消息类型为“群聊”
        msg.data.put("sender", sender); // 添加发送者信息
        msg.data.put("content", content); // 添加聊天内容
        return msg;
    }

    /**
     * 构造一条私聊消息的方法
     * @param sender 发送者的用户名
     * @param to 接收者的用户名
     * @param content 私聊内容
     * @return 返回一个 Message 对象，表示一条私聊消息
     */
    public static Message privateMsg(String sender, String to, String content) {
        Message msg = new Message();         // 创建新消息
        msg.type = "private";                // 设置消息类型为“私聊”
        msg.data.put("sender", sender);      // 添加发送者
        msg.data.put("to", to);              // 添加接收者
        msg.data.put("content", content);    // 添加私聊内容
        return msg;
    }

    /**
     * 构造一条用户列表更新的消息
     * 当在线用户发生变化时，服务器会发送这种类型的消息给所有客户端
     * @param users 用户名和状态的映射表
     * @return 返回一个 Message 对象，表示用户列表更新
     */
    public static Message userList(Map<String, Object> users) {
        Message msg = new Message();       // 创建新消息
        msg.type = "user_list";            // 设置消息类型为“用户列表”
        msg.data.put("users", users);      // 添加用户列表数据
        return msg;
    }

    /**
     * 构造一条历史记录消息
     * 通常在客户端刚连接上服务器时，服务器会发送聊天历史记录
     * @param log 历史记录的内容字符串
     * @return 返回一个 Message 对象，表示历史记录
     */
    public static Message history(String log) {
        Message msg = new Message();       // 创建新消息
        msg.type = "history";              // 设置消息类型为“历史记录”
        msg.data.put("log", log);          // 添加历史内容
        return msg;
    }

    /**
     * 构造一条系统消息
     * 系统消息通常是服务器自动发送的通知，比如有人上线或下线
     * @param content 系统通知的具体内容
     * @return 返回一个 Message 对象，表示系统消息
     */
    public static Message system(String content) {
        Message msg = new Message();           // 创建新消息
        msg.type = "system";                   // 设置消息类型为“系统消息”
        msg.data.put("content", content);      // 添加系统内容
        return msg;
    }

    /**
     * 构造一条登陆请求消息
     * 登陆请求消息通常是客户端在用户请求登陆时发送的消息
     * @param username 登陆请求的用户名
     * @param password 登陆请求的用户的密码哈希
     * @return 返回一个 Message 对象，表示系统消息
     */
    public static Message login(String username, String password) {
        Message msg = new Message();           // 创建新消息
        msg.type = "login";                   // 设置消息类型为“登陆消息”
        msg.data.put("username", username);      // 添加用户名
        msg.data.put("password", password);      // 添加密码哈希
        return msg;
    }

    /**
     * 构造一条注册请求消息
     */
    public static Message register(String username, String passwordHash, byte[] salt) {
        Message msg = new Message();
        msg.type = "register";
        msg.data.put("username", username);
        msg.data.put("password_hash", passwordHash);
        msg.data.put("salt", salt);
        return msg;
    }

    /**
     * 请求用户独立的加密盐
     */
    public static Message getSalt(String username) {
        Message msg = new Message();
        msg.type = "getsalt";
        msg.data.put("username", username);
        return msg;
    }

    /**
     * 返回用户独立的加密盐
     */
    public static Message returnSalt(byte[] salt) {
        Message msg = new Message();
        msg.type = "returnsalt";
        msg.data.put("salt", salt); // 改为放到 data 中
        return msg;
    }
}