package validator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates SQL syntax without requiring database connection
 * Uses JSqlParser to detect syntax errors before runtime
 */
public class SyntaxValidator {
    
    /**
     * Validate SQL syntax and return detailed error information
     */
    public SyntaxValidationResult validateSyntax(String sql) {
        SyntaxValidationResult result = new SyntaxValidationResult();
        result.setOriginalSQL(sql);
        
        // Quick checks for common syntax errors
        List<String> quickErrors = performQuickChecks(sql);
        if (!quickErrors.isEmpty()) {
            result.setValid(false);
            result.setErrors(quickErrors);
            return result;
        }
        
        // Use JSqlParser for comprehensive syntax validation
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            result.setValid(true);
            result.setParsedStatement(stmt);
        } catch (JSQLParserException e) {
            result.setValid(false);
            result.addError("SQL Syntax Error: " + extractErrorMessage(e));
            result.setParseException(e);
        }
        
        return result;
    }
    
    /**
     * Perform quick regex-based checks for common syntax errors
     */
    private List<String> performQuickChecks(String sql) {
        List<String> errors = new ArrayList<>();
        
        // Check for empty or whitespace-only query
        if (sql == null || sql.trim().isEmpty()) {
            errors.add("Empty SQL query");
            return errors;
        }
        
        String trimmed = sql.trim();
        
        // Check for unclosed quotes
        if (countOccurrences(trimmed, "'") % 2 != 0) {
            errors.add("Unclosed single quote (')");
        }
        if (countOccurrences(trimmed, "\"") % 2 != 0) {
            errors.add("Unclosed double quote (\")");
        }
        
        // Check for unbalanced parentheses
        int openParen = countOccurrences(trimmed, "(");
        int closeParen = countOccurrences(trimmed, ")");
        if (openParen != closeParen) {
            errors.add(String.format("Unbalanced parentheses: %d open, %d close", openParen, closeParen));
        }
        
        // Check for common typos in keywords
        if (Pattern.compile("\\bFORM\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            errors.add("Possible typo: 'FORM' should be 'FROM'");
        }
        if (Pattern.compile("\\bSELCT\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            errors.add("Possible typo: 'SELCT' should be 'SELECT'");
        }
        if (Pattern.compile("\\bWHERE\\s+FROM\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            errors.add("Invalid clause order: WHERE before FROM");
        }
        
        // Check for missing SELECT/INSERT/UPDATE/DELETE
        if (!Pattern.compile("^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|WITH)", 
                Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            errors.add("Query must start with a valid SQL keyword (SELECT, INSERT, UPDATE, etc.)");
        }
        
        // Check for multiple semicolons (potential SQL injection or multiple statements)
        if (countOccurrences(trimmed, ";") > 1) {
            errors.add("Multiple statements detected (multiple semicolons)");
        }
        
        return errors;
    }
    
    /**
     * Count occurrences of a substring
     */
    private int countOccurrences(String str, String substring) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
    
    /**
     * Extract meaningful error message from JSQLParserException
     */
    private String extractErrorMessage(JSQLParserException e) {
        String message = e.getMessage();
        
        // Clean up the error message
        if (message != null) {
            // Remove stack trace info
            int newlineIndex = message.indexOf('\n');
            if (newlineIndex > 0) {
                message = message.substring(0, newlineIndex);
            }
            
            // Extract line and column if available
            if (message.contains("line")) {
                return message;
            }
            
            // Provide more user-friendly message
            if (message.contains("Encountered unexpected token")) {
                return "Unexpected token found - check for missing commas, keywords, or typos";
            }
            if (message.contains("Encountered \"<EOF>\"")) {
                return "Incomplete query - missing closing parenthesis, quote, or keyword";
            }
        }
        
        return message != null ? message : "Unknown syntax error";
    }
    
    /**
     * Result class for syntax validation
     */
    public static class SyntaxValidationResult {
        private boolean valid;
        private String originalSQL;
        private List<String> errors;
        private Statement parsedStatement;
        private JSQLParserException parseException;
        
        public SyntaxValidationResult() {
            this.errors = new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getOriginalSQL() {
            return originalSQL;
        }
        
        public void setOriginalSQL(String originalSQL) {
            this.originalSQL = originalSQL;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
        
        public void addError(String error) {
            this.errors.add(error);
        }
        
        public Statement getParsedStatement() {
            return parsedStatement;
        }
        
        public void setParsedStatement(Statement parsedStatement) {
            this.parsedStatement = parsedStatement;
        }
        
        public JSQLParserException getParseException() {
            return parseException;
        }
        
        public void setParseException(JSQLParserException parseException) {
            this.parseException = parseException;
        }
        
        public String getErrorSummary() {
            if (valid) {
                return "No syntax errors";
            }
            return String.join("; ", errors);
        }
    }
}

// Made with Bob
