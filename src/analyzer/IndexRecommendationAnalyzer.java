package com.ibm.aip.validator.analyzer;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.*;

/**
 * Analyzes SQL queries and recommends indexes for performance optimization.
 * Extracts columns from WHERE, JOIN, and ORDER BY clauses using JSqlParser.
 *
 * @author Jaydeep Shah
 */
public class IndexRecommendationAnalyzer {
    
    /**
     * Analyzes query and generates index recommendations
     */
    public IndexRecommendation analyzeQuery(String sql) {
        IndexRecommendation recommendation = new IndexRecommendation();
        
        if (sql == null || sql.trim().isEmpty()) {
            return recommendation;
        }
        
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) selectStatement.getSelectBody();
                
                // Extract table name
                if (plainSelect.getFromItem() instanceof Table) {
                    Table table = (Table) plainSelect.getFromItem();
                    recommendation.setTableName(table.getName());
                }
                
                // Analyze WHERE clause
                if (plainSelect.getWhere() != null) {
                    analyzeWhereClause(plainSelect.getWhere(), recommendation);
                }
                
                // Analyze JOIN conditions
                if (plainSelect.getJoins() != null) {
                    for (Join join : plainSelect.getJoins()) {
                        if (join.getOnExpression() != null) {
                            analyzeJoinCondition(join, recommendation);
                        }
                    }
                }
                
                // Analyze ORDER BY clause
                if (plainSelect.getOrderByElements() != null) {
                    analyzeOrderBy(plainSelect.getOrderByElements(), recommendation);
                }
                
                // Generate recommendations
                generateRecommendations(recommendation);
            }
            
        } catch (JSQLParserException e) {
            recommendation.setError("Could not parse query for index analysis: " + e.getMessage());
        }
        
        return recommendation;
    }
    
    /**
     * Analyze WHERE clause to find filterable columns
     */
    private void analyzeWhereClause(Expression where, IndexRecommendation recommendation) {
        if (where instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) where;
            
            // Check left side
            if (binary.getLeftExpression() instanceof Column) {
                Column col = (Column) binary.getLeftExpression();
                recommendation.addWhereColumn(col.getColumnName());
            }
            
            // Check right side
            if (binary.getRightExpression() instanceof Column) {
                Column col = (Column) binary.getRightExpression();
                recommendation.addWhereColumn(col.getColumnName());
            }
            
            // Recursively analyze AND/OR expressions
            if (binary instanceof AndExpression || binary instanceof OrExpression) {
                analyzeWhereClause(binary.getLeftExpression(), recommendation);
                analyzeWhereClause(binary.getRightExpression(), recommendation);
            }
        } else if (where instanceof InExpression) {
            InExpression in = (InExpression) where;
            if (in.getLeftExpression() instanceof Column) {
                Column col = (Column) in.getLeftExpression();
                recommendation.addWhereColumn(col.getColumnName());
            }
        } else if (where instanceof Between) {
            Between between = (Between) where;
            if (between.getLeftExpression() instanceof Column) {
                Column col = (Column) between.getLeftExpression();
                recommendation.addWhereColumn(col.getColumnName());
            }
        }
    }
    
    /**
     * Analyze JOIN conditions to find join columns
     */
    private void analyzeJoinCondition(Join join, IndexRecommendation recommendation) {
        Expression onExpr = join.getOnExpression();
        
        if (onExpr instanceof EqualsTo) {
            EqualsTo equals = (EqualsTo) onExpr;
            
            if (equals.getLeftExpression() instanceof Column) {
                Column col = (Column) equals.getLeftExpression();
                recommendation.addJoinColumn(col.getColumnName());
            }
            
            if (equals.getRightExpression() instanceof Column) {
                Column col = (Column) equals.getRightExpression();
                recommendation.addJoinColumn(col.getColumnName());
            }
        }
        
        // Get join table name
        if (join.getRightItem() instanceof Table) {
            Table table = (Table) join.getRightItem();
            recommendation.addJoinTable(table.getName());
        }
    }
    
    /**
     * Analyze ORDER BY clause to find sortable columns
     */
    private void analyzeOrderBy(List<OrderByElement> orderByElements, IndexRecommendation recommendation) {
        for (OrderByElement element : orderByElements) {
            if (element.getExpression() instanceof Column) {
                Column col = (Column) element.getExpression();
                recommendation.addOrderByColumn(col.getColumnName());
            }
        }
    }
    
    /**
     * Generate index recommendations based on analysis
     */
    private void generateRecommendations(IndexRecommendation recommendation) {
        List<String> whereColumns = recommendation.getWhereColumns();
        List<String> joinColumns = recommendation.getJoinColumns();
        List<String> orderByColumns = recommendation.getOrderByColumns();
        
        // Priority 1: Composite index for WHERE + ORDER BY
        if (!whereColumns.isEmpty() && !orderByColumns.isEmpty()) {
            List<String> compositeColumns = new ArrayList<>(whereColumns);
            compositeColumns.addAll(orderByColumns);
            recommendation.addRecommendation(
                "HIGH",
                "Composite Index",
                compositeColumns,
                "Covers both filtering (WHERE) and sorting (ORDER BY) operations"
            );
        }
        
        // Priority 2: Index for JOIN columns
        if (!joinColumns.isEmpty()) {
            for (String col : joinColumns) {
                recommendation.addRecommendation(
                    "HIGH",
                    "Join Index",
                    Arrays.asList(col),
                    "Improves JOIN performance significantly"
                );
            }
        }
        
        // Priority 3: Individual indexes for WHERE columns
        if (!whereColumns.isEmpty() && orderByColumns.isEmpty()) {
            for (String col : whereColumns) {
                recommendation.addRecommendation(
                    "MEDIUM",
                    "Filter Index",
                    Arrays.asList(col),
                    "Speeds up WHERE clause filtering"
                );
            }
        }
        
        // Priority 4: Index for ORDER BY only
        if (whereColumns.isEmpty() && !orderByColumns.isEmpty()) {
            recommendation.addRecommendation(
                "MEDIUM",
                "Sort Index",
                orderByColumns,
                "Improves ORDER BY performance"
            );
        }
    }
    
    /**
     * Index recommendation result
     */
    public static class IndexRecommendation {
        private String tableName;
        private List<String> whereColumns = new ArrayList<>();
        private List<String> joinColumns = new ArrayList<>();
        private List<String> orderByColumns = new ArrayList<>();
        private List<String> joinTables = new ArrayList<>();
        private List<Recommendation> recommendations = new ArrayList<>();
        private String error;
        
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public void addWhereColumn(String column) {
            if (!whereColumns.contains(column)) {
                whereColumns.add(column);
            }
        }
        
        public void addJoinColumn(String column) {
            if (!joinColumns.contains(column)) {
                joinColumns.add(column);
            }
        }
        
        public void addOrderByColumn(String column) {
            if (!orderByColumns.contains(column)) {
                orderByColumns.add(column);
            }
        }
        
        public void addJoinTable(String table) {
            if (!joinTables.contains(table)) {
                joinTables.add(table);
            }
        }
        
        public List<String> getWhereColumns() {
            return whereColumns;
        }
        
        public List<String> getJoinColumns() {
            return joinColumns;
        }
        
        public List<String> getOrderByColumns() {
            return orderByColumns;
        }
        
        public void addRecommendation(String priority, String type, List<String> columns, String reason) {
            recommendations.add(new Recommendation(priority, type, columns, reason));
        }
        
        public List<Recommendation> getRecommendations() {
            return recommendations;
        }
        
        public boolean hasRecommendations() {
            return !recommendations.isEmpty();
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
        
        public boolean hasError() {
            return error != null;
        }
    }
    
    /**
     * Individual index recommendation
     */
    public static class Recommendation {
        private String priority;
        private String type;
        private List<String> columns;
        private String reason;
        
        public Recommendation(String priority, String type, List<String> columns, String reason) {
            this.priority = priority;
            this.type = type;
            this.columns = columns;
            this.reason = reason;
        }
        
        public String getPriority() {
            return priority;
        }
        
        public String getType() {
            return type;
        }
        
        public List<String> getColumns() {
            return columns;
        }
        
        public String getColumnsAsString() {
            return String.join(", ", columns);
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getPriorityIcon() {
            switch (priority) {
                case "HIGH": return "🔴";
                case "MEDIUM": return "🟡";
                case "LOW": return "🟢";
                default: return "⚪";
            }
        }
    }
}

// Made with Bob