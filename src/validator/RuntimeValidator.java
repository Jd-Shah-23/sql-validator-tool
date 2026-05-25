package com.ibm.aip.validator.validator;

import com.ibm.aip.validator.extractor.SQLQuery;
import java.sql.*;
import java.util.*;

/**
 * Runtime validator that executes SELECT queries on actual databases
 * and compares result counts across DB2, PostgreSQL, and Oracle
 * 
 * @author AIP Innovation Team
 */
public class RuntimeValidator {
    
    private Connection db2Connection;
    private Connection postgresConnection;
    private Connection oracleConnection;
    
    public static class DatabaseConfig {
        public String url;
        public String username;
        public String password;
        public String driverClass;
        
        public DatabaseConfig(String url, String username, String password, String driverClass) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.driverClass = driverClass;
        }
    }
    
    public static class RuntimeResult {
        public String database;
        public boolean executed;
        public int rowCount;
        public String error;
        public long executionTimeMs;
        
        public RuntimeResult(String database) {
            this.database = database;
            this.executed = false;
            this.rowCount = -1;
            this.error = null;
            this.executionTimeMs = 0;
        }
    }
    
    /**
     * Initialize database connections
     */
    public boolean connect(DatabaseConfig db2Config, DatabaseConfig postgresConfig, DatabaseConfig oracleConfig) {
        boolean allConnected = true;
        
        // Connect to DB2
        if (db2Config != null) {
            try {
                Class.forName(db2Config.driverClass);
                db2Connection = DriverManager.getConnection(
                    db2Config.url, 
                    db2Config.username, 
                    db2Config.password
                );
                System.out.println("✅ Connected to DB2");
            } catch (Exception e) {
                System.err.println("❌ Failed to connect to DB2: " + e.getMessage());
                allConnected = false;
            }
        }
        
        // Connect to PostgreSQL
        if (postgresConfig != null) {
            try {
                Class.forName(postgresConfig.driverClass);
                postgresConnection = DriverManager.getConnection(
                    postgresConfig.url,
                    postgresConfig.username,
                    postgresConfig.password
                );
                System.out.println("✅ Connected to PostgreSQL");
            } catch (Exception e) {
                System.err.println("❌ Failed to connect to PostgreSQL: " + e.getMessage());
                allConnected = false;
            }
        }
        
        // Connect to Oracle
        if (oracleConfig != null) {
            try {
                Class.forName(oracleConfig.driverClass);
                oracleConnection = DriverManager.getConnection(
                    oracleConfig.url,
                    oracleConfig.username,
                    oracleConfig.password
                );
                System.out.println("✅ Connected to Oracle");
            } catch (Exception e) {
                System.err.println("❌ Failed to connect to Oracle: " + e.getMessage());
                allConnected = false;
            }
        }
        
        return allConnected;
    }
    
    /**
     * Execute SELECT query and return row count
     * SAFETY: Only SELECT queries are executed - all data modification queries are blocked
     */
    private RuntimeResult executeQuery(Connection conn, String database, String sql) {
        RuntimeResult result = new RuntimeResult(database);
        
        if (conn == null) {
            result.error = "No connection available";
            return result;
        }
        
        // CRITICAL SAFETY CHECK: Only execute SELECT queries
        // Block all data modification operations
        String sqlUpper = sql.trim().toUpperCase();
        
        // Check if it starts with SELECT
        if (!sqlUpper.startsWith("SELECT")) {
            result.error = "BLOCKED: Only SELECT queries are executed for safety (found: " +
                          sqlUpper.substring(0, Math.min(20, sqlUpper.length())) + "...)";
            return result;
        }
        
        // Additional safety: Check for dangerous keywords anywhere in the query
        // This prevents queries like "SELECT * FROM (DELETE FROM ...)"
        String[] dangerousKeywords = {
            "INSERT ", "UPDATE ", "DELETE ", "DROP ", "TRUNCATE ",
            "ALTER ", "CREATE ", "GRANT ", "REVOKE ", "EXEC ",
            "EXECUTE ", "CALL ", "MERGE "
        };
        
        for (String keyword : dangerousKeywords) {
            if (sqlUpper.contains(keyword)) {
                result.error = "BLOCKED: Query contains dangerous keyword '" + keyword.trim() +
                              "' - only pure SELECT queries are allowed";
                return result;
            }
        }
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            int count = 0;
            while (rs.next()) {
                count++;
            }
            
            result.executed = true;
            result.rowCount = count;
            result.executionTimeMs = System.currentTimeMillis() - startTime;
            
        } catch (SQLException e) {
            result.error = e.getMessage();
            result.executionTimeMs = System.currentTimeMillis() - startTime;
        }
        
        return result;
    }
    
    /**
     * Validate query across all databases and compare results
     */
    public Map<String, RuntimeResult> validateQuery(SQLQuery query) {
        Map<String, RuntimeResult> results = new HashMap<>();
        
        String sql = query.getNormalizedQuery();
        
        // Execute on DB2
        if (db2Connection != null) {
            results.put("DB2", executeQuery(db2Connection, "DB2", sql));
        }
        
        // Execute on PostgreSQL
        if (postgresConnection != null) {
            results.put("PostgreSQL", executeQuery(postgresConnection, "PostgreSQL", sql));
        }
        
        // Execute on Oracle
        if (oracleConnection != null) {
            results.put("Oracle", executeQuery(oracleConnection, "Oracle", sql));
        }
        
        return results;
    }
    
    /**
     * Check if all databases returned the same row count
     */
    public boolean areResultsConsistent(Map<String, RuntimeResult> results) {
        Set<Integer> rowCounts = new HashSet<>();
        
        for (RuntimeResult result : results.values()) {
            if (result.executed) {
                rowCounts.add(result.rowCount);
            }
        }
        
        // All executed queries should return the same count
        return rowCounts.size() <= 1;
    }
    
    /**
     * Close all database connections
     */
    public void disconnect() {
        try {
            if (db2Connection != null && !db2Connection.isClosed()) {
                db2Connection.close();
                System.out.println("✅ Disconnected from DB2");
            }
        } catch (SQLException e) {
            System.err.println("Error closing DB2 connection: " + e.getMessage());
        }
        
        try {
            if (postgresConnection != null && !postgresConnection.isClosed()) {
                postgresConnection.close();
                System.out.println("✅ Disconnected from PostgreSQL");
            }
        } catch (SQLException e) {
            System.err.println("Error closing PostgreSQL connection: " + e.getMessage());
        }
        
        try {
            if (oracleConnection != null && !oracleConnection.isClosed()) {
                oracleConnection.close();
                System.out.println("✅ Disconnected from Oracle");
            }
        } catch (SQLException e) {
            System.err.println("Error closing Oracle connection: " + e.getMessage());
        }
    }
    
    /**
     * Test database connectivity
     */
    public static boolean testConnection(DatabaseConfig config) {
        try {
            Class.forName(config.driverClass);
            Connection conn = DriverManager.getConnection(
                config.url,
                config.username,
                config.password
            );
            conn.close();
            return true;
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }
}

// Made with Bob
