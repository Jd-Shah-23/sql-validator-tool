# Multi-Database SQL Validator Tool

A comprehensive Java-based static analysis tool that validates SQL queries across DB2, PostgreSQL, and Oracle databases. Detects syntax errors, identifies database-specific incompatibilities, and generates database-agnostic SQL rewrites—all without requiring database connections.

## 🎯 Purpose

This tool was developed as an innovation project for the Asset Investment Planning (AIP) application to ensure SQL compatibility across multiple database platforms. It helps developers:

- **Catch SQL errors early** in the development cycle
- **Ensure database portability** across DB2, PostgreSQL, and Oracle
- **Automate code quality checks** without manual testing
- **Generate portable SQL** that works on all supported databases

## ✨ Key Features

### 🔍 Syntax Error Detection
- Validates SQL syntax before compatibility checks
- Detects common typos (FORM → FROM, SELCT → SELECT)
- Catches unclosed quotes and unbalanced parentheses
- Uses JSqlParser for comprehensive validation

### 🗄️ Multi-Database Validation
- Validates against DB2, PostgreSQL, and Oracle simultaneously
- Static analysis (no database connection required)
- Identifies database-specific syntax issues
- Handles unlimited issues per query

### ✨ Database-Agnostic Rewrites
Automatically generates unified SQL that works on all 3 databases:
- **Date functions**: `NOW()`, `SYSDATE` → `CURRENT_TIMESTAMP`
- **Data types**: `BOOLEAN` → `SMALLINT`, `AUTO_INCREMENT` → `IDENTITY`
- **Pagination**: `LIMIT`, `ROWNUM`, `FETCH FIRST` → `ROW_NUMBER()`
- **Multi-issue handling**: Processes queries with multiple incompatibilities

### 📊 Comprehensive Reporting
- Clear, color-coded console output
- Detailed issue descriptions per database
- Before/after SQL comparison
- Actionable recommendations

## 🚀 Quick Start

### Prerequisites
- Java 8 or higher
- No database installation required

### Build
```bash
chmod +x build.sh
./build.sh
```

### Run

**Validate a single file:**
```bash
java -jar sql-validator.jar --file path/to/YourFile.java
```

**Scan entire directory:**
```bash
java -jar sql-validator.jar --scan path/to/directory --recursive
```

**Scan current directory:**
```bash
java -jar sql-validator.jar --scan . --recursive
```

## 📖 Example Output

### Syntax Error Detection
```
SQL Query: SELECT * FORM USERS WHERE ID = 1

╔═══════════════════════════════════════════════════════════════╗
║  ⚠️  SYNTAX ERROR DETECTED - Query will not execute          ║
╚═══════════════════════════════════════════════════════════════╝

❌ Possible typo: 'FORM' should be 'FROM'
💡 Fix syntax errors before checking database compatibility
```

### Multi-Issue Handling
```
SQL Query: SELECT * FROM USERS WHERE CREATED > NOW() AND ROWNUM <= 10 LIMIT 5

Issues Detected:
- NOW() function (not supported on DB2/Oracle)
- ROWNUM (Oracle-specific, not supported on DB2/PostgreSQL)
- LIMIT (PostgreSQL/MySQL, not supported on Oracle)

✅ Database-Agnostic Query (Works on ALL 3 databases):
SELECT * FROM (
  SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS rn
  FROM (SELECT * FROM USERS WHERE CREATED > CURRENT_TIMESTAMP) inner_query
) WHERE rn BETWEEN 1 AND 5
```

## 📁 Project Structure

```
sql-validator-tool/
├── src/
│   ├── SQLValidatorMain.java          # Main entry point
│   ├── extractor/                     # SQL extraction from Java files
│   │   ├── JavaSQLExtractor.java
│   │   ├── SQLQuery.java
│   │   └── ValidationResult.java
│   ├── validator/                     # Validation logic
│   │   ├── SyntaxValidator.java       # Syntax error detection
│   │   ├── EnhancedSQLRewriter.java   # SQL rewriting engine
│   │   └── MultiDatabaseValidator.java # Multi-DB validation
│   └── reporter/                      # Output formatting
│       └── ConsoleReporter.java
├── lib/
│   └── jsqlparser-4.6.jar            # SQL parsing library
├── test-examples/                     # Sample test files
├── config/                           # Configuration files
├── build.sh                          # Build script
├── manifest.txt                      # JAR manifest
└── README.md                         # This file
```

## 🔧 Supported Transformations

### Date/Time Functions
| Original | Rewritten |
|----------|-----------|
| `NOW()` | `CURRENT_TIMESTAMP` |
| `SYSDATE` | `CURRENT_TIMESTAMP` |
| `GETDATE()` | `CURRENT_TIMESTAMP` |

### Data Types
| Original | Rewritten |
|----------|-----------|
| `BOOLEAN` | `SMALLINT` (0=false, 1=true) |
| `AUTO_INCREMENT` | `GENERATED ALWAYS AS IDENTITY` |
| `SERIAL` | `INTEGER GENERATED ALWAYS AS IDENTITY` |

### Pagination
| Original | Rewritten |
|----------|-----------|
| `LIMIT n OFFSET m` | `ROW_NUMBER() BETWEEN m+1 AND m+n` |
| `ROWNUM <= n` | `ROW_NUMBER() <= n` |
| `FETCH FIRST n ROWS ONLY` | `ROW_NUMBER() <= n` |

## ⚠️ Limitations

### What It DOES ✅
- Detects SQL syntax errors
- Validates database compatibility
- Generates database-agnostic rewrites
- Handles unlimited issues per query
- Works without database connections

### What It DOES NOT ❌
- Validate table/column existence (requires database)
- Check data type mismatches (requires schema)
- Verify permissions (requires runtime)
- Fix business logic errors
- Execute queries

## 🎓 Use Cases

### Development
- Validate SQL during code reviews
- Catch compatibility issues before deployment
- Ensure code works across all target databases

### CI/CD Integration
- Add to build pipeline for automated validation
- Fail builds on SQL compatibility issues
- Generate reports for code quality metrics

### Migration Projects
- Identify database-specific SQL during migrations
- Generate portable SQL for multi-database support
- Reduce manual testing effort

## 📝 Configuration

Edit `config/db-config.properties.example` to customize:
- Database-specific rules
- Custom SQL patterns
- Validation thresholds

## 🤝 Contributing

This tool was developed for the AIP project. For enhancements or bug reports, contact the AIP development team.

## 📄 License

Internal IBM tool for Asset Investment Planning project.

## 👥 Authors

- **AIP Innovation Team**
- Developed with Bob (AI Assistant)

## 🏆 Version History

- **v3.0.0** (Current) - Added syntax error detection and multi-issue handling
- **v2.0.0** - Added database-agnostic SQL rewrites
- **v1.0.0** - Initial release with basic validation

## 📞 Support

For questions or issues:
1. Check the [GIT_SETUP.md](GIT_SETUP.md) for Git-related help
2. Review example files in `test-examples/`
3. Contact the AIP development team

---

**Built for IBM Developer Certificate Application** 🎓

This tool demonstrates:
- Advanced SQL parsing and analysis
- Multi-database expertise (DB2, PostgreSQL, Oracle)
- Automated code quality tools
- Static analysis techniques
- Software engineering best practices