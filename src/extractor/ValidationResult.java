package com.ibm.aip.validator.extractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents validation result for a specific database
 *
 * @author Jaydeep Shah
 */
public class ValidationResult {
    private String databaseType; // DB2, PostgreSQL, Oracle
    private boolean valid;
    private String status; // PASS, WARN, FAIL
    private List<String> issues = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private String errorMessage;
    
    public ValidationResult(String databaseType) {
        this.databaseType = databaseType;
        this.valid = true;
        this.status = "PASS";
    }
    
    // Getters and Setters
    public String getDatabaseType() {
        return databaseType;
    }
    
    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
        if (!valid && "PASS".equals(status)) {
            this.status = "FAIL";
        }
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        if ("FAIL".equals(status)) {
            this.valid = false;
        }
    }
    
    public List<String> getIssues() {
        return issues;
    }
    
    public void addIssue(String issue) {
        this.issues.add(issue);
        if (this.valid) {
            this.status = "WARN";
        }
    }
    
    public List<String> getSuggestions() {
        return suggestions;
    }
    
    public void addSuggestion(String suggestion) {
        this.suggestions.add(suggestion);
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.valid = false;
        this.status = "FAIL";
    }
    
    public String getStatusIcon() {
        switch (status) {
            case "PASS": return "✅";
            case "WARN": return "⚠️";
            case "FAIL": return "❌";
            default: return "❓";
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s: %s", getStatusIcon(), databaseType, status));
        if (!issues.isEmpty()) {
            sb.append("\n  Issues: ").append(String.join(", ", issues));
        }
        if (!suggestions.isEmpty()) {
            sb.append("\n  Suggestions: ").append(String.join(", ", suggestions));
        }
        if (errorMessage != null) {
            sb.append("\n  Error: ").append(errorMessage);
        }
        return sb.toString();
    }
}

// Made with Bob
