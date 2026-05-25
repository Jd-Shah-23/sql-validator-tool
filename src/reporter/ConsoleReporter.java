package com.ibm.aip.validator.reporter;

import com.ibm.aip.validator.extractor.SQLQuery;
import com.ibm.aip.validator.extractor.ValidationResult;
import com.ibm.aip.validator.validator.MultiDatabaseValidator;
import com.ibm.aip.validator.validator.RuntimeValidator.RuntimeResult;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Console reporter for SQL validation results
 * 
 * @author AIP Innovation Team
 */
public class ConsoleReporter {
    
    private static final String SEPARATOR = "─".repeat(65);
    private static final String DOUBLE_SEPARATOR = "═".repeat(65);
    private MultiDatabaseValidator validator;
    
    /**
     * Constructor
     */
    public ConsoleReporter(MultiDatabaseValidator validator) {
        this.validator = validator;
    }
    
    /**
     * Generate and print console report
     */
    public void generateReport(List<SQLQuery> queries) {
        printHeader();
        
        int totalQueries = queries.size();
        int fullyCompatible = 0;
        int warnings = 0;
        int incompatible = 0;
        
        Map<String, Integer> issueCount = new HashMap<>();
        
        for (SQLQuery query : queries) {
            Map<String, RuntimeResult> runtimeResults = null;
            if (query.getRuntimeResults() != null) {
                runtimeResults = (Map<String, RuntimeResult>) query.getRuntimeResults();
            }
            printQueryReport(query, runtimeResults);
            
            if (query.isCompatibleWithAllDatabases()) {
                fullyCompatible++;
            } else {
                boolean hasFailure = false;
                for (ValidationResult result : query.getValidationResults()) {
                    if ("FAIL".equals(result.getStatus())) {
                        hasFailure = true;
                        incompatible++;
                        break;
                    } else if ("WARN".equals(result.getStatus())) {
                        warnings++;
                    }
                    
                    // Count issues
                    for (String issue : result.getIssues()) {
                        issueCount.put(issue, issueCount.getOrDefault(issue, 0) + 1);
                    }
                }
                if (!hasFailure && warnings > 0) {
                    warnings++;
                }
            }
        }
        
        printSummary(totalQueries, fullyCompatible, warnings, incompatible, issueCount);
    }
    
    /**
     * Print report header
     */
    private void printHeader() {
        System.out.println();
        System.out.println("╔" + DOUBLE_SEPARATOR + "╗");
        System.out.println("║   Multi-Database SQL Compatibility Report              ║");
        System.out.println("╚" + DOUBLE_SEPARATOR + "╝");
        System.out.println();
    }
    
    /**
     * Print individual query report
     */
    private void printQueryReport(SQLQuery query) {
        printQueryReport(query, null);
    }
    
    /**
     * Print individual query report with optional runtime results
     */
    public void printQueryReport(SQLQuery query, Map<String, RuntimeResult> runtimeResults) {
        System.out.println("📁 File: " + query.getFileName());
        System.out.println("📍 Line: " + query.getLineNumber());
        System.out.println("🔍 Type: " + query.getQueryType());
        System.out.println();
        
        System.out.println("SQL Query:");
        String displayQuery = query.getNormalizedQuery();
        if (displayQuery.length() > 100) {
            displayQuery = displayQuery.substring(0, 100) + "...";
        }
        System.out.println(displayQuery);
        System.out.println();
        
        // Print syntax errors if any (CRITICAL - shown first)
        if (query.hasSyntaxError()) {
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  ⚠️  SYNTAX ERROR DETECTED - Query will not execute          ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.out.println();
            for (String error : query.getSyntaxErrors()) {
                System.out.println("❌ " + error);
            }
            System.out.println();
            System.out.println("💡 Fix syntax errors before checking database compatibility");
            System.out.println();
        }
        
        // Print validation results table
        System.out.println("┌─────────────┬──────────┬─────────────────────────────────────┐");
        System.out.println("│ Database    │ Status   │ Issues                              │");
        System.out.println("├─────────────┼──────────┼─────────────────────────────────────┤");
        
        for (ValidationResult result : query.getValidationResults()) {
            String database = String.format("%-11s", result.getDatabaseType());
            String status = String.format("%-8s", result.getStatusIcon() + " " + result.getStatus());
            
            if (result.getIssues().isEmpty() && result.getErrorMessage() == null) {
                System.out.println(String.format("│ %s │ %s │ %-35s │", 
                    database, status, "No issues"));
            } else {
                String issues = result.getIssues().isEmpty() ? 
                    (result.getErrorMessage() != null ? result.getErrorMessage() : "No issues") :
                    result.getIssues().get(0);
                
                if (issues.length() > 35) {
                    issues = issues.substring(0, 32) + "...";
                }
                issues = String.format("%-35s", issues);
                System.out.println(String.format("│ %s │ %s │ %s │", 
                    database, status, issues));
                
                // Print additional issues
                for (int i = 1; i < result.getIssues().size(); i++) {
                    String additionalIssue = result.getIssues().get(i);
                    if (additionalIssue.length() > 35) {
                        additionalIssue = additionalIssue.substring(0, 32) + "...";
                    }
                    additionalIssue = String.format("%-35s", additionalIssue);
                    System.out.println(String.format("│ %s │ %s │ %s │", 
                        "           ", "        ", additionalIssue));
                }
            }
        }
        
        System.out.println("└─────────────┴──────────┴─────────────────────────────────────┘");
        
        // Database-specific suggestions are removed - only show unified agnostic rewrite below
        
        // Print database-agnostic suggestions if query has any issues (including warnings)
        // BUT only if there are no syntax errors (can't rewrite broken SQL)
        if (query.hasAnyIssues() && !query.hasSyntaxError() && validator != null) {
            String agnosticSuggestion = validator.generateDatabaseAgnosticSuggestion(query);
            if (agnosticSuggestion != null) {
                System.out.println(agnosticSuggestion);
            }
        } else if (query.hasSyntaxError()) {
            System.out.println();
            System.out.println("⚠️  Cannot generate database-agnostic rewrite due to syntax errors");
            System.out.println("   Please fix the syntax errors first, then re-run the validator");
        }
        
        // Print runtime validation results if available
        if (runtimeResults != null && !runtimeResults.isEmpty()) {
            printRuntimeValidationResults(runtimeResults);
        }
        
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println();
    }
    
    /**
     * Print runtime validation results
     */
    private void printRuntimeValidationResults(Map<String, RuntimeResult> runtimeResults) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  🔄 Runtime Validation Results (Actual Database Execution)   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // Check if all results are consistent
        boolean allConsistent = true;
        Long referenceCount = null;
        String referenceDb = null;
        
        for (Map.Entry<String, RuntimeResult> entry : runtimeResults.entrySet()) {
            RuntimeResult result = entry.getValue();
            if (result.isSuccess()) {
                if (referenceCount == null) {
                    referenceCount = result.getRowCount();
                    referenceDb = entry.getKey();
                } else if (!referenceCount.equals(result.getRowCount())) {
                    allConsistent = false;
                    break;
                }
            }
        }
        
        // Print results table
        System.out.println("┌─────────────┬──────────┬────────────┬─────────────────────────┐");
        System.out.println("│ Database    │ Status   │ Row Count  │ Execution Time          │");
        System.out.println("├─────────────┼──────────┼────────────┼─────────────────────────┤");
        
        for (Map.Entry<String, RuntimeResult> entry : runtimeResults.entrySet()) {
            String database = String.format("%-11s", entry.getKey());
            RuntimeResult result = entry.getValue();
            
            String status;
            String rowCount;
            String execTime;
            
            if (result.isSuccess()) {
                status = String.format("%-8s", "✅ OK");
                rowCount = String.format("%-10s", result.getRowCount());
                execTime = String.format("%-23s", result.getExecutionTime() + " ms");
            } else {
                status = String.format("%-8s", "❌ FAIL");
                rowCount = String.format("%-10s", "N/A");
                execTime = String.format("%-23s", "Error");
            }
            
            System.out.println(String.format("│ %s │ %s │ %s │ %s │",
                database, status, rowCount, execTime));
            
            // Print error message if any
            if (!result.isSuccess() && result.getErrorMessage() != null) {
                String errorMsg = result.getErrorMessage();
                if (errorMsg.length() > 55) {
                    errorMsg = errorMsg.substring(0, 52) + "...";
                }
                System.out.println(String.format("│ %s │ %s │ %s │",
                    "           ", "Error:  ", String.format("%-48s", errorMsg) + " │"));
            }
        }
        
        System.out.println("└─────────────┴──────────┴────────────┴─────────────────────────┘");
        System.out.println();
        
        // Print consistency verdict
        if (allConsistent && referenceCount != null) {
            System.out.println("✅ CONSISTENT: All databases returned " + referenceCount + " rows");
            System.out.println("   The query produces identical results across all databases");
        } else if (referenceCount != null) {
            System.out.println("❌ INCONSISTENT: Databases returned different row counts");
            System.out.println("   ⚠️  WARNING: This query may have database-specific behavior");
            System.out.println("   Review the query logic and ensure it's truly database-agnostic");
        } else {
            System.out.println("⚠️  EXECUTION FAILED: Could not validate consistency");
            System.out.println("   Check database connections and query syntax");
        }
    }
    
    /**
     * Print summary statistics
     */
    private void printSummary(int total, int compatible, int warnings, int incompatible, 
                              Map<String, Integer> issueCount) {
        System.out.println(DOUBLE_SEPARATOR);
        System.out.println();
        System.out.println("📊 Summary Statistics:");
        System.out.println("  Total Queries Analyzed: " + total);
        
        if (total > 0) {
            int compatiblePercent = (compatible * 100) / total;
            int warningPercent = (warnings * 100) / total;
            int incompatiblePercent = (incompatible * 100) / total;
            
            System.out.println("  ✅ Fully Compatible: " + compatible + " (" + compatiblePercent + "%)");
            System.out.println("  ⚠️  Warnings: " + warnings + " (" + warningPercent + "%)");
            System.out.println("  ❌ Incompatible: " + incompatible + " (" + incompatiblePercent + "%)");
            System.out.println();
            System.out.println("🎯 Compatibility Score: " + compatiblePercent + "%");
        }
        
        if (!issueCount.isEmpty()) {
            System.out.println();
            System.out.println("Top Issues:");
            issueCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .forEach(entry -> 
                    System.out.println("  " + entry.getValue() + "x - " + entry.getKey())
                );
        }
        
        System.out.println();
        System.out.println(DOUBLE_SEPARATOR);
    }
}

// Made with Bob
