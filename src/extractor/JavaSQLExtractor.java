package com.ibm.aip.validator.extractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts SQL queries from Java source files.
 * Handles string concatenation and StringBuilder patterns.
 *
 * @author Jaydeep Shah
 */
public class JavaSQLExtractor {
    
    // SQL keyword patterns
    private static final Pattern SQL_PATTERN = Pattern.compile(
        "(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|MERGE)\\s+.*?(?:FROM|INTO|TABLE|SET|WHERE|VALUES)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // String literal patterns
    private static final Pattern STRING_LITERAL = Pattern.compile(
        "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"",
        Pattern.DOTALL
    );
    
    // StringBuilder append pattern
    private static final Pattern STRINGBUILDER_APPEND = Pattern.compile(
        "\\.append\\s*\\(\\s*\"([^\"]+)\"\\s*\\)",
        Pattern.DOTALL
    );
    
    /**
     * Extract all SQL queries from a Java file
     */
    public List<SQLQuery> extractFromFile(File javaFile) throws IOException {
        List<SQLQuery> queries = new ArrayList<>();
        
        StringBuilder fileContent = new StringBuilder();
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(javaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                fileContent.append(line).append("\n");
            }
        }
        
        String content = fileContent.toString();
        
        // Extract direct string literals containing SQL
        queries.addAll(extractDirectSQL(content, javaFile.getName()));
        
        // Extract StringBuilder concatenated SQL
        queries.addAll(extractStringBuilderSQL(content, javaFile.getName()));
        
        return queries;
    }
    
    /**
     * Extract SQL from direct string literals
     * Skip fragments that are part of StringBuilder construction
     */
    private List<SQLQuery> extractDirectSQL(String content, String fileName) {
        List<SQLQuery> queries = new ArrayList<>();
        
        Matcher stringMatcher = STRING_LITERAL.matcher(content);
        while (stringMatcher.find()) {
            String stringContent = stringMatcher.group(1);
            
            // Check if this string contains SQL keywords
            Matcher sqlMatcher = SQL_PATTERN.matcher(stringContent);
            if (sqlMatcher.find()) {
                int lineNumber = getLineNumber(content, stringMatcher.start());
                
                // Skip if this is part of a StringBuilder.append() call
                int matchStart = stringMatcher.start();
                int lineStart = content.lastIndexOf('\n', matchStart) + 1;
                String lineContent = content.substring(lineStart, Math.min(matchStart + 100, content.length()));
                
                if (lineContent.contains(".append(")) {
                    // This is part of StringBuilder, skip it
                    continue;
                }
                
                SQLQuery query = new SQLQuery();
                query.setFileName(fileName);
                query.setLineNumber(lineNumber);
                query.setRawQuery(stringContent);
                query.setNormalizedQuery(normalizeQuery(stringContent));
                query.setQueryType(detectQueryType(stringContent));
                
                queries.add(query);
            }
        }
        
        return queries;
    }
    
    /**
     * Extract SQL from StringBuilder patterns
     * Enhanced to look for .toString() usage to get complete SQL
     */
    private List<SQLQuery> extractStringBuilderSQL(String content, String fileName) {
        List<SQLQuery> queries = new ArrayList<>();
        
        // Find StringBuilder variable declarations
        Pattern sbPattern = Pattern.compile(
            "StringBuilder\\s+(\\w+)\\s*=\\s*new\\s+StringBuilder\\s*\\(\\s*\\);",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher sbMatcher = sbPattern.matcher(content);
        while (sbMatcher.find()) {
            String varName = sbMatcher.group(1);
            int startPos = sbMatcher.end();
            int lineNumber = getLineNumber(content, sbMatcher.start());
            
            // Look for where this StringBuilder is converted to String
            // Patterns: varName.toString(), SqlFormat(varName.toString()), prepareStatement(varName.toString())
            int searchEnd = Math.min(startPos + 10000, content.length());
            String searchRegion = content.substring(startPos, searchEnd);
            
            // Find the .toString() call
            Pattern toStringPattern = Pattern.compile(
                varName + "\\.toString\\s*\\(\\s*\\)",
                Pattern.CASE_INSENSITIVE
            );
            
            Matcher toStringMatcher = toStringPattern.matcher(searchRegion);
            if (toStringMatcher.find()) {
                // Extract all append calls up to the toString() call
                String buildRegion = searchRegion.substring(0, toStringMatcher.start());
                
                StringBuilder sqlBuilder = new StringBuilder();
                Pattern appendPattern = Pattern.compile(
                    varName + "\\.append\\s*\\(\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"\\s*\\)",
                    Pattern.DOTALL
                );
                
                Matcher appendMatcher = appendPattern.matcher(buildRegion);
                boolean foundAppends = false;
                
                while (appendMatcher.find()) {
                    String appendContent = appendMatcher.group(1);
                    // Unescape the string content
                    appendContent = appendContent.replace("\\\"", "\"")
                                               .replace("\\n", " ")
                                               .replace("\\t", " ");
                    sqlBuilder.append(appendContent).append(" ");
                    foundAppends = true;
                }
                
                // Also look for .append() with variables or expressions
                Pattern appendVarPattern = Pattern.compile(
                    varName + "\\.append\\s*\\(\\s*([^)]+)\\s*\\)",
                    Pattern.DOTALL
                );
                Matcher appendVarMatcher = appendVarPattern.matcher(buildRegion);
                while (appendVarMatcher.find()) {
                    String appendExpr = appendVarMatcher.group(1).trim();
                    // If it's not a string literal, try to evaluate simple expressions
                    if (!appendExpr.startsWith("\"")) {
                        // For simple variable references or numbers, add placeholder
                        if (appendExpr.matches("\\w+") || appendExpr.matches("\\d+")) {
                            sqlBuilder.append(" ? ");
                        }
                    }
                }
                
                if (foundAppends) {
                    String sql = sqlBuilder.toString().trim();
                    // Clean up extra spaces
                    sql = sql.replaceAll("\\s+", " ");
                    
                    // Only add if it looks like SQL
                    if (!sql.isEmpty() && SQL_PATTERN.matcher(sql).find()) {
                        SQLQuery query = new SQLQuery();
                        query.setFileName(fileName);
                        query.setLineNumber(lineNumber);
                        query.setRawQuery(sql);
                        query.setNormalizedQuery(normalizeQuery(sql));
                        query.setQueryType(detectQueryType(sql));
                        query.setConstructionMethod("StringBuilder");
                        
                        queries.add(query);
                    }
                }
            }
        }
        
        return queries;
    }
    
    /**
     * Check if a SQL query seems syntactically complete
     */
    private boolean isQueryComplete(String sql) {
        String upper = sql.toUpperCase().trim();
        
        // Check for balanced parentheses
        int openParens = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') openParens++;
            if (c == ')') openParens--;
        }
        if (openParens != 0) return false;
        
        // Check for common complete patterns
        if (upper.startsWith("SELECT")) {
            return upper.contains("FROM");
        }
        if (upper.startsWith("INSERT")) {
            return upper.contains("INTO") && (upper.contains("VALUES") || upper.contains("SELECT"));
        }
        if (upper.startsWith("UPDATE")) {
            return upper.contains("SET");
        }
        if (upper.startsWith("DELETE")) {
            return upper.contains("FROM");
        }
        
        return true; // For other types, assume complete
    }
    
    /**
     * Normalize SQL query for comparison
     */
    private String normalizeQuery(String sql) {
        return sql.replaceAll("\\s+", " ")
                  .replaceAll("\\?", "?")
                  .trim();
    }
    
    /**
     * Detect SQL query type
     */
    private String detectQueryType(String sql) {
        String upperSQL = sql.toUpperCase().trim();
        if (upperSQL.startsWith("SELECT")) return "SELECT";
        if (upperSQL.startsWith("INSERT")) return "INSERT";
        if (upperSQL.startsWith("UPDATE")) return "UPDATE";
        if (upperSQL.startsWith("DELETE")) return "DELETE";
        if (upperSQL.startsWith("CREATE")) return "CREATE";
        if (upperSQL.startsWith("ALTER")) return "ALTER";
        if (upperSQL.startsWith("DROP")) return "DROP";
        if (upperSQL.startsWith("MERGE")) return "MERGE";
        return "UNKNOWN";
    }
    
    /**
     * Get line number from position in content
     */
    private int getLineNumber(String content, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
    
    /**
     * Extract queries from multiple files in a directory
     */
    public List<SQLQuery> extractFromDirectory(File directory, boolean recursive) throws IOException {
        List<SQLQuery> allQueries = new ArrayList<>();
        
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && recursive) {
                    allQueries.addAll(extractFromDirectory(file, recursive));
                } else if (file.isFile() && file.getName().endsWith(".java")) {
                    allQueries.addAll(extractFromFile(file));
                }
            }
        }
        
        return allQueries;
    }
}

// Made with Bob
