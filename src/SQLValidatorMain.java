package com.ibm.aip.validator;

import com.ibm.aip.validator.extractor.JavaSQLExtractor;
import com.ibm.aip.validator.extractor.JavaParserSQLExtractor;
import com.ibm.aip.validator.extractor.SQLQuery;
import com.ibm.aip.validator.validator.MultiDatabaseValidator;
import com.ibm.aip.validator.validator.RuntimeValidator;
import com.ibm.aip.validator.validator.RuntimeValidator.RuntimeResult;
import com.ibm.aip.validator.reporter.ConsoleReporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Main entry point for Multi-Database SQL Validator
 *
 * Usage:
 *   java SQLValidatorMain --scan <directory> [--recursive] [--runtime-validate] [--config <file>]
 *   java SQLValidatorMain --file <java-file> [--runtime-validate] [--config <file>]
 *
 * @author Jaydeep Shah
 */
public class SQLValidatorMain {
    
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            CommandLineArgs cmdArgs = parseArguments(args);
            
            if (cmdArgs.showHelp) {
                printHelp();
                return;
            }
            
            System.out.println("🚀 Starting Multi-Database SQL Validator...");
            System.out.println();
            
            // Load configuration if runtime validation is enabled
            Properties config = null;
            RuntimeValidator runtimeValidator = null;
            
            if (cmdArgs.runtimeValidate) {
                if (cmdArgs.configFile == null) {
                    cmdArgs.configFile = "sql-validator-tool/config/db-config.properties";
                }
                
                File configFileObj = new File(cmdArgs.configFile);
                if (!configFileObj.exists()) {
                    System.err.println("❌ Error: Configuration file not found: " + cmdArgs.configFile);
                    System.err.println("   Please copy db-config.properties.example to db-config.properties");
                    System.err.println("   and configure your database credentials.");
                    return;
                }
                
                System.out.println("📋 Loading database configuration from: " + cmdArgs.configFile);
                config = loadConfig(cmdArgs.configFile);
                
                // Check if runtime validation is enabled in config
                String runtimeEnabled = config.getProperty("runtime.validation.enabled", "false");
                if (!"true".equalsIgnoreCase(runtimeEnabled)) {
                    System.err.println("❌ Error: Runtime validation is not enabled in configuration");
                    System.err.println("   Set runtime.validation.enabled=true in " + cmdArgs.configFile);
                    return;
                }
                
                System.out.println("🔄 Runtime validation enabled - will execute queries on actual databases");
                runtimeValidator = new RuntimeValidator();
                
                try {
                    // Create DatabaseConfig objects from properties
                    RuntimeValidator.DatabaseConfig db2Config = null;
                    RuntimeValidator.DatabaseConfig postgresConfig = null;
                    RuntimeValidator.DatabaseConfig oracleConfig = null;
                    
                    if ("true".equalsIgnoreCase(config.getProperty("db2.enabled", "false"))) {
                        db2Config = new RuntimeValidator.DatabaseConfig(
                            config.getProperty("db2.url"),
                            config.getProperty("db2.username"),
                            config.getProperty("db2.password"),
                            config.getProperty("db2.driver")
                        );
                    }
                    
                    if ("true".equalsIgnoreCase(config.getProperty("postgres.enabled", "false"))) {
                        postgresConfig = new RuntimeValidator.DatabaseConfig(
                            config.getProperty("postgres.url"),
                            config.getProperty("postgres.username"),
                            config.getProperty("postgres.password"),
                            config.getProperty("postgres.driver")
                        );
                    }
                    
                    if ("true".equalsIgnoreCase(config.getProperty("oracle.enabled", "false"))) {
                        oracleConfig = new RuntimeValidator.DatabaseConfig(
                            config.getProperty("oracle.url"),
                            config.getProperty("oracle.username"),
                            config.getProperty("oracle.password"),
                            config.getProperty("oracle.driver")
                        );
                    }
                    
                    runtimeValidator.connect(db2Config, postgresConfig, oracleConfig);
                    System.out.println("✓ Connected to databases");
                } catch (Exception e) {
                    System.err.println("❌ Error connecting to databases: " + e.getMessage());
                    System.err.println("   Please check your database configuration and credentials");
                    return;
                }
            } else {
                System.out.println("ℹ️  Using static analysis only (no database connection required)");
                System.out.println("   Use --runtime-validate to execute queries on actual databases");
            }
            
            // Extract SQL queries from Java files using JavaParser (AST-based)
            System.out.println("🔬 Using JavaParser for accurate SQL extraction");
            List<SQLQuery> queries;
            
            if (cmdArgs.scanDirectory != null) {
                System.out.println("📂 Scanning directory: " + cmdArgs.scanDirectory);
                System.out.println("   Recursive: " + cmdArgs.recursive);
                File dir = new File(cmdArgs.scanDirectory);
                queries = JavaParserSQLExtractor.extractFromDirectory(dir, cmdArgs.recursive);
            } else if (cmdArgs.javaFile != null) {
                System.out.println("📄 Analyzing file: " + cmdArgs.javaFile);
                File file = new File(cmdArgs.javaFile);
                queries = JavaParserSQLExtractor.extractFromFile(file);
            } else {
                System.err.println("❌ Error: Either --scan or --file must be specified");
                printHelp();
                return;
            }
            
            System.out.println("✓ Found " + queries.size() + " SQL queries");
            System.out.println();
            
            if (queries.isEmpty()) {
                System.out.println("ℹ️  No SQL queries found in the specified files.");
                return;
            }
            
            // Validate queries using static analysis
            System.out.println("🔍 Validating queries against DB2, PostgreSQL, and Oracle...");
            System.out.println("   Using static syntax analysis");
            MultiDatabaseValidator validator = new MultiDatabaseValidator();
            validator.validateQueries(queries);
            System.out.println("✓ Static validation complete");
            System.out.println();
            
            // Perform runtime validation if enabled
            if (runtimeValidator != null) {
                System.out.println("🔄 Performing runtime validation...");
                System.out.println("   ⚠️  SAFETY: Only SELECT queries will be executed");
                System.out.println("   ⚠️  INSERT/UPDATE/DELETE queries are automatically skipped");
                System.out.println();
                
                int selectQueryCount = 0;
                int skippedQueryCount = 0;
                
                for (SQLQuery query : queries) {
                    // CRITICAL SAFETY: Only validate SELECT queries
                    // All data modification queries (INSERT/UPDATE/DELETE) are skipped
                    if ("SELECT".equalsIgnoreCase(query.getQueryType()) &&
                        !query.hasSyntaxError()) {
                        
                        selectQueryCount++;
                        System.out.println("   Validating query " + selectQueryCount + " from " +
                                         query.getFileName() + ":" + query.getLineNumber());
                        
                        try {
                            Map<String, RuntimeResult> results = runtimeValidator.validateQuery(query);
                            query.setRuntimeResults(results);
                            
                            boolean consistent = runtimeValidator.areResultsConsistent(results);
                            if (consistent) {
                                System.out.println("   ✓ Results consistent across all databases");
                            } else {
                                System.out.println("   ⚠️  Results differ across databases");
                            }
                        } catch (Exception e) {
                            System.out.println("   ❌ Error: " + e.getMessage());
                        }
                    } else {
                        // Count skipped queries
                        if (!query.hasSyntaxError()) {
                            skippedQueryCount++;
                        }
                    }
                }
                
                System.out.println();
                System.out.println("✓ Runtime validation complete");
                System.out.println("   ✅ Tested: " + selectQueryCount + " SELECT queries");
                if (skippedQueryCount > 0) {
                    System.out.println("   ⚠️  Skipped: " + skippedQueryCount + " non-SELECT queries (INSERT/UPDATE/DELETE/etc.)");
                    System.out.println("   💡 Non-SELECT queries are never executed for safety");
                }
                System.out.println();
                
                // Disconnect from databases
                runtimeValidator.disconnect();
            }
            
            // Generate report
            ConsoleReporter reporter = new ConsoleReporter(validator);
            reporter.generateReport(queries);
            
            // Exit with appropriate code
            boolean hasErrors = queries.stream()
                .anyMatch(q -> !q.isCompatibleWithAllDatabases());
            
            if (hasErrors) {
                System.out.println("⚠️  Validation completed with issues. Please review the report above.");
                System.exit(1);
            } else {
                System.out.println("✅ All queries are compatible with all databases!");
                System.exit(0);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Parse command line arguments
     */
    private static CommandLineArgs parseArguments(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                case "-h":
                    cmdArgs.showHelp = true;
                    break;
                    
                case "--scan":
                    if (i + 1 < args.length) {
                        cmdArgs.scanDirectory = args[++i];
                    }
                    break;
                    
                case "--file":
                    if (i + 1 < args.length) {
                        cmdArgs.javaFile = args[++i];
                    }
                    break;
                    
                case "--recursive":
                case "-r":
                    cmdArgs.recursive = true;
                    break;
                    
                case "--runtime-validate":
                case "--runtime":
                    cmdArgs.runtimeValidate = true;
                    break;
                    
                case "--config":
                    if (i + 1 < args.length) {
                        cmdArgs.configFile = args[++i];
                    }
                    break;
                    
                default:
                    System.err.println("⚠️  Unknown argument: " + args[i]);
            }
        }
        
        return cmdArgs;
    }
    
    /**
     * Load configuration from properties file
     */
    private static Properties loadConfig(String configFile) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        }
        return props;
    }
    
    /**
     * Print help message
     */
    private static void printHelp() {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Multi-Database SQL Validator for AIP Application         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java SQLValidatorMain [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --scan <directory>     Scan directory for Java files");
        System.out.println("  --file <java-file>     Analyze specific Java file");
        System.out.println("  --recursive, -r        Scan directories recursively");
        System.out.println("  --runtime-validate     Execute queries on actual databases");
        System.out.println("  --config <file>        Database configuration file");
        System.out.println("                         (default: sql-validator-tool/config/db-config.properties)");
        System.out.println("  --help, -h             Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Static analysis only (no database connection)");
        System.out.println("  java SQLValidatorMain --scan /path/to/java/files --recursive");
        System.out.println();
        System.out.println("  # Runtime validation (executes queries on databases)");
        System.out.println("  java SQLValidatorMain --scan /path/to/java/files --runtime-validate");
        System.out.println();
        System.out.println("  # Analyze specific file with runtime validation");
        System.out.println("  java SQLValidatorMain --file PlusAipScenario.java --runtime-validate");
        System.out.println();
        System.out.println();
        System.out.println("Note:");
        System.out.println("  Static analysis validates SQL syntax without database connection.");
        System.out.println("  Runtime validation executes SELECT queries on actual databases");
        System.out.println("  and compares result counts to verify consistency.");
        System.out.println();
    }
    
    /**
     * Command line arguments holder
     */
    private static class CommandLineArgs {
        String scanDirectory = null;
        String javaFile = null;
        boolean recursive = false;
        boolean runtimeValidate = false;
        String configFile = null;
        boolean showHelp = false;
    }
}

// Made with Bob
