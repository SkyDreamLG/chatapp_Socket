// 文件路径：client/SecurityUtil.java

// 定义包名 client，表示该类属于客户端模块
package client;

// 导入安全相关的类
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;  // 用于生成消息摘要（如 SHA-256）
import java.security.SecureRandom;   // 用于生成加密安全的随机数

/**
 * SecurityUtil 类是一个密码安全工具类。
 * 主要功能：
 * - 生成随机盐值（salt）
 * - 使用盐值对密码进行 SHA-256 哈希处理
 */
public class SecurityUtil {

    /**
     * 定义盐值长度为16字节。
     * 盐值是用来增加密码哈希复杂度的随机数据。
     */
    private static final int SALT_LENGTH = 16; // 盐长度

    /**
     * 生成一个随机的盐值。
     * 使用 SecureRandom 来保证随机性是加密安全的。
     *
     * @return byte[] 类型的盐值
     */
    public static byte[] generateSalt() {
        // 创建一个 SecureRandom 实例，用于生成加密安全的随机数
        SecureRandom random = new SecureRandom();

        // 创建一个长度为 SALT_LENGTH 的字节数组
        byte[] salt = new byte[SALT_LENGTH];

        // 用随机数填充这个数组
        random.nextBytes(salt);

        // 返回生成的盐值
        return salt;
    }

    /**
     * 使用给定的盐值对密码进行 SHA-256 哈希运算。
     * 这个方法返回的是十六进制格式的字符串结果。
     *
     * @param password 用户输入的明文密码
     * @param salt     随机生成的盐值
     * @return 哈希后的密码字符串（十六进制表示）
     */
    public static String hashPasswordWithSalt(String password, byte[] salt) {
        try {
            // 获取 SHA-256 算法的 MessageDigest 实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 重置摘要器，确保它是干净的
            digest.reset();

            // 将盐值加入到摘要计算中
            digest.update(salt);

            // 对密码进行编码并计算哈希值
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // 创建一个 StringBuilder，用于拼接哈希结果的十六进制字符串
            StringBuilder hexString = new StringBuilder();

            // 遍历每个字节，将其转换为两位的十六进制字符串
            for (byte b : hash) {
                // 将字节转为整数，并且只取低8位（0xff & b）
                String hex = Integer.toHexString(0xff & b);

                // 如果转换后的字符串长度为1，前面补一个0，保证两位
                if (hex.length() == 1) {
                    hexString.append('0');
                }

                // 添加当前字节对应的十六进制字符串
                hexString.append(hex);
            }

            // 返回最终的哈希字符串
            return hexString.toString();
        } catch (Exception ex) {
            // 如果出现异常，抛出运行时异常，终止程序
            throw new RuntimeException(ex);
        }
    }
}