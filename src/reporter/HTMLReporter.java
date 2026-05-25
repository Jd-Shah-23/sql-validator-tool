package com.ibm.aip.validator.reporter;

import com.ibm.aip.validator.extractor.SQLQuery;
import com.ibm.aip.validator.extractor.ValidationResult;
import com.ibm.aip.validator.validator.RuntimeValidator.RuntimeResult;
import com.ibm.aip.validator.analyzer.IndexRecommendationAnalyzer;
import com.ibm.aip.validator.analyzer.IndexRecommendationAnalyzer.IndexRecommendation;
import com.ibm.aip.validator.analyzer.IndexRecommendationAnalyzer.Recommendation;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HTML reporter for SQL validation results with charts and visualizations
 *
 * @author Jaydeep Shah
 */
public class HTMLReporter {
    
    private List<SQLQuery> queries;
    private String outputPath;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public HTMLReporter(List<SQLQuery> queries, String outputPath) {
        this.queries = queries;
        this.outputPath = outputPath;
    }
    
    /**
     * Generate HTML report
     */
    public void generateReport() throws IOException {
        StringBuilder html = new StringBuilder();
        
        // HTML Header
        html.append(getHTMLHeader());
        
        // Summary Section
        html.append(generateSummarySection());
        
        // Charts Section
        html.append(generateChartsSection());
        
        // Detailed Results
        html.append(generateDetailedResults());
        
        // HTML Footer
        html.append(getHTMLFooter());
        
        // Write to file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
    }
    
    /**
     * Generate HTML header with embedded CSS and JavaScript
     */
    private String getHTMLHeader() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>SQL Validation Report - " + dateFormat.format(new Date()) + "</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n" +
                "    <style>\n" +
                getCSS() +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <header>\n" +
                "            <h1>🗄️ Multi-Database SQL Validation Report</h1>\n" +
                "            <p class=\"subtitle\">Generated on " + dateFormat.format(new Date()) + "</p>\n" +
                "        </header>\n";
    }
    
    /**
     * Get embedded CSS styles
     */
    private String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }\n" +
                ".container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n" +
                "header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; text-align: center; }\n" +
                "header h1 { font-size: 2.5em; margin-bottom: 10px; }\n" +
                ".subtitle { opacity: 0.9; font-size: 1.1em; }\n" +
                ".summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; padding: 30px; background: #f8f9fa; }\n" +
                ".stat-card { background: white; padding: 25px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; transition: transform 0.3s; }\n" +
                ".stat-card:hover { transform: translateY(-5px); box-shadow: 0 5px 20px rgba(0,0,0,0.15); }\n" +
                ".stat-number { font-size: 3em; font-weight: bold; margin: 10px 0; }\n" +
                ".stat-label { color: #666; font-size: 1.1em; text-transform: uppercase; letter-spacing: 1px; }\n" +
                ".success { color: #28a745; }\n" +
                ".warning { color: #ffc107; }\n" +
                ".danger { color: #dc3545; }\n" +
                ".info { color: #17a2b8; }\n" +
                ".charts-section { padding: 30px; background: white; }\n" +
                ".chart-container { margin: 30px 0; }\n" +
                ".chart-wrapper { position: relative; height: 400px; margin: 20px 0; }\n" +
                ".section-title { font-size: 1.8em; margin: 30px 0 20px 0; color: #333; border-bottom: 3px solid #667eea; padding-bottom: 10px; }\n" +
                ".query-card { background: #f8f9fa; border-left: 4px solid #667eea; padding: 20px; margin: 20px 0; border-radius: 5px; }\n" +
                ".query-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }\n" +
                ".query-meta { color: #666; font-size: 0.9em; }\n" +
                ".query-sql { background: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; overflow-x: auto; font-family: 'Courier New', monospace; margin: 15px 0; }\n" +
                ".results-table { width: 100%; border-collapse: collapse; margin: 15px 0; }\n" +
                ".results-table th { background: #667eea; color: white; padding: 12px; text-align: left; }\n" +
                ".results-table td { padding: 12px; border-bottom: 1px solid #ddd; }\n" +
                ".results-table tr:hover { background: #f5f5f5; }\n" +
                ".badge { display: inline-block; padding: 5px 12px; border-radius: 20px; font-size: 0.85em; font-weight: bold; }\n" +
                ".badge-success { background: #d4edda; color: #155724; }\n" +
                ".badge-warning { background: #fff3cd; color: #856404; }\n" +
                ".badge-danger { background: #f8d7da; color: #721c24; }\n" +
                ".index-recommendations { background: #e7f3ff; border-left: 4px solid #0066cc; padding: 15px; margin: 15px 0; border-radius: 5px; }\n" +
                ".recommendation-item { margin: 10px 0; padding: 10px; background: white; border-radius: 3px; }\n" +
                ".code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: monospace; }\n" +
                "footer { background: #2d2d2d; color: white; text-align: center; padding: 20px; }\n" +
                ".progress-bar { width: 100%; height: 30px; background: #e9ecef; border-radius: 15px; overflow: hidden; margin: 10px 0; }\n" +
                ".progress-fill { height: 100%; background: linear-gradient(90deg, #28a745 0%, #20c997 100%); display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; transition: width 0.5s; }\n";
    }
    
    /**
     * Generate summary section with statistics
     */
    private String generateSummarySection() {
        int totalQueries = queries.size();
        int fullyCompatible = 0;
        int warnings = 0;
        int incompatible = 0;
        
        for (SQLQuery query : queries) {
            if (query.isCompatibleWithAllDatabases()) {
                fullyCompatible++;
            } else {
                boolean hasFailure = false;
                for (ValidationResult result : query.getValidationResults()) {
                    if ("FAIL".equals(result.getStatus())) {
                        hasFailure = true;
                        incompatible++;
                        break;
                    }
                }
                if (!hasFailure) {
                    warnings++;
                }
            }
        }
        
        int compatibilityScore = totalQueries > 0 ? (fullyCompatible * 100 / totalQueries) : 0;
        
        StringBuilder html = new StringBuilder();
        html.append("        <div class=\"summary\">\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-number info\">").append(totalQueries).append("</div>\n");
        html.append("                <div class=\"stat-label\">Total Queries</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-number success\">").append(fullyCompatible).append("</div>\n");
        html.append("                <div class=\"stat-label\">Compatible</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-number warning\">").append(warnings).append("</div>\n");
        html.append("                <div class=\"stat-label\">Warnings</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-number danger\">").append(incompatible).append("</div>\n");
        html.append("                <div class=\"stat-label\">Incompatible</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // Compatibility Score Progress Bar
        html.append("        <div style=\"padding: 30px; background: white;\">\n");
        html.append("            <h2 class=\"section-title\">Overall Compatibility Score</h2>\n");
        html.append("            <div class=\"progress-bar\">\n");
        html.append("                <div class=\"progress-fill\" style=\"width: ").append(compatibilityScore).append("%\">\n");
        html.append("                    ").append(compatibilityScore).append("%\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        return html.toString();
    }
    
    /**
     * Generate charts section with visualizations
     */
    private String generateChartsSection() {
        StringBuilder html = new StringBuilder();
        html.append("        <div class=\"charts-section\">\n");
        html.append("            <h2 class=\"section-title\">📊 Visual Analytics</h2>\n");
        
        // Database Compatibility Chart
        html.append("            <div class=\"chart-container\">\n");
        html.append("                <h3>Database Compatibility Breakdown</h3>\n");
        html.append("                <div class=\"chart-wrapper\">\n");
        html.append("                    <canvas id=\"compatibilityChart\"></canvas>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        
        // Query Status Distribution
        html.append("            <div class=\"chart-container\">\n");
        html.append("                <h3>Query Status Distribution</h3>\n");
        html.append("                <div class=\"chart-wrapper\">\n");
        html.append("                    <canvas id=\"statusChart\"></canvas>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        
        html.append("        </div>\n");
        
        return html.toString();
    }
    
    /**
     * Generate detailed results section
     */
    private String generateDetailedResults() {
        StringBuilder html = new StringBuilder();
        html.append("        <div style=\"padding: 30px; background: #f8f9fa;\">\n");
        html.append("            <h2 class=\"section-title\">📝 Detailed Query Analysis</h2>\n");
        
        int queryNumber = 1;
        for (SQLQuery query : queries) {
            html.append(generateQueryCard(query, queryNumber++));
        }
        
        html.append("        </div>\n");
        return html.toString();
    }
    
    /**
     * Generate individual query card
     */
    private String generateQueryCard(SQLQuery query, int queryNumber) {
        StringBuilder html = new StringBuilder();
        html.append("            <div class=\"query-card\">\n");
        html.append("                <div class=\"query-header\">\n");
        html.append("                    <h3>Query #").append(queryNumber).append("</h3>\n");
        html.append("                    <div class=\"query-meta\">\n");
        html.append("                        📁 ").append(escapeHtml(query.getFileName())).append(" | ");
        html.append("                        📍 Line ").append(query.getLineNumber()).append("\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        
        // SQL Query
        html.append("                <div class=\"query-sql\">\n");
        html.append("                    ").append(escapeHtml(query.getNormalizedQuery())).append("\n");
        html.append("                </div>\n");
        
        // Validation Results Table
        html.append("                <table class=\"results-table\">\n");
        html.append("                    <thead>\n");
        html.append("                        <tr>\n");
        html.append("                            <th>Database</th>\n");
        html.append("                            <th>Status</th>\n");
        html.append("                            <th>Issues</th>\n");
        html.append("                        </tr>\n");
        html.append("                    </thead>\n");
        html.append("                    <tbody>\n");
        
        for (ValidationResult result : query.getValidationResults()) {
            html.append("                        <tr>\n");
            html.append("                            <td><strong>").append(result.getDatabaseType()).append("</strong></td>\n");
            html.append("                            <td>");
            
            if ("PASS".equals(result.getStatus())) {
                html.append("<span class=\"badge badge-success\">✅ PASS</span>");
            } else if ("WARN".equals(result.getStatus())) {
                html.append("<span class=\"badge badge-warning\">⚠️ WARN</span>");
            } else {
                html.append("<span class=\"badge badge-danger\">❌ FAIL</span>");
            }
            
            html.append("</td>\n");
            html.append("                            <td>");
            
            if (result.getIssues().isEmpty()) {
                html.append("No issues");
            } else {
                html.append("<ul style=\"margin: 0; padding-left: 20px;\">");
                for (String issue : result.getIssues()) {
                    html.append("<li>").append(escapeHtml(issue)).append("</li>");
                }
                html.append("</ul>");
            }
            
            html.append("</td>\n");
            html.append("                        </tr>\n");
        }
        
        html.append("                    </tbody>\n");
        html.append("                </table>\n");
        
        // Index Recommendations
        try {
            if (query.getNormalizedQuery().trim().toUpperCase().startsWith("SELECT")) {
                IndexRecommendationAnalyzer analyzer = new IndexRecommendationAnalyzer();
                IndexRecommendation recommendation = analyzer.analyzeQuery(query.getNormalizedQuery());
                
                if (!recommendation.hasError() && recommendation.hasRecommendations()) {
                    html.append("                <div class=\"index-recommendations\">\n");
                    html.append("                    <h4>💡 Index Recommendations</h4>\n");
                    
                    for (Recommendation rec : recommendation.getRecommendations()) {
                        html.append("                    <div class=\"recommendation-item\">\n");
                        html.append("                        <strong>").append(rec.getPriority()).append(":</strong> ");
                        html.append(rec.getType()).append(" on columns: <span class=\"code\">");
                        html.append(rec.getColumnsAsString()).append("</span><br>\n");
                        html.append("                        <small>").append(rec.getReason()).append("</small>\n");
                        html.append("                    </div>\n");
                    }
                    
                    html.append("                </div>\n");
                }
            }
        } catch (Exception e) {
            // Silently skip index recommendations if analysis fails
        }
        
        html.append("            </div>\n");
        return html.toString();
    }
    
    /**
     * Generate HTML footer with JavaScript for charts
     */
    private String getHTMLFooter() {
        StringBuilder html = new StringBuilder();
        html.append("        <footer>\n");
        html.append("            <p>Generated by Multi-Database SQL Validator Tool</p>\n");
        html.append("            <p>Author: Jaydeep Shah | © 2024</p>\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append(getChartJavaScript());
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }
    
    /**
     * Generate JavaScript for charts
     */
    private String getChartJavaScript() {
        // Calculate statistics for charts
        Map<String, Integer> dbStats = new HashMap<>();
        dbStats.put("DB2_PASS", 0);
        dbStats.put("DB2_FAIL", 0);
        dbStats.put("PostgreSQL_PASS", 0);
        dbStats.put("PostgreSQL_FAIL", 0);
        dbStats.put("Oracle_PASS", 0);
        dbStats.put("Oracle_FAIL", 0);
        
        int compatible = 0;
        int warnings = 0;
        int incompatible = 0;
        
        for (SQLQuery query : queries) {
            if (query.isCompatibleWithAllDatabases()) {
                compatible++;
            } else {
                boolean hasFailure = false;
                for (ValidationResult result : query.getValidationResults()) {
                    String key = result.getDatabaseType() + "_" + ("PASS".equals(result.getStatus()) ? "PASS" : "FAIL");
                    dbStats.put(key, dbStats.getOrDefault(key, 0) + 1);
                    
                    if ("FAIL".equals(result.getStatus())) {
                        hasFailure = true;
                    }
                }
                if (hasFailure) {
                    incompatible++;
                } else {
                    warnings++;
                }
            }
        }
        
        return "// Compatibility Chart\n" +
                "const compatibilityCtx = document.getElementById('compatibilityChart').getContext('2d');\n" +
                "new Chart(compatibilityCtx, {\n" +
                "    type: 'bar',\n" +
                "    data: {\n" +
                "        labels: ['DB2', 'PostgreSQL', 'Oracle'],\n" +
                "        datasets: [{\n" +
                "            label: 'Compatible',\n" +
                "            data: [" + dbStats.get("DB2_PASS") + ", " + dbStats.get("PostgreSQL_PASS") + ", " + dbStats.get("Oracle_PASS") + "],\n" +
                "            backgroundColor: 'rgba(40, 167, 69, 0.8)'\n" +
                "        }, {\n" +
                "            label: 'Incompatible',\n" +
                "            data: [" + dbStats.get("DB2_FAIL") + ", " + dbStats.get("PostgreSQL_FAIL") + ", " + dbStats.get("Oracle_FAIL") + "],\n" +
                "            backgroundColor: 'rgba(220, 53, 69, 0.8)'\n" +
                "        }]\n" +
                "    },\n" +
                "    options: {\n" +
                "        responsive: true,\n" +
                "        maintainAspectRatio: false,\n" +
                "        scales: {\n" +
                "            y: { beginAtZero: true }\n" +
                "        }\n" +
                "    }\n" +
                "});\n\n" +
                "// Status Distribution Chart\n" +
                "const statusCtx = document.getElementById('statusChart').getContext('2d');\n" +
                "new Chart(statusCtx, {\n" +
                "    type: 'doughnut',\n" +
                "    data: {\n" +
                "        labels: ['Fully Compatible', 'Warnings', 'Incompatible'],\n" +
                "        datasets: [{\n" +
                "            data: [" + compatible + ", " + warnings + ", " + incompatible + "],\n" +
                "            backgroundColor: [\n" +
                "                'rgba(40, 167, 69, 0.8)',\n" +
                "                'rgba(255, 193, 7, 0.8)',\n" +
                "                'rgba(220, 53, 69, 0.8)'\n" +
                "            ]\n" +
                "        }]\n" +
                "    },\n" +
                "    options: {\n" +
                "        responsive: true,\n" +
                "        maintainAspectRatio: false\n" +
                "    }\n" +
                "});\n";
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&" + "amp;")
                   .replace("<", "&" + "lt;")
                   .replace(">", "&" + "gt;")
                   .replace(String.valueOf((char)34), "&" + "quot;")
                   .replace(String.valueOf((char)39), "&" + "#39;");
    }
}

// Made with Bob
