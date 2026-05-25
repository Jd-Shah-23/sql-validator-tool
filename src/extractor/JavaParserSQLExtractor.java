package com.ibm.aip.validator.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Enhanced SQL extractor using JavaParser library for accurate AST-based extraction.
 * Handles multi-line string concatenations, StringBuilder patterns, and complex expressions.
 */
public class JavaParserSQLExtractor {
    
    private static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile(
        "^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE|MERGE|WITH)\\s+",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Extract SQL queries from a Java file using JavaParser AST analysis
     */
    public static List<SQLQuery> extractFromFile(File file) {
        List<SQLQuery> queries = new ArrayList<>();
        
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser javaParser = new JavaParser();
            CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + file.getName());
                return queries;
            }
            
            // Visit all string expressions in the AST
            cu.accept(new SQLStringVisitor(queries, file.getName()), null);
            
        } catch (Exception e) {
            System.err.println("Error parsing " + file.getName() + ": " + e.getMessage());
        }
        
        return queries;
    }
    
    /**
     * Visitor that traverses the AST and extracts SQL strings
     */
    private static class SQLStringVisitor extends VoidVisitorAdapter<Void> {
        private final List<SQLQuery> queries;
        private final String fileName;
        
        public SQLStringVisitor(List<SQLQuery> queries, String fileName) {
            this.queries = queries;
            this.fileName = fileName;
        }
        
        @Override
        public void visit(VariableDeclarator var, Void arg) {
            super.visit(var, arg);
            
            if (var.getInitializer().isPresent()) {
                Expression init = var.getInitializer().get();
                String sql = extractSQLFromExpression(init);
                
                if (sql != null && !sql.trim().isEmpty() && looksLikeSQL(sql)) {
                    int line = var.getBegin().map(pos -> pos.line).orElse(0);
                    SQLQuery query = new SQLQuery();
                    query.setRawQuery(sql.trim());
                    query.setNormalizedQuery(sql.trim());
                    query.setFileName(fileName);
                    query.setLineNumber(line);
                    query.setConstructionMethod("JavaParser-Variable");
                    queries.add(query);
                }
            }
        }
        
        @Override
        public void visit(AssignExpr assign, Void arg) {
            super.visit(assign, arg);
            
            String sql = extractSQLFromExpression(assign.getValue());
            
            if (sql != null && !sql.trim().isEmpty() && looksLikeSQL(sql)) {
                int line = assign.getBegin().map(pos -> pos.line).orElse(0);
                SQLQuery query = new SQLQuery();
                query.setRawQuery(sql.trim());
                query.setNormalizedQuery(sql.trim());
                query.setFileName(fileName);
                query.setLineNumber(line);
                query.setConstructionMethod("JavaParser-Assignment");
                queries.add(query);
            }
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check for executeQuery, executeUpdate, prepareStatement, etc.
            String methodName = methodCall.getNameAsString();
            if (methodName.equals("executeQuery") || 
                methodName.equals("executeUpdate") ||
                methodName.equals("prepareStatement") ||
                methodName.equals("createStatement")) {
                
                // Check arguments for SQL strings
                for (Expression argExpr : methodCall.getArguments()) {
                    String sql = extractSQLFromExpression(argExpr);
                    if (sql != null && !sql.trim().isEmpty() && looksLikeSQL(sql)) {
                        int line = methodCall.getBegin().map(pos -> pos.line).orElse(0);
                        SQLQuery query = new SQLQuery();
                        query.setRawQuery(sql.trim());
                        query.setNormalizedQuery(sql.trim());
                        query.setFileName(fileName);
                        query.setLineNumber(line);
                        query.setConstructionMethod("JavaParser-MethodCall");
                        queries.add(query);
                    }
                }
            }
        }
    }
    
    /**
     * Extract SQL string from various expression types
     */
    private static String extractSQLFromExpression(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expr).getValue();
        }
        else if (expr instanceof BinaryExpr) {
            // Handle string concatenation with + operator
            return extractFromBinaryExpr((BinaryExpr) expr);
        }
        else if (expr instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) expr;
            
            // Handle StringBuilder.toString() or StringBuffer.toString()
            if (methodCall.getNameAsString().equals("toString")) {
                return extractFromStringBuilder(methodCall);
            }
            
            // Handle String.format()
            if (methodCall.getNameAsString().equals("format") && 
                methodCall.getScope().isPresent() &&
                methodCall.getScope().get().toString().equals("String")) {
                
                if (!methodCall.getArguments().isEmpty()) {
                    return extractSQLFromExpression(methodCall.getArguments().get(0));
                }
            }
        }
        else if (expr instanceof NameExpr) {
            // Variable reference - we can't resolve without full context
            // Return null to skip
            return null;
        }
        
        return null;
    }
    
    /**
     * Extract SQL from binary expression (string concatenation with +)
     */
    private static String extractFromBinaryExpr(BinaryExpr expr) {
        if (expr.getOperator() != BinaryExpr.Operator.PLUS) {
            return null;
        }
        
        StringBuilder result = new StringBuilder();
        collectConcatenatedStrings(expr, result);
        return result.toString();
    }
    
    /**
     * Recursively collect concatenated strings
     */
    private static void collectConcatenatedStrings(Expression expr, StringBuilder result) {
        if (expr instanceof BinaryExpr) {
            BinaryExpr binExpr = (BinaryExpr) expr;
            if (binExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                collectConcatenatedStrings(binExpr.getLeft(), result);
                collectConcatenatedStrings(binExpr.getRight(), result);
                return;
            }
        }
        
        if (expr instanceof StringLiteralExpr) {
            result.append(((StringLiteralExpr) expr).getValue());
        }
        else if (expr instanceof MethodCallExpr) {
            // Handle method calls that might return strings
            MethodCallExpr methodCall = (MethodCallExpr) expr;
            if (methodCall.getNameAsString().equals("toString")) {
                String sql = extractFromStringBuilder(methodCall);
                if (sql != null) {
                    result.append(sql);
                }
            } else {
                // For method calls that return strings, use a placeholder
                result.append("?");
            }
        }
        else if (expr instanceof NameExpr) {
            // For variable references, use a placeholder
            // This handles cases like: "VALUES (" + getSequence + ", ?)"
            result.append("?");
        }
        // For other expressions, skip them
    }
    
    /**
     * Extract SQL from StringBuilder/StringBuffer pattern
     */
    private static String extractFromStringBuilder(MethodCallExpr toStringCall) {
        // Try to find the StringBuilder variable and trace back all append() calls
        if (!toStringCall.getScope().isPresent()) {
            return null;
        }
        
        Expression scope = toStringCall.getScope().get();
        
        // If scope is a name (variable), we need to trace it back
        // For now, we'll handle simple cases where append() calls are chained
        if (scope instanceof MethodCallExpr) {
            return extractFromAppendChain((MethodCallExpr) scope);
        }
        
        return null;
    }
    
    /**
     * Extract SQL from chained append() calls
     */
    private static String extractFromAppendChain(MethodCallExpr methodCall) {
        StringBuilder result = new StringBuilder();
        collectAppendedStrings(methodCall, result);
        return result.toString();
    }
    
    /**
     * Recursively collect strings from append() chain
     */
    private static void collectAppendedStrings(MethodCallExpr methodCall, StringBuilder result) {
        if (!methodCall.getNameAsString().equals("append")) {
            return;
        }
        
        // Process the scope (previous append in chain)
        if (methodCall.getScope().isPresent() && 
            methodCall.getScope().get() instanceof MethodCallExpr) {
            collectAppendedStrings((MethodCallExpr) methodCall.getScope().get(), result);
        }
        
        // Add current append argument
        if (!methodCall.getArguments().isEmpty()) {
            Expression arg = methodCall.getArguments().get(0);
            if (arg instanceof StringLiteralExpr) {
                result.append(((StringLiteralExpr) arg).getValue());
            }
            else if (arg instanceof BinaryExpr) {
                String concatenated = extractFromBinaryExpr((BinaryExpr) arg);
                if (concatenated != null) {
                    result.append(concatenated);
                }
            }
        }
    }
    
    /**
     * Check if a string looks like SQL
     */
    private static boolean looksLikeSQL(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = str.trim();
        
        // Must start with SQL keyword
        if (!SQL_KEYWORD_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        
        // Should not be too short
        if (trimmed.length() < 10) {
            return false;
        }
        
        // Should contain typical SQL elements
        String upper = trimmed.toUpperCase();
        return upper.contains("FROM") || 
               upper.contains("INTO") || 
               upper.contains("SET") ||
               upper.contains("VALUES") ||
               upper.contains("WHERE") ||
               upper.contains("TABLE");
    }
    
    /**
     * Extract SQL queries from all Java files in a directory
     */
    public static List<SQLQuery> extractFromDirectory(File directory, boolean recursive) {
        List<SQLQuery> allQueries = new ArrayList<>();
        
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory.getPath());
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return allQueries;
        }
        
        for (File file : files) {
            if (file.isDirectory() && recursive) {
                allQueries.addAll(extractFromDirectory(file, recursive));
            }
            else if (file.isFile() && file.getName().endsWith(".java")) {
                allQueries.addAll(extractFromFile(file));
            }
        }
        
        return allQueries;
    }
}

// Made with Bob
