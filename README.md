# Multi-Database SQL Validator

Validates SQL queries across DB2, PostgreSQL, and Oracle. Extracts SQL from Java code and checks compatibility.

**Author:** Jaydeep Shah

## Features

- Extract SQL from Java files using JavaParser
- Detect syntax errors before validation
- Check compatibility across 3 databases
- Generate database-agnostic SQL rewrites
- Runtime validation on actual databases
- Index recommendations for performance
- **HTML reports with charts and visualizations**

## Quick Start

### Build
```bash
./build.sh
```

### Run
```bash
# Static analysis (no database needed)
java -jar sql-validator.jar --scan /path/to/code --recursive

# With runtime validation (needs database)
java -jar sql-validator.jar --scan /path/to/code --recursive --runtime-validate --config config/db-config.properties

# Generate HTML report (saved in current directory)
java -jar sql-validator.jar --scan /path/to/code --recursive --html-report validation-report.html
```

**Note:** The HTML report is generated in the directory where you run the command. You can specify any path:
```bash
# Save to reports directory
java -jar sql-validator.jar --scan /path/to/code --recursive --html-report reports/my-report.html

# Save to absolute path
java -jar sql-validator.jar --scan /path/to/code --recursive --html-report /Users/username/Documents/report.html
```

## Configuration

Copy and edit the config file:
```bash
cp config/db-config.properties.example config/db-config.properties
```

## What It Does

### 1. Extracts SQL from Java
- String literals
- Multi-line concatenations
- StringBuilder patterns
- Method arguments

### 2. Validates Syntax
- Detects typos (FORM → FROM)
- Finds unclosed quotes
- Checks parentheses balance

### 3. Checks Database Compatibility
- Date functions: `NOW()`, `SYSDATE` → `CURRENT_TIMESTAMP`
- Data types: `BOOLEAN` → `SMALLINT`
- Pagination: `LIMIT`, `ROWNUM` → `ROW_NUMBER()`

### 4. Runtime Validation (Optional)
- Executes SELECT queries on databases
- Compares result counts
- Verifies query works on all 3 databases

### 5. Index Recommendations
- Analyzes WHERE, JOIN, ORDER BY clauses
- Suggests indexes with priority (HIGH/MEDIUM/LOW)
- Generates CREATE INDEX statements

## Example Output

```
Query: SELECT * FROM USERS WHERE STATUS = 'ACTIVE' ORDER BY CREATED_DATE

Issues:
- DB2: PASS
- PostgreSQL: PASS  
- Oracle: PASS

Index Recommendations:
🔴 HIGH: CREATE INDEX idx_users_status_created ON USERS (STATUS, CREATED_DATE);
```

## Limitations

**Works:**
- SQL in Java string literals
- Multi-line concatenations
- StringBuilder patterns

**Doesn't Work:**
- SQL from external files
- SQL from properties/config
- Complex dynamic SQL
- SQL in annotations

## Project Structure

```
sql-validator-tool/
├── src/
│   ├── SQLValidatorMain.java
│   ├── extractor/          # SQL extraction
│   ├── validator/          # Validation logic
│   ├── analyzer/           # Index recommendations
│   └── reporter/           # Output formatting
├── lib/
│   └── jsqlparser-4.6.jar
├── javaparser-core-3.25.5.jar
├── config/
│   └── db-config.properties.example
└── build.sh
```

## Requirements

- Java 8+
- No database required for static analysis
- DB2, PostgreSQL, Oracle for runtime validation

## Author

Jaydeep Shah