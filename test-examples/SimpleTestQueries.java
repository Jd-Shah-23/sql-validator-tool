package com.ibm.test;

/**
 * Simple test queries to demonstrate database-agnostic SQL rewriting
 */
public class SimpleTestQueries {
    
    // Test 1: PostgreSQL LIMIT syntax
    public void testPaginationPostgres() {
        String sql = "SELECT * FROM orders WHERE status = 'ACTIVE' LIMIT 10 OFFSET 20";
        // This will be rewritten to ROW_NUMBER() syntax
    }
    
    // Test 2: Oracle SYSDATE
    public void testOracleSysdate() {
        String sql = "SELECT * FROM orders WHERE order_date > SYSDATE";
        // This will be rewritten to CURRENT_TIMESTAMP
    }
    
    // Test 3: PostgreSQL NOW()
    public void testPostgresNow() {
        String sql = "SELECT * FROM users WHERE created_date > NOW()";
        // This will be rewritten to CURRENT_TIMESTAMP
    }
    
    // Test 4: String concatenation with +
    public void testStringConcat() {
        String sql = "SELECT 'Hello' + ' ' + 'World' FROM dual";
        // This will be rewritten to use || operator
    }
    
    // Test 5: Boolean data type
    public void testBooleanType() {
        String sql = "CREATE TABLE settings (id INT, is_active BOOLEAN DEFAULT TRUE)";
        // This will be rewritten to use SMALLINT
    }
}

// Made with Bob
