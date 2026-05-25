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
 * Extracts SQL queries from Java source files
 * Handles various SQL patterns including string concatenation and StringBuilder
 * 
 * @author AIP Innovation Team
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
            
            // Extract all append calls for this StringBuilder
            StringBuilder sqlBuilder = new StringBuilder();
            Pattern appendPattern = Pattern.compile(
                varName + "\\.append\\s*\\(\\s*\"([^\"]+)\"\\s*\\);",
                Pattern.DOTALL
            );
            
            Matcher appendMatcher = appendPattern.matcher(content.substring(startPos));
            int lineNumber = getLineNumber(content, startPos);
            
            while (appendMatcher.find()) {
                sqlBuilder.append(appendMatcher.group(1)).append(" ");
            }
            
            String sql = sqlBuilder.toString().trim();
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
        
        return queries;
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
