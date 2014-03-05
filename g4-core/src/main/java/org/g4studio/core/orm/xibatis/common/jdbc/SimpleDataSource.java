package org.g4studio.core.orm.xibatis.common.jdbc;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.sql.DataSource;

import org.g4studio.core.orm.xibatis.common.beans.ClassInfo;
import org.g4studio.core.orm.xibatis.common.logging.Log;
import org.g4studio.core.orm.xibatis.common.logging.LogFactory;
import org.g4studio.core.orm.xibatis.common.resources.Resources;

public class SimpleDataSource implements DataSource {
    private static final Log log = LogFactory.getLog(SimpleDataSource.class);
    private static final String PROP_JDBC_DRIVER = "JDBC.Driver";
    private static final String PROP_JDBC_URL = "JDBC.ConnectionURL";
    private static final String PROP_JDBC_USERNAME = "JDBC.Username";
    private static final String PROP_JDBC_PASSWORD = "JDBC.Password";
    private static final String PROP_JDBC_DEFAULT_AUTOCOMMIT = "JDBC.DefaultAutoCommit";
    private static final String PROP_POOL_MAX_ACTIVE_CONN = "Pool.MaximumActiveConnections";
    private static final String PROP_POOL_MAX_IDLE_CONN = "Pool.MaximumIdleConnections";
    private static final String PROP_POOL_MAX_CHECKOUT_TIME = "Pool.MaximumCheckoutTime";
    private static final String PROP_POOL_TIME_TO_WAIT = "Pool.TimeToWait";
    private static final String PROP_POOL_PING_QUERY = "Pool.PingQuery";
    private static final String PROP_POOL_PING_CONN_OLDER_THAN = "Pool.PingConnectionsOlderThan";
    private static final String PROP_POOL_PING_ENABLED = "Pool.PingEnabled";
    private static final String PROP_POOL_PING_CONN_NOT_USED_FOR = "Pool.PingConnectionsNotUsedFor";
    private int expectedConnectionTypeCode;
    private static final String ADD_DRIVER_PROPS_PREFIX = "Driver.";
    private static final int ADD_DRIVER_PROPS_PREFIX_LENGTH = "Driver.".length();

    private final Object POOL_LOCK = new Object();
    private List idleConnections = new ArrayList();
    private List activeConnections = new ArrayList();
    private long requestCount = 0L;
    private long accumulatedRequestTime = 0L;
    private long accumulatedCheckoutTime = 0L;
    private long claimedOverdueConnectionCount = 0L;
    private long accumulatedCheckoutTimeOfOverdueConnections = 0L;
    private long accumulatedWaitTime = 0L;
    private long hadToWaitCount = 0L;
    private long badConnectionCount = 0L;
    private String jdbcDriver;
    private String jdbcUrl;
    private String jdbcUsername;
    private String jdbcPassword;
    private boolean jdbcDefaultAutoCommit;
    private Properties driverProps;
    private boolean useDriverProps;
    private int poolMaximumActiveConnections;
    private int poolMaximumIdleConnections;
    private int poolMaximumCheckoutTime;
    private int poolTimeToWait;
    private String poolPingQuery;
    private boolean poolPingEnabled;
    private int poolPingConnectionsOlderThan;
    private int poolPingConnectionsNotUsedFor;

    public SimpleDataSource(Map props) {
        initialize(props);
    }

    private void initialize(Map props) {
        try {
            String prop_pool_ping_query = null;

            if (props == null) {
                throw new RuntimeException("SimpleDataSource: The properties map passed to the initializer was null.");
            }

            if ((!props.containsKey("JDBC.Driver")) || (!props.containsKey("JDBC.ConnectionURL")) ||
                    (!props.containsKey("JDBC.Username")) || (!props.containsKey("JDBC.Password"))) {
                throw new RuntimeException("SimpleDataSource: Some properties were not set.");
            }

            this.jdbcDriver = ((String) props.get("JDBC.Driver"));
            this.jdbcUrl = ((String) props.get("JDBC.ConnectionURL"));
            this.jdbcUsername = ((String) props.get("JDBC.Username"));
            this.jdbcPassword = ((String) props.get("JDBC.Password"));

            this.poolMaximumActiveConnections = (props.containsKey("Pool.MaximumActiveConnections") ?
                    Integer.parseInt((String) props.get("Pool.MaximumActiveConnections")) : 10);

            this.poolMaximumIdleConnections = (props.containsKey("Pool.MaximumIdleConnections") ?
                    Integer.parseInt((String) props.get("Pool.MaximumIdleConnections")) : 5);

            this.poolMaximumCheckoutTime = (props.containsKey("Pool.MaximumCheckoutTime") ?
                    Integer.parseInt((String) props.get("Pool.MaximumCheckoutTime")) : 20000);

            this.poolTimeToWait = (props.containsKey("Pool.TimeToWait") ? Integer.parseInt(
                    (String) props
                            .get("Pool.TimeToWait")) : 20000);

            this.poolPingEnabled = ((props.containsKey("Pool.PingEnabled")) &&
                    (Boolean.valueOf((String) props.get("Pool.PingEnabled")).booleanValue()));

            prop_pool_ping_query = (String) props.get("Pool.PingQuery");
            this.poolPingQuery = (props.containsKey("Pool.PingQuery") ? prop_pool_ping_query : "NO PING QUERY SET");

            this.poolPingConnectionsOlderThan = (props.containsKey("Pool.PingConnectionsOlderThan") ?
                    Integer.parseInt((String) props.get("Pool.PingConnectionsOlderThan")) : 0);

            this.poolPingConnectionsNotUsedFor = (props.containsKey("Pool.PingConnectionsNotUsedFor") ?
                    Integer.parseInt((String) props.get("Pool.PingConnectionsNotUsedFor")) : 0);

            this.jdbcDefaultAutoCommit = ((props.containsKey("JDBC.DefaultAutoCommit")) &&
                    (Boolean.valueOf((String) props.get("JDBC.DefaultAutoCommit")).booleanValue()));

            this.useDriverProps = false;
            Iterator propIter = props.keySet().iterator();
            this.driverProps = new Properties();
            this.driverProps.put("user", this.jdbcUsername);
            this.driverProps.put("password", this.jdbcPassword);
            while (propIter.hasNext()) {
                String name = (String) propIter.next();
                String value = (String) props.get(name);
                if (name.startsWith("Driver.")) {
                    this.driverProps.put(name.substring(ADD_DRIVER_PROPS_PREFIX_LENGTH), value);
                    this.useDriverProps = true;
                }
            }

            this.expectedConnectionTypeCode = assembleConnectionTypeCode(this.jdbcUrl, this.jdbcUsername, this.jdbcPassword);

            Resources.instantiate(this.jdbcDriver);

            if ((this.poolPingEnabled) && (
                    (!props.containsKey("Pool.PingQuery")) || (prop_pool_ping_query.trim().length() == 0))) {
                throw new RuntimeException("SimpleDataSource: property 'Pool.PingEnabled' is true, but property 'Pool.PingQuery' is not set correctly.");
            }

        } catch (Exception e) {
            log.error("SimpleDataSource: Error while loading properties. Cause: " + e.toString(), e);
            throw new RuntimeException("SimpleDataSource: Error while loading properties. Cause: " + e, e);
        }
    }

    private int assembleConnectionTypeCode(String url, String username, String password) {
        return (url + username + password).hashCode();
    }

    public Connection getConnection()
            throws SQLException {
        return popConnection(this.jdbcUsername, this.jdbcPassword).getProxyConnection();
    }

    public Connection getConnection(String username, String password)
            throws SQLException {
        return popConnection(username, password).getProxyConnection();
    }

    public void setLoginTimeout(int loginTimeout)
            throws SQLException {
        DriverManager.setLoginTimeout(loginTimeout);
    }

    public int getLoginTimeout()
            throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    public void setLogWriter(PrintWriter logWriter)
            throws SQLException {
        DriverManager.setLogWriter(logWriter);
    }

    public PrintWriter getLogWriter()
            throws SQLException {
        return DriverManager.getLogWriter();
    }

    public int getPoolPingConnectionsNotUsedFor() {
        return this.poolPingConnectionsNotUsedFor;
    }

    public String getJdbcDriver() {
        return this.jdbcDriver;
    }

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    public String getJdbcUsername() {
        return this.jdbcUsername;
    }

    public String getJdbcPassword() {
        return this.jdbcPassword;
    }

    public int getPoolMaximumActiveConnections() {
        return this.poolMaximumActiveConnections;
    }

    public int getPoolMaximumIdleConnections() {
        return this.poolMaximumIdleConnections;
    }

    public int getPoolMaximumCheckoutTime() {
        return this.poolMaximumCheckoutTime;
    }

    public int getPoolTimeToWait() {
        return this.poolTimeToWait;
    }

    public String getPoolPingQuery() {
        return this.poolPingQuery;
    }

    public boolean isPoolPingEnabled() {
        return this.poolPingEnabled;
    }

    public int getPoolPingConnectionsOlderThan() {
        return this.poolPingConnectionsOlderThan;
    }

    private int getExpectedConnectionTypeCode() {
        return this.expectedConnectionTypeCode;
    }

    public long getRequestCount() {
        synchronized (this.POOL_LOCK) {
            return this.requestCount;
        }
    }

    public long getAverageRequestTime() {
        synchronized (this.POOL_LOCK) {
            return this.requestCount == 0L ? 0L : this.accumulatedRequestTime / this.requestCount;
        }
    }

    public long getAverageWaitTime() {
        synchronized (this.POOL_LOCK) {
            return this.hadToWaitCount == 0L ? 0L : this.accumulatedWaitTime / this.hadToWaitCount;
        }
    }

    public long getHadToWaitCount() {
        synchronized (this.POOL_LOCK) {
            return this.hadToWaitCount;
        }
    }

    public long getBadConnectionCount() {
        synchronized (this.POOL_LOCK) {
            return this.badConnectionCount;
        }
    }

    public long getClaimedOverdueConnectionCount() {
        synchronized (this.POOL_LOCK) {
            return this.claimedOverdueConnectionCount;
        }
    }

    public long getAverageOverdueCheckoutTime() {
        synchronized (this.POOL_LOCK) {
            return this.claimedOverdueConnectionCount == 0L ? 0L : this.accumulatedCheckoutTimeOfOverdueConnections /
                    this.claimedOverdueConnectionCount;
        }
    }

    public long getAverageCheckoutTime() {
        synchronized (this.POOL_LOCK) {
            return this.requestCount == 0L ? 0L : this.accumulatedCheckoutTime / this.requestCount;
        }
    }

    public String getStatus() {
        StringBuffer buffer = new StringBuffer();

        buffer.append("\n===============================================================");
        buffer.append("\n jdbcDriver                     ").append(this.jdbcDriver);
        buffer.append("\n jdbcUrl                        ").append(this.jdbcUrl);
        buffer.append("\n jdbcUsername                   ").append(this.jdbcUsername);
        buffer.append("\n jdbcPassword                   ").append(this.jdbcPassword == null ? "NULL" : "************");
        buffer.append("\n poolMaxActiveConnections       ").append(this.poolMaximumActiveConnections);
        buffer.append("\n poolMaxIdleConnections         ").append(this.poolMaximumIdleConnections);
        buffer.append("\n poolMaxCheckoutTime            " + this.poolMaximumCheckoutTime);
        buffer.append("\n poolTimeToWait                 " + this.poolTimeToWait);
        buffer.append("\n poolPingEnabled                " + this.poolPingEnabled);
        buffer.append("\n poolPingQuery                  " + this.poolPingQuery);
        buffer.append("\n poolPingConnectionsOlderThan   " + this.poolPingConnectionsOlderThan);
        buffer.append("\n poolPingConnectionsNotUsedFor  " + this.poolPingConnectionsNotUsedFor);
        buffer.append("\n --------------------------------------------------------------");
        buffer.append("\n activeConnections              " + this.activeConnections.size());
        buffer.append("\n idleConnections                " + this.idleConnections.size());
        buffer.append("\n requestCount                   " + getRequestCount());
        buffer.append("\n averageRequestTime             " + getAverageRequestTime());
        buffer.append("\n averageCheckoutTime            " + getAverageCheckoutTime());
        buffer.append("\n claimedOverdue                 " + getClaimedOverdueConnectionCount());
        buffer.append("\n averageOverdueCheckoutTime     " + getAverageOverdueCheckoutTime());
        buffer.append("\n hadToWait                      " + getHadToWaitCount());
        buffer.append("\n averageWaitTime                " + getAverageWaitTime());
        buffer.append("\n badConnectionCount             " + getBadConnectionCount());
        buffer.append("\n===============================================================");
        return buffer.toString();
    }

    public void forceCloseAll() {
        synchronized (this.POOL_LOCK) {
            for (int i = this.activeConnections.size(); i > 0; i--)
                try {
                    SimplePooledConnection conn = (SimplePooledConnection) this.activeConnections.remove(i - 1);
                    conn.invalidate();

                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    realConn.close();
                } catch (Exception localException) {
                }
            for (int i = this.idleConnections.size(); i > 0; i--)
                try {
                    SimplePooledConnection conn = (SimplePooledConnection) this.idleConnections.remove(i - 1);
                    conn.invalidate();

                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    realConn.close();
                } catch (Exception localException1) {
                }
        }
        if (log.isDebugEnabled())
            log.debug("SimpleDataSource forcefully closed/removed all connections.");
    }

    private void pushConnection(SimplePooledConnection conn)
            throws SQLException {
        synchronized (this.POOL_LOCK) {
            this.activeConnections.remove(conn);
            if (conn.isValid()) {
                if ((this.idleConnections.size() < this.poolMaximumIdleConnections) &&
                        (conn.getConnectionTypeCode() == getExpectedConnectionTypeCode())) {
                    this.accumulatedCheckoutTime += conn.getCheckoutTime();
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    SimplePooledConnection newConn = new SimplePooledConnection(conn.getRealConnection(), this);
                    this.idleConnections.add(newConn);
                    newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
                    newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
                    conn.invalidate();
                    if (log.isDebugEnabled()) {
                        log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
                    }
                    this.POOL_LOCK.notifyAll();
                } else {
                    this.accumulatedCheckoutTime += conn.getCheckoutTime();
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    conn.getRealConnection().close();
                    if (log.isDebugEnabled()) {
                        log.debug("Closed connection " + conn.getRealHashCode() + ".");
                    }
                    conn.invalidate();
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("A bad connection (" + conn.getRealHashCode() +
                            ") attempted to return to the pool, discarding connection.");
                }
                this.badConnectionCount += 1L;
            }
        }
    }

    private SimplePooledConnection popConnection(String username, String password) throws SQLException {
        boolean countedWait = false;
        SimplePooledConnection conn = null;
        long t = System.currentTimeMillis();
        int localBadConnectionCount = 0;

        while (conn == null) {
            synchronized (this.POOL_LOCK) {
                if (this.idleConnections.size() > 0) {
                    conn = (SimplePooledConnection) this.idleConnections.remove(0);
                    if (log.isDebugEnabled()) {
                        log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
                    }

                } else if (this.activeConnections.size() < this.poolMaximumActiveConnections) {
                    if (this.useDriverProps)
                        conn = new SimplePooledConnection(DriverManager.getConnection(this.jdbcUrl, this.driverProps), this);
                    else {
                        conn = new SimplePooledConnection(DriverManager.getConnection(this.jdbcUrl, this.jdbcUsername,
                                this.jdbcPassword), this);
                    }
                    Connection realConn = conn.getRealConnection();
                    if (realConn.getAutoCommit() != this.jdbcDefaultAutoCommit) {
                        realConn.setAutoCommit(this.jdbcDefaultAutoCommit);
                    }
                    if (log.isDebugEnabled())
                        log.debug("Created connection " + conn.getRealHashCode() + ".");
                } else {
                    SimplePooledConnection oldestActiveConnection =
                            (SimplePooledConnection) this.activeConnections
                                    .get(0);
                    long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
                    if (longestCheckoutTime > this.poolMaximumCheckoutTime) {
                        this.claimedOverdueConnectionCount += 1L;
                        this.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
                        this.accumulatedCheckoutTime += longestCheckoutTime;
                        this.activeConnections.remove(oldestActiveConnection);
                        if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                            oldestActiveConnection.getRealConnection().rollback();
                        }
                        conn = new SimplePooledConnection(oldestActiveConnection.getRealConnection(), this);
                        oldestActiveConnection.invalidate();
                        if (log.isDebugEnabled())
                            log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
                    } else {
                        try {
                            if (!countedWait) {
                                this.hadToWaitCount += 1L;
                                countedWait = true;
                            }
                            if (log.isDebugEnabled()) {
                                log.debug("Waiting as long as " + this.poolTimeToWait + " milliseconds for connection.");
                            }
                            long wt = System.currentTimeMillis();
                            this.POOL_LOCK.wait(this.poolTimeToWait);
                            this.accumulatedWaitTime += System.currentTimeMillis() - wt;
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }

                if (conn != null) {
                    if (conn.isValid()) {
                        if (!conn.getRealConnection().getAutoCommit()) {
                            conn.getRealConnection().rollback();
                        }
                        conn.setConnectionTypeCode(assembleConnectionTypeCode(this.jdbcUrl, username, password));
                        conn.setCheckoutTimestamp(System.currentTimeMillis());
                        conn.setLastUsedTimestamp(System.currentTimeMillis());
                        this.activeConnections.add(conn);
                        this.requestCount += 1L;
                        this.accumulatedRequestTime += System.currentTimeMillis() - t;
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("A bad connection (" + conn.getRealHashCode() +
                                    ") was returned from the pool, getting another connection.");
                        }
                        this.badConnectionCount += 1L;
                        localBadConnectionCount++;
                        conn = null;
                        if (localBadConnectionCount > this.poolMaximumIdleConnections + 3) {
                            if (log.isDebugEnabled()) {
                                log.debug("SimpleDataSource: Could not get a good connection to the database.");
                            }
                            throw new SQLException("SimpleDataSource: Could not get a good connection to the database.");
                        }
                    }
                }
            }

        }

        if (conn == null) {
            if (log.isDebugEnabled()) {
                log.debug("SimpleDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
            }
            throw new SQLException(
                    "SimpleDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
        }

        return conn;
    }

    private boolean pingConnection(SimplePooledConnection conn) {
        boolean result = true;
        try {
            result = !conn.getRealConnection().isClosed();
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
            result = false;
        }

        if ((result) &&
                (this.poolPingEnabled) && (
                ((this.poolPingConnectionsOlderThan > 0) && (conn.getAge() > this.poolPingConnectionsOlderThan)) || (
                        (this.poolPingConnectionsNotUsedFor > 0) && (conn.getTimeElapsedSinceLastUse() > this.poolPingConnectionsNotUsedFor)))) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Testing connection " + conn.getRealHashCode() + " ...");
                }
                Connection realConn = conn.getRealConnection();
                Statement statement = realConn.createStatement();
                ResultSet rs = statement.executeQuery(this.poolPingQuery);
                rs.close();
                statement.close();
                if (!realConn.getAutoCommit()) {
                    realConn.rollback();
                }
                result = true;
                if (log.isDebugEnabled())
                    log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
            } catch (Exception e) {
                log.warn("Execution of ping query '" + this.poolPingQuery + "' failed: " + e.getMessage());
                try {
                    conn.getRealConnection().close();
                } catch (Exception localException1) {
                }
                result = false;
                if (log.isDebugEnabled()) {
                    log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
                }
            }

        }

        return result;
    }

    public static Connection unwrapConnection(Connection conn) {
        if ((conn instanceof SimplePooledConnection)) {
            return ((SimplePooledConnection) conn).getRealConnection();
        }
        return conn;
    }

    protected void finalize() throws Throwable {
        forceCloseAll();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public static class SimplePooledConnection
            implements InvocationHandler {
        private static final String CLOSE = "close";
        private static final Class[] IFACES = {Connection.class};

        private int hashCode = 0;
        private SimpleDataSource dataSource;
        private Connection realConnection;
        private Connection proxyConnection;
        private long checkoutTimestamp;
        private long createdTimestamp;
        private long lastUsedTimestamp;
        private int connectionTypeCode;
        private boolean valid;

        public SimplePooledConnection(Connection connection, SimpleDataSource dataSource) {
            this.hashCode = connection.hashCode();
            this.realConnection = connection;
            this.dataSource = dataSource;
            this.createdTimestamp = System.currentTimeMillis();
            this.lastUsedTimestamp = System.currentTimeMillis();
            this.valid = true;

            this.proxyConnection = ((Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this));
        }

        public void invalidate() {
            this.valid = false;
        }

        public boolean isValid() {
            return (this.valid) && (this.realConnection != null) && (this.dataSource.pingConnection(this));
        }

        public Connection getRealConnection() {
            return this.realConnection;
        }

        public Connection getProxyConnection() {
            return this.proxyConnection;
        }

        public int getRealHashCode() {
            if (this.realConnection == null) {
                return 0;
            }
            return this.realConnection.hashCode();
        }

        public int getConnectionTypeCode() {
            return this.connectionTypeCode;
        }

        public void setConnectionTypeCode(int connectionTypeCode) {
            this.connectionTypeCode = connectionTypeCode;
        }

        public long getCreatedTimestamp() {
            return this.createdTimestamp;
        }

        public void setCreatedTimestamp(long createdTimestamp) {
            this.createdTimestamp = createdTimestamp;
        }

        public long getLastUsedTimestamp() {
            return this.lastUsedTimestamp;
        }

        public void setLastUsedTimestamp(long lastUsedTimestamp) {
            this.lastUsedTimestamp = lastUsedTimestamp;
        }

        public long getTimeElapsedSinceLastUse() {
            return System.currentTimeMillis() - this.lastUsedTimestamp;
        }

        public long getAge() {
            return System.currentTimeMillis() - this.createdTimestamp;
        }

        public long getCheckoutTimestamp() {
            return this.checkoutTimestamp;
        }

        public void setCheckoutTimestamp(long timestamp) {
            this.checkoutTimestamp = timestamp;
        }

        public long getCheckoutTime() {
            return System.currentTimeMillis() - this.checkoutTimestamp;
        }

        private Connection getValidConnection() {
            if (!this.valid) {
                throw new RuntimeException("Error accessing SimplePooledConnection. Connection is invalid.");
            }
            return this.realConnection;
        }

        public int hashCode() {
            return this.hashCode;
        }

        public boolean equals(Object obj) {
            if ((obj instanceof SimplePooledConnection))
                return this.realConnection.hashCode() == ((SimplePooledConnection) obj).realConnection.hashCode();
            if ((obj instanceof Connection)) {
                return this.hashCode == obj.hashCode();
            }
            return false;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String methodName = method.getName();
            if (("close".hashCode() == methodName.hashCode()) && ("close".equals(methodName))) {
                this.dataSource.pushConnection(this);
                return null;
            }
            try {
                return method.invoke(getValidConnection(), args);
            } catch (Throwable t) {
                throw ClassInfo.unwrapThrowable(t);
            }
        }

        public Statement createStatement() throws SQLException {
            return getValidConnection().createStatement();
        }

        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return getValidConnection().prepareStatement(sql);
        }

        public CallableStatement prepareCall(String sql) throws SQLException {
            return getValidConnection().prepareCall(sql);
        }

        public String nativeSQL(String sql) throws SQLException {
            return getValidConnection().nativeSQL(sql);
        }

        public void setAutoCommit(boolean autoCommit) throws SQLException {
            getValidConnection().setAutoCommit(autoCommit);
        }

        public boolean getAutoCommit() throws SQLException {
            return getValidConnection().getAutoCommit();
        }

        public void commit() throws SQLException {
            getValidConnection().commit();
        }

        public void rollback() throws SQLException {
            getValidConnection().rollback();
        }

        public void close() throws SQLException {
            this.dataSource.pushConnection(this);
        }

        public boolean isClosed() throws SQLException {
            return getValidConnection().isClosed();
        }

        public DatabaseMetaData getMetaData() throws SQLException {
            return getValidConnection().getMetaData();
        }

        public void setReadOnly(boolean readOnly) throws SQLException {
            getValidConnection().setReadOnly(readOnly);
        }

        public boolean isReadOnly() throws SQLException {
            return getValidConnection().isReadOnly();
        }

        public void setCatalog(String catalog) throws SQLException {
            getValidConnection().setCatalog(catalog);
        }

        public String getCatalog() throws SQLException {
            return getValidConnection().getCatalog();
        }

        public void setTransactionIsolation(int level) throws SQLException {
            getValidConnection().setTransactionIsolation(level);
        }

        public int getTransactionIsolation() throws SQLException {
            return getValidConnection().getTransactionIsolation();
        }

        public SQLWarning getWarnings() throws SQLException {
            return getValidConnection().getWarnings();
        }

        public void clearWarnings() throws SQLException {
            getValidConnection().clearWarnings();
        }

        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return getValidConnection().createStatement(resultSetType, resultSetConcurrency);
        }

        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return getValidConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return getValidConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        public Map getTypeMap() throws SQLException {
            return getValidConnection().getTypeMap();
        }

        public void setTypeMap(Map map) throws SQLException {
            getValidConnection().setTypeMap(map);
        }

        public void setHoldability(int holdability)
                throws SQLException {
            getValidConnection().setHoldability(holdability);
        }

        public int getHoldability() throws SQLException {
            return getValidConnection().getHoldability();
        }

        public Savepoint setSavepoint() throws SQLException {
            return getValidConnection().setSavepoint();
        }

        public Savepoint setSavepoint(String name) throws SQLException {
            return getValidConnection().setSavepoint(name);
        }

        public void rollback(Savepoint savepoint) throws SQLException {
            getValidConnection().rollback(savepoint);
        }

        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            getValidConnection().releaseSavepoint(savepoint);
        }

        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return getValidConnection().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return getValidConnection()
                    .prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return getValidConnection().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return getValidConnection().prepareStatement(sql, autoGeneratedKeys);
        }

        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return getValidConnection().prepareStatement(sql, columnIndexes);
        }

        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return getValidConnection().prepareStatement(sql, columnNames);
        }
    }
}