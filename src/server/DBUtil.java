package server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * DBUtil 是数据库工具类，用于管理数据库连接池。
 * 它负责初始化数据库连接、获取连接以及关闭连接。
 * 使用了 HikariCP 这个高性能的 JDBC 连接池框架。
 */
public class DBUtil {
    // 用于管理数据库连接的连接池对象
    private static HikariDataSource dataSource;

    /**
     * 初始化数据库连接池。
     *
     * @param dbUrl      数据库地址（例如 jdbc:mysql://localhost:3306/chatdb）
     * @param dbUser     数据库用户名
     * @param dbPassword 数据库密码
     */
    public static void init(String dbUrl, String dbUser, String dbPassword) {
        // 创建 HikariCP 的配置对象
        HikariConfig config = new HikariConfig();

        // 设置数据库连接的基本信息
        config.setJdbcUrl(dbUrl);       // 数据库地址
        config.setUsername(dbUser);     // 登录用户名
        config.setPassword(dbPassword); // 登录密码

        // 以下是一些数据库连接池的优化和自动重连相关配置：

        config.addDataSourceProperty("cachePrepStmts", "true"); // 缓存预编译语句，提高性能
        config.addDataSourceProperty("prepStmtCacheSize", "250"); // 预编译语句缓存大小
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048"); // 单条 SQL 语句最大长度限制

        config.setMaximumPoolSize(10);         // 最大连接数：最多同时有10个数据库连接
        config.setMinimumIdle(2);              // 最小空闲连接数：至少保持2个连接可用
        config.setIdleTimeout(30000);          // 空闲连接超时时间：30秒后释放空闲连接
        config.setMaxLifetime(1800000);        // 连接最大存活时间：30分钟，之后会被回收
        config.setConnectionTestQuery("SELECT 1"); // 测试连接是否有效的 SQL 语句
        config.setValidationTimeout(3000);     // 验证连接是否可用的超时时间：3秒
        config.setLeakDetectionThreshold(5000); // 检测连接泄露的时间阈值：5秒

        // 根据配置创建连接池
        dataSource = new HikariDataSource(config);
    }

    /**
     * 获取一个数据库连接。
     *
     * @return 返回一个可用的数据库连接对象
     * @throws Exception 如果连接池未初始化，则抛出异常
     */
    public static java.sql.Connection getConnection() throws Exception {
        if (dataSource == null) {
            throw new Exception("数据库连接池未初始化");
        }
        return dataSource.getConnection(); // 从连接池中取出一个连接
    }

    /**
     * 关闭整个数据库连接池，释放资源。
     * 通常在服务器关闭时调用。
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close(); // 关闭连接池，释放所有连接资源
        }
    }
}