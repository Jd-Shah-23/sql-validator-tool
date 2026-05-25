package com.ibm.aip.validator.validator;

import com.ibm.aip.validator.extractor.SQLQuery;
import com.ibm.aip.validator.extractor.ValidationResult;
import validator.EnhancedSQLRewriter;
import validator.SyntaxValidator;
import validator.SyntaxValidator.SyntaxValidationResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-database SQL validator that checks compatibility across DB2, PostgreSQL, and Oracle
 * 
 * @author AIP Innovation Team
 */
public class MultiDatabaseValidator {
    
    private Properties config;
    private boolean testWithLiveDB = false;
    private EnhancedSQLRewriter rewriter;
    private SyntaxValidator syntaxValidator;
    
    // Database-specific patterns
    private static final Pattern SEQUENCE_NEXTVAL_DB2 = Pattern.compile(
        "NEXTVAL\\s+FOR\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQUENCE_NEXTVAL_POSTGRES = Pattern.compile(
        "NEXTVAL\\s*\\(\\s*'(\\w+)'\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQUENCE_NEXTVAL_ORACLE = Pattern.compile(
        "(\\w+)\\.NEXTVAL", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern ROW_NUMBER_PATTERN = Pattern.compile(
        "ROW_NUMBER\\s*\\(\\s*\\)\\s+OVER", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROWNUM_PATTERN = Pattern.compile(
        "\\bROWNUM\\b", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LIMIT_OFFSET = Pattern.compile(
        "LIMIT\\s+\\d+\\s+OFFSET\\s+\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern FETCH_FIRST = Pattern.compile(
        "FETCH\\s+FIRST\\s+\\d+\\s+ROWS", Pattern.CASE_INSENSITIVE);
    
    public MultiDatabaseValidator() {
        this.config = null;
        this.testWithLiveDB = false;
        this.rewriter = new EnhancedSQLRewriter();
        this.syntaxValidator = new SyntaxValidator();
    }
    
    /**
     * Validate a SQL query against all databases using static analysis only
     */
    public void validateQuery(SQLQuery query) {
        // Step 1: Check for syntax errors FIRST
        SyntaxValidationResult syntaxResult = syntaxValidator.validateSyntax(query.getNormalizedQuery());
        if (!syntaxResult.isValid()) {
            query.setSyntaxErrors(syntaxResult.getErrors());
            // Still continue with database validation to show what would be issues
            // if syntax was fixed
        }
        
        // Step 2: Validate for DB2
        ValidationResult db2Result = validateForDB2(query);
        query.addValidationResult(db2Result);
        
        // Step 3: Validate for PostgreSQL
        ValidationResult postgresResult = validateForPostgreSQL(query);
        query.addValidationResult(postgresResult);
        
        // Step 4: Validate for Oracle
        ValidationResult oracleResult = validateForOracle(query);
        query.addValidationResult(oracleResult);
    }
    
    /**
     * Validate SQL for DB2 compatibility
     */
    private ValidationResult validateForDB2(SQLQuery query) {
        ValidationResult result = new ValidationResult("DB2");
        String sql = query.getNormalizedQuery();
        
        // Check for PostgreSQL-specific syntax
        if (SEQUENCE_NEXTVAL_POSTGRES.matcher(sql).find()) {
            result.addIssue("PostgreSQL sequence syntax detected");
            result.addSuggestion("Use: NEXTVAL FOR sequencename");
            result.setStatus("WARN");
        }
        
        // Check for Oracle-specific syntax
        if (SEQUENCE_NEXTVAL_ORACLE.matcher(sql).find()) {
            result.addIssue("Oracle sequence syntax detected");
            result.addSuggestion("Use: NEXTVAL FOR sequencename");
            result.setStatus("WARN");
        }
        
        if (ROWNUM_PATTERN.matcher(sql).find()) {
            result.addIssue("Oracle ROWNUM not supported");
            result.addSuggestion("Use: ROW_NUMBER() OVER (ORDER BY ...) or FETCH FIRST n ROWS");
            result.setValid(false);
        }
        
        if (LIMIT_OFFSET.matcher(sql).find()) {
            result.addIssue("PostgreSQL LIMIT/OFFSET syntax");
            result.addSuggestion("Use: FETCH FIRST n ROWS ONLY");
            result.setStatus("WARN");
        }
        
        // Check for common DB2 compatibility issues
        checkCommonIssues(sql, result, "DB2");
        
        return result;
    }
    
    /**
     * Validate SQL for PostgreSQL compatibility
     */
    private ValidationResult validateForPostgreSQL(SQLQuery query) {
        ValidationResult result = new ValidationResult("PostgreSQL");
        String sql = query.getNormalizedQuery();
        
        // Check for DB2-specific syntax
        if (SEQUENCE_NEXTVAL_DB2.matcher(sql).find()) {
            result.addIssue("DB2 sequence syntax detected");
            result.addSuggestion("Use: NEXTVAL('sequencename')");
            result.setStatus("WARN");
        }
        
        // Check for Oracle-specific syntax
        if (SEQUENCE_NEXTVAL_ORACLE.matcher(sql).find()) {
            result.addIssue("Oracle sequence syntax detected");
            result.addSuggestion("Use: NEXTVAL('sequencename')");
            result.setStatus("WARN");
        }
        
        if (ROWNUM_PATTERN.matcher(sql).find()) {
            result.addIssue("Oracle ROWNUM not supported");
            result.addSuggestion("Use: LIMIT n OFFSET m or ROW_NUMBER() OVER (ORDER BY ...)");
            result.setValid(false);
        }
        
        if (FETCH_FIRST.matcher(sql).find()) {
            result.addIssue("DB2 FETCH FIRST syntax");
            result.addSuggestion("Use: LIMIT n OFFSET m");
            result.setStatus("WARN");
        }
        
        // Check for common PostgreSQL compatibility issues
        checkCommonIssues(sql, result, "PostgreSQL");
        
        return result;
    }
    
    /**
     * Validate SQL for Oracle compatibility
     */
    private ValidationResult validateForOracle(SQLQuery query) {
        ValidationResult result = new ValidationResult("Oracle");
        String sql = query.getNormalizedQuery();
        
        // Check for DB2-specific syntax
        if (SEQUENCE_NEXTVAL_DB2.matcher(sql).find()) {
            result.addIssue("DB2 sequence syntax detected");
            result.addSuggestion("Use: sequencename.NEXTVAL");
            result.setStatus("WARN");
        }
        
        // Check for PostgreSQL-specific syntax
        if (SEQUENCE_NEXTVAL_POSTGRES.matcher(sql).find()) {
            result.addIssue("PostgreSQL sequence syntax detected");
            result.addSuggestion("Use: sequencename.NEXTVAL");
            result.setStatus("WARN");
        }
        
        if (LIMIT_OFFSET.matcher(sql).find()) {
            result.addIssue("PostgreSQL LIMIT/OFFSET not supported");
            result.addSuggestion("Use: ROWNUM or ROW_NUMBER() OVER (ORDER BY ...)");
            result.setValid(false);
        }
        
        if (FETCH_FIRST.matcher(sql).find()) {
            result.addIssue("DB2 FETCH FIRST syntax");
            result.addSuggestion("Use: WHERE ROWNUM <= n");
            result.setStatus("WARN");
        }
        
        // Check for common Oracle compatibility issues
        checkCommonIssues(sql, result, "Oracle");
        
        return result;
    }
    
    /**
     * Check for common compatibility issues across all databases
     */
    private void checkCommonIssues(String sql, ValidationResult result, String dbType) {
        // Check for date/time functions
        if (sql.toUpperCase().contains("NOW()")) {
            if ("DB2".equals(dbType)) {
                result.addIssue("NOW() function");
                result.addSuggestion("Use: CURRENT TIMESTAMP");
            } else if ("Oracle".equals(dbType)) {
                result.addIssue("NOW() function");
                result.addSuggestion("Use: SYSDATE or SYSTIMESTAMP");
            }
        }
        
        if (sql.toUpperCase().contains("SYSDATE") && !"Oracle".equals(dbType)) {
            result.addIssue("Oracle SYSDATE function");
            if ("DB2".equals(dbType)) {
                result.addSuggestion("Use: CURRENT TIMESTAMP");
            } else {
                result.addSuggestion("Use: NOW() or CURRENT_TIMESTAMP");
            }
        }
        
        // Check for string concatenation
        if (sql.contains(" + ") && sql.matches(".*'[^']*'\\s*\\+\\s*'[^']*'.*")) {
            if (!"Oracle".equals(dbType) && !"PostgreSQL".equals(dbType)) {
                result.addIssue("String concatenation with + operator");
                result.addSuggestion("Use: || operator or CONCAT() function");
            }
        }
        
        // Check for boolean data type
        if (sql.toUpperCase().matches(".*\\bBOOLEAN\\b.*")) {
            if ("DB2".equals(dbType) || "Oracle".equals(dbType)) {
                result.addIssue("BOOLEAN data type not supported");
                result.addSuggestion("Use: SMALLINT or CHAR(1) with 'Y'/'N'");
            }
        }
        
        // Check for AUTO_INCREMENT
        if (sql.toUpperCase().contains("AUTO_INCREMENT")) {
            result.addIssue("MySQL AUTO_INCREMENT syntax");
            if ("DB2".equals(dbType)) {
                result.addSuggestion("Use: GENERATED ALWAYS AS IDENTITY");
            } else if ("PostgreSQL".equals(dbType)) {
                result.addSuggestion("Use: SERIAL or GENERATED ALWAYS AS IDENTITY");
            } else {
                result.addSuggestion("Use: SEQUENCE with trigger");
            }
        }
    }
    
    /**
     * Generate database-agnostic SQL rewrite if query is not compatible with all databases
     */
    public String generateDatabaseAgnosticSuggestion(SQLQuery query) {
        String sql = query.getNormalizedQuery();
        
        // Check if query has any issues (including warnings)
        if (!query.hasAnyIssues()) {
            return null; // No suggestion needed - query is perfect
        }
        
        StringBuilder result = new StringBuilder();
        result.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        result.append("║   REWRITTEN SQL - Works on DB2, PostgreSQL & Oracle         ║\n");
        result.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        String rewrittenSQL = rewriteToAgnosticSQL(sql);
        
        if (rewrittenSQL != null && !rewrittenSQL.equals(sql)) {
            result.append("Original Query:\n");
            result.append("  " + formatSQL(sql) + "\n\n");
            result.append("✅ Database-Agnostic Query (Works on ALL 3 databases):\n");
            result.append("  " + formatSQL(rewrittenSQL) + "\n\n");
            result.append("═══════════════════════════════════════════════════════════════\n");
            result.append("💡 This rewritten query maintains the same logic and output\n");
            result.append("   while ensuring compatibility across all databases.\n");
            result.append("═══════════════════════════════════════════════════════════════\n");
            return result.toString();
        }
        
        return null;
    }
    
    /**
     * Rewrite SQL to database-agnostic version using EnhancedSQLRewriter
     */
    private String rewriteToAgnosticSQL(String sql) {
        // Use the enhanced rewriter that handles multiple issues properly
        return rewriter.rewriteToAgnostic(sql);
    }
    
    /**
     * Format SQL for display with proper line breaks
     */
    private String formatSQL(String sql) {
        // Don't truncate - show full query with line breaks for readability
        StringBuilder formatted = new StringBuilder();
        int lineLength = 70;
        
        if (sql.length() <= lineLength) {
            return sql;
        }
        
        // Break long queries into multiple lines
        String[] words = sql.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > lineLength) {
                formatted.append(currentLine.toString()).append("\n  ");
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            formatted.append(currentLine.toString());
        }
        
        return formatted.toString();
    }
    
    /**
     * Test query execution against live database
     */
    private void testWithLiveDatabase(SQLQuery query, ValidationResult result, String dbType) {
        if (config == null) return;
        
        String url = config.getProperty(dbType.toLowerCase() + ".url");
        String username = config.getProperty(dbType.toLowerCase() + ".username");
        String password = config.getProperty(dbType.toLowerCase() + ".password");
        
        if (url == null || username == null || password == null) {
            return; // Skip if config not available
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DriverManager.getConnection(url, username, password);
            
            // Try to prepare the statement (syntax check)
            String testSQL = query.getNormalizedQuery();
            
            // Replace ? placeholders with dummy values for syntax check
            testSQL = testSQL.replaceAll("\\?", "NULL");
            
            stmt = conn.prepareStatement(testSQL);
            
            // If we get here, syntax is valid
            if (!"PASS".equals(result.getStatus())) {
                result.addSuggestion("Live test passed - query is executable");
            }
            
        } catch (SQLException e) {
            result.setValid(false);
            result.setErrorMessage("SQL Error: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Validate multiple queries
     */
    public void validateQueries(List<SQLQuery> queries) {
        for (SQLQuery query : queries) {
            validateQuery(query);
        }
    }
}

// Made with Bob
