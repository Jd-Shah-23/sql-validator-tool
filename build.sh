#!/bin/bash
# Build script for Multi-Database SQL Validator

echo "🔨 Building Multi-Database SQL Validator..."
echo ""

# Clean previous build
echo "🧹 Cleaning previous build..."
rm -rf bin
rm -f sql-validator.jar
rm -f manifest.txt

# Create directories
echo "📁 Creating build directories..."
mkdir -p bin
mkdir -p lib
mkdir -p reports

# Compile Java files in correct order
echo "⚙️  Compiling Java source files..."

# Step 1: Compile ValidationResult (no dependencies)
echo "  Compiling ValidationResult..."
javac -d bin src/extractor/ValidationResult.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at ValidationResult!"
    exit 1
fi

# Step 2: Compile SQLQuery (depends on ValidationResult)
echo "  Compiling SQLQuery..."
javac -d bin -cp bin src/extractor/SQLQuery.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at SQLQuery!"
    exit 1
fi

# Step 3: Compile JavaSQLExtractor (depends on SQLQuery)
echo "  Compiling JavaSQLExtractor..."
javac -d bin -cp bin src/extractor/JavaSQLExtractor.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at JavaSQLExtractor!"
    exit 1
fi

# Step 4: Compile MultiDatabaseValidator (depends on SQLQuery, ValidationResult)
echo "  Compiling MultiDatabaseValidator..."
javac -d bin -cp bin src/validator/MultiDatabaseValidator.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at MultiDatabaseValidator!"
    exit 1
fi

# Step 5: Compile ConsoleReporter (depends on SQLQuery, ValidationResult)
echo "  Compiling ConsoleReporter..."
javac -d bin -cp bin src/reporter/ConsoleReporter.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at ConsoleReporter!"
    exit 1
fi

# Step 6: Compile SQLValidatorMain (depends on all above)
echo "  Compiling SQLValidatorMain..."
javac -d bin -cp bin src/SQLValidatorMain.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed at SQLValidatorMain!"
    exit 1
fi

echo "✅ Compilation successful!"
echo ""

# Create manifest file
echo "📝 Creating manifest file..."
cat > manifest.txt << 'EOF'
Manifest-Version: 1.0
Main-Class: com.ibm.aip.validator.SQLValidatorMain

EOF

# Create JAR file
echo "📦 Creating JAR file..."
jar cvfm sql-validator.jar manifest.txt -C bin .

if [ $? -ne 0 ]; then
    echo "❌ JAR creation failed!"
    exit 1
fi

echo "✅ JAR file created: sql-validator.jar"
echo ""

# Verify JAR contents
echo "🔍 Verifying JAR contents..."
jar tf sql-validator.jar | grep "SQLValidatorMain.class"
if [ $? -eq 0 ]; then
    echo "✅ Main class found in JAR"
else
    echo "❌ Main class NOT found in JAR!"
fi
echo ""

# Test the JAR
echo "🧪 Testing JAR file..."
java -jar sql-validator.jar --help

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📋 Usage:"
    echo "  java -jar sql-validator.jar --scan <directory> --recursive"
    echo ""
    echo "Example:"
    echo "  java -jar sql-validator.jar --scan ../asset-investment-planning/AssetInvestPlan/applications/maximo/businessobjects/src/psdi/app/plusaip --recursive"
    echo ""
else
    echo "❌ JAR test failed!"
    echo ""
    echo "💡 Try running without JAR:"
    echo "  java -cp bin com.ibm.aip.validator.SQLValidatorMain --help"
    exit 1
fi

# Made with Bob
