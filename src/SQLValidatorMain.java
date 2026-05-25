package com.ibm.aip.validator;

import com.ibm.aip.validator.extractor.JavaSQLExtractor;
import com.ibm.aip.validator.extractor.SQLQuery;
import com.ibm.aip.validator.validator.MultiDatabaseValidator;
import com.ibm.aip.validator.reporter.ConsoleReporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Main entry point for Multi-Database SQL Validator
 * 
 * Usage:
 *   java SQLValidatorMain --scan <directory> [--recursive] [--test-db] [--config <file>]
 *   java SQLValidatorMain --file <java-file> [--test-db] [--config <file>]
 * 
 * @author AIP Innovation Team
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
            
            // Configuration not needed for static analysis
            System.out.println("ℹ️  Using static analysis (no database connection required)");
            
            // Extract SQL queries from Java files
            JavaSQLExtractor extractor = new JavaSQLExtractor();
            List<SQLQuery> queries;
            
            if (cmdArgs.scanDirectory != null) {
                System.out.println("📂 Scanning directory: " + cmdArgs.scanDirectory);
                System.out.println("   Recursive: " + cmdArgs.recursive);
                File dir = new File(cmdArgs.scanDirectory);
                queries = extractor.extractFromDirectory(dir, cmdArgs.recursive);
            } else if (cmdArgs.javaFile != null) {
                System.out.println("📄 Analyzing file: " + cmdArgs.javaFile);
                File file = new File(cmdArgs.javaFile);
                queries = extractor.extractFromFile(file);
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
            System.out.println("✓ Validation complete");
            System.out.println();
            
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
                    
                // Removed --test-db and --config options (static analysis only)
                    
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
        System.out.println("  --help, -h             Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Scan directory recursively");
        System.out.println("  java SQLValidatorMain --scan /path/to/java/files --recursive");
        System.out.println();
        System.out.println("  # Analyze specific file");
        System.out.println("  java SQLValidatorMain --file PlusAipScenario.java");
        System.out.println();
        System.out.println();
        System.out.println("Note:");
        System.out.println("  This tool uses static syntax analysis only.");
        System.out.println("  No database connection is required.");
        System.out.println();
    }
    
    /**
     * Command line arguments holder
     */
    private static class CommandLineArgs {
        String scanDirectory = null;
        String javaFile = null;
        boolean recursive = false;
        boolean testWithDB = false;
        String configFile = null;
        boolean showHelp = false;
    }
}

// Made with Bob
