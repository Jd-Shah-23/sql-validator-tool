package validator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced SQL Rewriter using JSqlParser for robust multi-issue handling
 * Handles queries with multiple database-specific syntax issues
 */
public class EnhancedSQLRewriter {
    
    private static final Map<String, String> FUNCTION_MAPPINGS = new HashMap<>();
    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();
    
    static {
        // Date/Time function mappings
        FUNCTION_MAPPINGS.put("NOW()", "CURRENT_TIMESTAMP");
        FUNCTION_MAPPINGS.put("SYSDATE", "CURRENT_TIMESTAMP");
        FUNCTION_MAPPINGS.put("GETDATE()", "CURRENT_TIMESTAMP");
        FUNCTION_MAPPINGS.put("CURRENT_DATE()", "CURRENT_DATE");
        
        // Type mappings
        TYPE_MAPPINGS.put("BOOLEAN", "SMALLINT /* 0=false, 1=true */");
        TYPE_MAPPINGS.put("AUTO_INCREMENT", "GENERATED ALWAYS AS IDENTITY");
        TYPE_MAPPINGS.put("SERIAL", "INTEGER GENERATED ALWAYS AS IDENTITY");
    }
    
    /**
     * Rewrite SQL query to be database-agnostic
     * Handles multiple issues in a single pass
     * IMPORTANT: Order matters - apply simple replacements FIRST, then complex transformations
     */
    public String rewriteToAgnostic(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        
        String rewritten = sql.trim();
        
        // Step 1: Handle function replacements FIRST (case-insensitive)
        // This must happen before complex transformations to ensure functions are replaced
        rewritten = replaceFunctions(rewritten);
        
        // Step 2: Handle type replacements
        rewritten = replaceTypes(rewritten);
        
        // Step 3: Handle complex transformations (pagination, etc.)
        // These work on the already-cleaned SQL
        try {
            rewritten = handleComplexTransformations(rewritten);
        } catch (Exception e) {
            // If parsing fails, fall back to regex-based transformations
            rewritten = handleComplexTransformationsRegex(rewritten);
        }
        
        return rewritten;
    }
    
    /**
     * Replace database-specific functions with ANSI equivalents
     */
    private String replaceFunctions(String sql) {
        String result = sql;
        
        for (Map.Entry<String, String> entry : FUNCTION_MAPPINGS.entrySet()) {
            String key = entry.getKey();
            // For functions with parentheses, don't use word boundaries around the parens
            if (key.contains("(")) {
                // Match function name with word boundary, then the parentheses literally
                String funcName = key.substring(0, key.indexOf('('));
                String pattern = "(?i)\\b" + Pattern.quote(funcName) + "\\s*\\(\\s*\\)";
                result = result.replaceAll(pattern, entry.getValue());
            } else {
                // For non-function keywords, use word boundaries
                String pattern = "(?i)\\b" + Pattern.quote(key) + "\\b";
                result = result.replaceAll(pattern, entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * Replace database-specific types with ANSI equivalents
     */
    private String replaceTypes(String sql) {
        String result = sql;
        
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            String pattern = "(?i)\\b" + entry.getKey() + "\\b";
            result = result.replaceAll(pattern, entry.getValue());
        }
        
        return result;
    }
    
    /**
     * Handle complex transformations using JSqlParser
     * Falls back to regex if query has multiple conflicting pagination methods
     */
    private String handleComplexTransformations(String sql) throws JSQLParserException {
        // Check for conflicting pagination methods that JSqlParser can't handle properly
        boolean hasRownum = sql.matches("(?i).*ROWNUM.*");
        boolean hasLimit = sql.matches("(?i).*LIMIT.*");
        boolean hasFetch = sql.matches("(?i).*FETCH\\s+FIRST.*");
        
        // If multiple pagination methods exist, use regex-based handling
        int paginationCount = (hasRownum ? 1 : 0) + (hasLimit ? 1 : 0) + (hasFetch ? 1 : 0);
        if (paginationCount > 1) {
            // Multiple pagination methods - use regex fallback
            throw new JSQLParserException("Multiple pagination methods detected - using regex fallback");
        }
        
        Statement stmt = CCJSqlParserUtil.parse(sql);
        
        if (stmt instanceof Select) {
            Select select = (Select) stmt;
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            
            // Handle ROWNUM (Oracle)
            if (hasRownumCondition(plainSelect)) {
                return convertRownumToRowNumber(sql, plainSelect);
            }
            
            // Handle LIMIT/OFFSET (PostgreSQL/MySQL)
            if (plainSelect.getLimit() != null) {
                return convertLimitToRowNumber(sql, plainSelect);
            }
            
            // Handle FETCH FIRST (DB2)
            if (plainSelect.getFetch() != null) {
                return convertFetchToRowNumber(sql, plainSelect);
            }
        }
        
        return sql;
    }
    
    /**
     * Fallback regex-based transformations for complex cases
     * NOTE: This receives SQL that has already had function/type replacements applied
     */
    private String handleComplexTransformationsRegex(String sql) {
        String result = sql;
        
        // Handle ROWNUM with LIMIT/OFFSET combination (most complex case)
        if (result.matches("(?i).*ROWNUM.*LIMIT.*")) {
            result = handleRownumWithLimit(result);
        }
        // Handle ROWNUM alone
        else if (result.matches("(?i).*ROWNUM\\s*<=?\\s*\\d+.*")) {
            result = convertRownumToRowNumberRegex(result);
        }
        // Handle LIMIT/OFFSET
        else if (result.matches("(?i).*LIMIT\\s+\\d+.*")) {
            result = convertLimitToRowNumberRegex(result);
        }
        // Handle FETCH FIRST
        else if (result.matches("(?i).*FETCH\\s+FIRST.*")) {
            result = convertFetchToRowNumberRegex(result);
        }
        
        return result;
    }
    
    /**
     * Handle queries with both ROWNUM and LIMIT (conflicting pagination)
     * NOTE: Function replacements (NOW, SYSDATE, etc.) have already been applied
     */
    private String handleRownumWithLimit(String sql) {
        // Extract ROWNUM value
        Pattern rownumPattern = Pattern.compile("(?i)ROWNUM\\s*<=?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher rownumMatcher = rownumPattern.matcher(sql);
        
        // Extract LIMIT value
        Pattern limitPattern = Pattern.compile("(?i)LIMIT\\s+(\\d+)(?:\\s+OFFSET\\s+(\\d+))?", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        
        int rownumValue = 0;
        int limitValue = 0;
        int offsetValue = 0;
        
        if (rownumMatcher.find()) {
            rownumValue = Integer.parseInt(rownumMatcher.group(1));
        }
        
        if (limitMatcher.find()) {
            limitValue = Integer.parseInt(limitMatcher.group(1));
            if (limitMatcher.group(2) != null) {
                offsetValue = Integer.parseInt(limitMatcher.group(2));
            }
        }
        
        // Use the most restrictive limit (smallest value wins)
        int finalLimit = Math.min(rownumValue > 0 ? rownumValue : Integer.MAX_VALUE,
                                   limitValue > 0 ? limitValue : Integer.MAX_VALUE);
        
        // Remove ROWNUM condition from WHERE clause (preserve other conditions)
        String result = sql.replaceAll("(?i)\\s*AND\\s+ROWNUM\\s*<=?\\s*\\d+", "");
        result = result.replaceAll("(?i)WHERE\\s+ROWNUM\\s*<=?\\s*\\d+\\s*AND\\s*", "WHERE ");
        result = result.replaceAll("(?i)WHERE\\s+ROWNUM\\s*<=?\\s*\\d+", "");
        
        // Remove LIMIT clause
        result = result.replaceAll("(?i)\\s*LIMIT\\s+\\d+(?:\\s+OFFSET\\s+\\d+)?", "");
        
        // Calculate row range for ROW_NUMBER()
        int startRow = offsetValue + 1;
        int endRow = offsetValue + finalLimit;
        
        // Wrap in subquery with ROW_NUMBER()
        // Note: Functions like NOW() have already been replaced with CURRENT_TIMESTAMP
        result = String.format(
            "SELECT * FROM (\n" +
            "  SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn\n" +
            "  FROM (%s) inner_query\n" +
            ") WHERE rn BETWEEN %d AND %d",
            result.trim(), startRow, endRow
        );
        
        return result;
    }
    
    /**
     * Convert ROWNUM to ROW_NUMBER() using regex
     */
    private String convertRownumToRowNumberRegex(String sql) {
        Pattern pattern = Pattern.compile("(?i)ROWNUM\\s*<=?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(1));
            
            // Remove ROWNUM from WHERE clause
            String result = sql.replaceAll("(?i)\\s*AND\\s+ROWNUM\\s*<=?\\s*\\d+", "");
            result = result.replaceAll("(?i)WHERE\\s+ROWNUM\\s*<=?\\s*\\d+\\s*AND\\s*", "WHERE ");
            result = result.replaceAll("(?i)WHERE\\s+ROWNUM\\s*<=?\\s*\\d+", "");
            
            // Wrap with ROW_NUMBER()
            result = String.format(
                "SELECT * FROM (\n" +
                "  SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn\n" +
                "  FROM (%s) inner_query\n" +
                ") WHERE rn <= %d",
                result.trim(), limit
            );
            
            return result;
        }
        
        return sql;
    }
    
    /**
     * Convert LIMIT/OFFSET to ROW_NUMBER() using regex
     */
    private String convertLimitToRowNumberRegex(String sql) {
        Pattern pattern = Pattern.compile("(?i)LIMIT\\s+(\\d+)(?:\\s+OFFSET\\s+(\\d+))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(1));
            int offset = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            
            // Remove LIMIT clause
            String result = sql.replaceAll("(?i)\\s*LIMIT\\s+\\d+(?:\\s+OFFSET\\s+\\d+)?", "");
            
            int startRow = offset + 1;
            int endRow = offset + limit;
            
            // Wrap with ROW_NUMBER()
            result = String.format(
                "SELECT * FROM (\n" +
                "  SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn\n" +
                "  FROM (%s) inner_query\n" +
                ") WHERE rn BETWEEN %d AND %d",
                result.trim(), startRow, endRow
            );
            
            return result;
        }
        
        return sql;
    }
    
    /**
     * Convert FETCH FIRST to ROW_NUMBER() using regex
     */
    private String convertFetchToRowNumberRegex(String sql) {
        Pattern pattern = Pattern.compile("(?i)FETCH\\s+FIRST\\s+(\\d+)\\s+ROWS?\\s+ONLY", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(1));
            
            // Remove FETCH clause
            String result = sql.replaceAll("(?i)\\s*FETCH\\s+FIRST\\s+\\d+\\s+ROWS?\\s+ONLY", "");
            
            // Wrap with ROW_NUMBER()
            result = String.format(
                "SELECT * FROM (\n" +
                "  SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn\n" +
                "  FROM (%s) inner_query\n" +
                ") WHERE rn <= %d",
                result.trim(), limit
            );
            
            return result;
        }
        
        return sql;
    }
    
    // JSqlParser helper methods
    
    private boolean hasRownumCondition(PlainSelect select) {
        if (select.getWhere() == null) return false;
        return select.getWhere().toString().toUpperCase().contains("ROWNUM");
    }
    
    private String convertRownumToRowNumber(String sql, PlainSelect select) {
        // Use regex fallback for ROWNUM
        return convertRownumToRowNumberRegex(sql);
    }
    
    private String convertLimitToRowNumber(String sql, PlainSelect select) {
        // Use regex fallback for LIMIT
        return convertLimitToRowNumberRegex(sql);
    }
    
    private String convertFetchToRowNumber(String sql, PlainSelect select) {
        // Use regex fallback for FETCH
        return convertFetchToRowNumberRegex(sql);
    }
}

// Made with Bob
