// DBUtil.java 位于 server 包中
package server;

// 使用 HikariCP 连接池库，用于高效管理数据库连接
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * DBUtil 是一个数据库工具类，专门用来管理数据库连接。
 *
 * 它的主要功能是：
 * - 初始化数据库连接池（使用高性能的 HikariCP）
 * - 提供数据库连接给其他类使用（如 ChatLogDAO）
 * - 在服务器关闭时释放所有数据库资源
 */
public class DBUtil {
    // 数据源对象：HikariCP 的核心组件，用来管理数据库连接池
    private static HikariDataSource dataSource;

    /**
     * 初始化数据库连接池。
     * 需要传入数据库地址、用户名和密码等信息。
     *
     * @param dbUrl      数据库地址，例如 jdbc:mysql://localhost:3306/chatdb
     * @param dbUser     数据库用户名，比如 root
     * @param dbPassword 数据库密码
     */
    public static void init(String dbUrl, String dbUser, String dbPassword) {
        // 创建 HikariCP 的配置对象，用于设置连接池参数
        HikariConfig config = new HikariConfig();

        // 设置数据库的基本连接信息
        config.setJdbcUrl(dbUrl);       // 数据库地址
        config.setUsername(dbUser);     // 登录用户名
        config.setPassword(dbPassword); // 登录密码

        // 以下是一些优化数据库连接性能的高级配置：

        // 启用预编译语句缓存，提高执行效率
        config.addDataSourceProperty("cachePrepStmts", "true");
        // 预编译语句缓存的最大数量
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        // 单条 SQL 语句最大长度限制
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // 设置连接池大小
        config.setMaximumPoolSize(10);         // 最多同时有10个连接
        config.setMinimumIdle(2);              // 至少保留2个空闲连接
        config.setIdleTimeout(30000);          // 空闲连接最多保持30秒
        config.setMaxLifetime(1800000);        // 每个连接最多存活30分钟

        // 设置连接测试相关参数
        config.setConnectionTestQuery("SELECT 1"); // 用这个 SQL 测试连接是否有效
        config.setValidationTimeout(3000);     // 验证连接是否有效的超时时间：3秒
        config.setLeakDetectionThreshold(5000); // 如果连接长时间未归还，提示泄露警告（5秒）

        // 根据上面的配置创建连接池对象
        dataSource = new HikariDataSource(config);
    }

    /**
     * 获取一个数据库连接。
     *
     * @return 返回一个可用的数据库连接对象
     * @throws Exception 如果连接池尚未初始化，则抛出异常
     */
    public static java.sql.Connection getConnection() throws Exception {
        if (dataSource == null) {
            throw new Exception("数据库连接池未初始化，请先调用 init 方法！");
        }
        return dataSource.getConnection(); // 从连接池中取出一个连接
    }

    /**
     * 关闭整个数据库连接池，释放所有资源。
     * 通常在服务器关闭时调用这个方法。
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close(); // 关闭连接池，释放所有连接资源
        }
    }
}