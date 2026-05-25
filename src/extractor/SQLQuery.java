package com.ibm.aip.validator.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a SQL query extracted from Java source code
 *
 * @author Jaydeep Shah
 */
public class SQLQuery {
    private String fileName;
    private int lineNumber;
    private String rawQuery;
    private String normalizedQuery;
    private String queryType;
    private String constructionMethod = "Direct";
    private List<ValidationResult> validationResults = new ArrayList<>();
    private boolean hasSyntaxError = false;
    private List<String> syntaxErrors = new ArrayList<>();
    private Map<String, Object> runtimeResults = null;
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getRawQuery() {
        return rawQuery;
    }
    
    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }
    
    public String getNormalizedQuery() {
        return normalizedQuery;
    }
    
    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }
    
    public String getQueryType() {
        return queryType;
    }
    
    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }
    
    public String getConstructionMethod() {
        return constructionMethod;
    }
    
    public void setConstructionMethod(String constructionMethod) {
        this.constructionMethod = constructionMethod;
    }
    
    public List<ValidationResult> getValidationResults() {
        return validationResults;
    }
    
    public void addValidationResult(ValidationResult result) {
        this.validationResults.add(result);
    }
    
    public boolean isCompatibleWithAllDatabases() {
        for (ValidationResult result : validationResults) {
            if (!result.isValid()) {
                return false;
            }
        }
        return !validationResults.isEmpty();
    }
    
    public boolean hasAnyIssues() {
        // Check for syntax errors first
        if (hasSyntaxError) {
            return true;
        }
        
        for (ValidationResult result : validationResults) {
            if (!result.getIssues().isEmpty() || "WARN".equals(result.getStatus()) || "FAIL".equals(result.getStatus())) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasSyntaxError() {
        return hasSyntaxError;
    }
    
    public void setHasSyntaxError(boolean hasSyntaxError) {
        this.hasSyntaxError = hasSyntaxError;
    }
    
    public List<String> getSyntaxErrors() {
        return syntaxErrors;
    }
    
    public void setSyntaxErrors(List<String> syntaxErrors) {
        this.syntaxErrors = syntaxErrors;
        this.hasSyntaxError = !syntaxErrors.isEmpty();
    }
    
    public void addSyntaxError(String error) {
        this.syntaxErrors.add(error);
        this.hasSyntaxError = true;
    }
    
    public int getCompatibilityScore() {
        if (validationResults.isEmpty()) return 0;
        
        int validCount = 0;
        for (ValidationResult result : validationResults) {
            if (result.isValid()) validCount++;
        }
        return (validCount * 100) / validationResults.size();
    }
    
    public Map<String, Object> getRuntimeResults() {
        return runtimeResults;
    }
    
    public void setRuntimeResults(Map<String, ?> runtimeResults) {
        this.runtimeResults = (Map<String, Object>) runtimeResults;
    }
    
    @Override
    public String toString() {
        return String.format("SQLQuery[file=%s, line=%d, type=%s, query=%s]",
            fileName, lineNumber, queryType, 
            normalizedQuery.length() > 50 ? normalizedQuery.substring(0, 50) + "..." : normalizedQuery);
    }
}

// Made with Bob
