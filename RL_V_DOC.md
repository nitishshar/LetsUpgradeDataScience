# Data Catalog Drools Rule Validator Service

## 📋 Overview

The **Drools Data Catalog Service** is a Spring Boot application that bridges business rule authoring and technical implementation by validating and transforming business-friendly rule expressions into executable Drools rules.

### Key Features

- ✅ **Rule Validation**: Validates partial Drools rules against a data catalog
- 🔄 **Placeholder Substitution**: Converts catalog placeholders to Java property references
- 🎯 **Syntax Validation**: Checks Drools syntax for errors
- 🏗️ **Class Generation**: Auto-generates Java domain classes from data catalog
- 📊 **Comprehensive Error Reporting**: Provides detailed validation errors with suggestions

---

## 🏛️ Architecture

### Architecture Principles (SOLID)

1. **Single Responsibility Principle**
   - `DroolsRuleParser`: Only parses rule syntax
   - `DroolsRuleValidatorService`: Only orchestrates validation
   - `DroolsRuleSubstitutionService`: Only handles placeholder substitution
   - `DroolsSyntaxValidator`: Only validates Drools syntax

2. **Open/Closed Principle**
   - Extendable through new validators (implement validation interface)
   - New data catalog sources can be added via repository pattern

3. **Liskov Substitution Principle**
   - Repository pattern allows different catalog implementations

4. **Interface Segregation Principle**
   - Focused service interfaces for each concern

5. **Dependency Inversion Principle**
   - Services depend on repository abstraction, not concrete implementations

### Layer Structure

```
┌─────────────────────────────────────┐
│     Controller Layer                │
│  (REST API Endpoints)               │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Service Layer                   │
│  - DroolsRuleValidatorService       │
│  - DroolsRuleParser                 │
│  - DroolsRuleSubstitutionService    │
│  - DroolsSyntaxValidator            │
│  - JavaClassGeneratorService        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Repository Layer                │
│  - DataCatalogRepository            │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Data Layer                      │
│  - data-catalog.json                │
└─────────────────────────────────────┘
```

---

## 🚀 API Endpoints

### 1. Basic Rule Validation (Fast)

**Endpoint**: `POST /api/v1/drools/validate`

**Purpose**: Quick syntax validation without compilation

**Use When**: 
- Fast feedback during rule authoring
- CI/CD pipeline pre-checks
- When you trust the data catalog structure

**Request Body**:
```json
{
  "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>=='Y')",
  "ruleName": "RULE-001"
}
```

**Response**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "validationResult": {
    "valid": true,
    "substitutedRule": "DepositContract(...)",
    "errors": [],
    "droolsRule": "package deposits;..."
  },
  "processingTimeMs": 45
}
```

---

### 2. ⭐ Full Compilation Validation (Guaranteed Valid)

**Endpoint**: `POST /api/v1/drools/validate-full`

**Purpose**: **Complete validation by actually compiling Java + Drools code**

**Use When**: 
- ✅ **Before deploying rules to production** (CRITICAL)
- ✅ Final validation before committing rules
- ✅ When you need 100% certainty the rule will work
- ✅ Detecting property name mismatches, type errors, missing methods

**What It Does**:
1. ✅ Parses partial rule syntax
2. ✅ Validates all data elements exist in catalog
3. ✅ Generates Java class source code from catalog
4. ✅ **Compiles Java classes in-memory** using Java Compiler API
5. ✅ **Compiles Drools rule** using KIE builder with compiled classes
6. ✅ Returns specific compilation errors with line numbers

**Request Body**:
```json
{
  "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>=='Y', <Record Type>=='XRP', <Spread Rate Percentage> is null)",
  "ruleName": "RULE-DQ-001",
  "ruleDescription": "Validate spread rate for XRP contracts",
  "action": "ERROR"
}
```

**Response** (Success - Fully Compiled):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "validationResult": {
    "valid": true,
    "phase": "SUCCESS",
    "message": "✓ Rule is valid and compiles successfully",
    "substitutedRule": "DepositContract(depositContract.contractIdentifier=='Y', depositContract.recordType=='XRP', depositContract.spreadRatePercentage == null)",
    "droolsRule": "package deposits;\n\nrule \"Generated Rule\"\nwhen\n    DepositContract(depositContract.contractIdentifier=='Y', depositContract.recordType=='XRP', depositContract.spreadRatePercentage == null)\nthen\n    System.out.println(\"Rule fired\");\nend",
    "substitutions": {
      "Contract Identifier": "CONTRACT_ID",
      "Record Type": "RECORD_TYPE",
      "Spread Rate Percentage": "SPREAD_RATE_PCT"
    },
    "errors": [],
    "compilationErrors": [],
    "compilationWarnings": [],
    "javaCompilationErrors": [],
    "compiledClassName": "deposits.DepositContract"
  },
  "processingTimeMs": 892
}
```

**Response** (Failure - Java Compilation Error):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "validationResult": {
    "valid": false,
    "phase": "JAVA_COMPILATION",
    "message": "✗ Rule has compilation errors",
    "substitutedRule": "DepositContract(...)",
    "droolsRule": "...",
    "substitutions": {...},
    "errors": [],
    "compilationErrors": [],
    "javaCompilationErrors": [
      "Line 5: ';' expected",
      "Line 10: incompatible types: String cannot be converted to int"
    ],
    "compiledClassName": null
  },
  "processingTimeMs": 456
}
```

**Response** (Failure - Drools Compilation Error):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "validationResult": {
    "valid": false,
    "phase": "DROOLS_COMPILATION",
    "message": "✗ Rule has compilation errors",
    "substitutedRule": "DepositContract(depositContract.invalidProperty=='Y')",
    "droolsRule": "package deposits;...",
    "substitutions": {...},
    "errors": [],
    "compilationErrors": [
      {
        "type": "SYNTAX_ERROR",
        "message": "Unable to resolve method 'getInvalidProperty' on class deposits.DepositContract",
        "location": "Line 4",
        "suggestion": "Check that the property name matches the Java class getter/setter"
      }
    ],
    "compilationWarnings": [
      "Rule has no consequence - it will match but do nothing"
    ],
    "javaCompilationErrors": [],
    "compiledClassName": "deposits.DepositContract"
  },
  "processingTimeMs": 687
}
```

**Response** (Failure - Type Mismatch):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440003",
  "validationResult": {
    "valid": false,
    "phase": "DROOLS_COMPILATION",
    "message": "✗ Rule has compilation errors",
    "compilationErrors": [
      {
        "type": "SYNTAX_ERROR",
        "message": "Type mismatch: cannot compare BigDecimal with String",
        "location": "Line 4",
        "suggestion": "Review the Drools syntax and class structure"
      }
    ]
  },
  "processingTimeMs": 523
}
```

---

### 3. Generate Java Classes

**Endpoint**: `POST /api/v1/drools/generate-classes`

**Purpose**: Generates Java domain classes from the data catalog

**Request Body**:
```json
{
  "outputDirectory": "/path/to/output/src/main/java",
  "generateLombok": true,
  "generateJPA": true
}
```

**Response**:
```json
{
  "generatedClasses": [
    {
      "className": "DepositContract",
      "packageName": "deposits",
      "filePath": "/path/to/output/src/main/java/deposits/DepositContract.java",
      "attributeCount": 4
    }
  ],
  "totalClassesGenerated": 2,
  "outputPath": "/path/to/output/src/main/java"
}
```

---

## 💡 How It Works - Dynamic Java Class Generation

### The Magic: Truly Generic Class Generation

**Key Innovation**: The validator doesn't use pre-existing Java classes or hardcoded templates. Instead, it:

1. **Reads your data catalog** for the specific collection in the rule
2. **Dynamically generates Java source code** with exact attributes, types, and methods
3. **Compiles it in-memory** during validation
4. **Uses the compiled class** to validate the Drools rule

### Example Flow

**Your Data Catalog** (`data-catalog.json`):
```json
[
  {
    "dictionary": "Loans",
    "collection": "Loan Account", 
    "attribute": "Outstanding Balance",
    "dataType": "DECIMAL"
  },
  {
    "dictionary": "Loans",
    "collection": "Loan Account",
    "attribute": "Is Delinquent", 
    "dataType": "BOOLEAN"
  }
]
```

**Your Rule**:
```
<Loans.Loan Account>:(<Outstanding Balance> > 10000, <Is Delinquent> == true)
```

**What Happens**:

1. Parser extracts: `dictionary="Loans"`, `collection="Loan Account"`

2. **Dynamic Code Generation** - `generateJavaClassSource()` queries catalog:
   ```java
   catalogRepository.findByCollection("Loans", "Loan Account")
   // Returns 2 DataCatalogElements
   ```

3. **Generates Java Source**:
   ```java
   package loans;
   
   import java.math.BigDecimal;
   
   public class LoanAccount {
       private BigDecimal outstandingBalance;
       
       public BigDecimal getOutstandingBalance() {
           return outstandingBalance;
       }
       
       public void setOutstandingBalance(BigDecimal outstandingBalance) {
           this.outstandingBalance = outstandingBalance;
       }
       
       private Boolean isDelinquent;
       
       public Boolean isIsDelinquent() {
           return isDelinquent;
       }
       
       public void setIsDelinquent(Boolean isDelinquent) {
           this.isDelinquent = isDelinquent;
       }
   }
   ```

4. **Compiles in-memory** using Java Compiler API

5. **Validates Drools rule** against compiled class:
   - ✅ `getOutstandingBalance()` exists
   - ✅ Returns `BigDecimal` (can use `>` operator)
   - ✅ `isIsDelinquent()` exists (note: "is" prefix for boolean)
   - ✅ Returns `Boolean` (can use `==` operator)

6. **Returns validation result** with 100% confidence

### Why This Matters

**Scenario**: You have 100+ collections in your data catalog

❌ **Without Dynamic Generation**:
- Need to manually create 100+ Java classes
- Keep them in sync with catalog changes
- Deploy new classes when catalog updates
- Version management nightmare

✅ **With Dynamic Generation**:
- Zero manual Java code
- Automatic sync with catalog
- Update catalog → validator automatically uses new structure
- Validate rules for collections that don't even exist in production yet!

### Supported Data Type Mappings

| SQL Type | Java Type | Drools Operators Supported |
|----------|-----------|---------------------------|
| VARCHAR, CHAR, TEXT | String | ==, !=, matches, contains, startsWith, endsWith |
| INTEGER, INT, SMALLINT | Integer | ==, !=, <, >, <=, >=, in |
| BIGINT | Long | ==, !=, <, >, <=, >=, in |
| DECIMAL, NUMERIC, MONEY | BigDecimal | ==, !=, <, >, <=, >=, in |
| FLOAT, REAL | Double | ==, !=, <, >, <=, >=, in |
| DATE | LocalDate | ==, !=, before, after, <, > |
| TIMESTAMP, DATETIME | LocalDateTime | ==, !=, before, after, <, > |
| TIME | LocalTime | ==, !=, before, after, <, > |
| BOOLEAN, BIT | Boolean | ==, !=, &&, \|\| |
| BLOB, BINARY | byte[] | ==, != |

### Advanced Example: Multiple Collections

**Catalog has 3 collections**:
- Deposits.Deposit Contract (4 attributes)
- Loans.Loan Account (7 attributes)  
- Credit.Credit Card (12 attributes)

**User writes rules for each**:

Rule 1:
```
<Deposits.Deposit Contract>:(<Spread Rate Percentage> > 5.0)
```
→ Generates `DepositContract.java` with 4 fields

Rule 2:
```
<Loans.Loan Account>:(<Outstanding Balance> > 10000)
```
→ Generates `LoanAccount.java` with 7 fields

Rule 3:
```
<Credit.Credit Card>:(<Credit Limit> < 5000, <Is Active> == true)
```
→ Generates `CreditCard.java` with 12 fields

**Each validation**:
1. Reads ONLY the relevant catalog entries
2. Generates ONLY the needed Java class
3. Compiles and validates independently
4. No interference between rules

---

### JSON Structure

The data catalog is stored in `data-catalog.json`:

```json
[
  {
    "dictionary": "Deposits",
    "collection": "Deposit Contract",
    "attribute": "Contract Identifier",
    "physicalTable": "DEPOSIT_CONTRACT",
    "physicalColumn": "CONTRACT_ID",
    "dataType": "VARCHAR",
    "description": "Unique contract identifier"
  },
  {
    "dictionary": "Deposits",
    "collection": "Deposit Contract",
    "attribute": "Record Type",
    "physicalTable": "DEPOSIT_CONTRACT",
    "physicalColumn": "RECORD_TYPE",
    "dataType": "VARCHAR",
    "description": "Type of deposit record"
  },
  {
    "dictionary": "Deposits",
    "collection": "Deposit Contract",
    "attribute": "Spread Rate Percentage",
    "physicalTable": "DEPOSIT_CONTRACT",
    "physicalColumn": "SPREAD_RATE_PCT",
    "dataType": "DECIMAL",
    "description": "Spread rate as percentage"
  }
]
```

### Field Descriptions

| Field | Description |
|-------|-------------|
| `dictionary` | Top-level namespace (e.g., "Deposits", "Loans") |
| `collection` | Business entity/collection name (e.g., "Deposit Contract") |
| `attribute` | Business-friendly attribute name (e.g., "Contract Identifier") |
| `physicalTable` | Database table name |
| `physicalColumn` | Database column name |
| `dataType` | SQL data type |
| `description` | Business description |

---

## 🔄 Processing Flow

### Basic Validation Flow (Fast - 20-100ms)

```
1. Parse partial rule syntax
   ↓
2. Validate data elements exist in catalog
   ↓
3. Substitute placeholders with Java properties
   ↓
4. Check basic syntax (parentheses, operators)
   ↓
5. Generate Drools rule text
   ↓
6. Return result (NO COMPILATION)
```

**Limitation**: ❌ Doesn't catch:
- Property name typos in substitution
- Type mismatches (String vs Integer)
- Missing getters/setters
- Invalid Drools operators for specific types

---

### ⭐ Full Compilation Validation Flow (Complete - 500-1500ms)

```
1. Parse partial rule syntax
   ↓
2. Validate data elements exist in catalog
   ↓
3. Substitute placeholders with Java properties
   ↓
4. Generate Java class source code from catalog
   - Map data types (VARCHAR → String, DECIMAL → BigDecimal)
   - Generate getters/setters
   - Add package declaration
   ↓
5. ✅ COMPILE Java class in-memory
   - Uses Java Compiler API (javax.tools)
   - Catches syntax errors, type errors
   - Returns bytecode
   ↓
6. ✅ COMPILE Drools rule with compiled class
   - Uses KIE Builder
   - Validates rule against actual Java class
   - Checks method existence (getContractIdentifier)
   - Validates type compatibility
   - Checks operator validity
   ↓
7. Return detailed compilation result
   - Phase: JAVA_COMPILATION, DROOLS_COMPILATION, or SUCCESS
   - Specific errors with line numbers
   - Actionable suggestions
```

**Guarantees**: ✅
- Property names match exactly
- Data types are compatible
- All getters/setters exist
- Drools operators are valid for the types
- **Rule will execute successfully if deployed**

---

### Why Full Compilation Matters

**Example 1: Typo in Property Name**

❌ **Basic Validation**: PASSES (looks syntactically correct)
```
contractIdentiifer == 'Y'  // Typo: "Identiifer"
```

✅ **Full Compilation**: FAILS with clear error
```
Error: Unable to resolve method 'getContractIdentiifer' on class DepositContract
Suggestion: Check that the property name matches the Java class getter/setter
```

---

**Example 2: Type Mismatch**

❌ **Basic Validation**: PASSES
```
spreadRatePercentage == 'ABC'  // String comparison on BigDecimal field
```

✅ **Full Compilation**: FAILS
```
Error: Type mismatch: cannot compare BigDecimal with String
```

---

**Example 3: Wrong Operator for Type**

❌ **Basic Validation**: PASSES
```
maturityDate matches "2024.*"  // Using regex on Date type
```

✅ **Full Compilation**: FAILS
```
Error: 'matches' operator not applicable for type LocalDate
```

---

## 🎯 Use Cases

### Use Case 1: Business Rule Author

**Persona**: Business Analyst creating data quality rules

**Goal**: Write rules using business terminology without knowing Java/Drools

**Flow**:
1. Author writes rule using data catalog names
2. Submits to validation API
3. Receives immediate feedback on:
   - Unknown data elements
   - Syntax errors
   - Suggested corrections
4. Iterates until valid
5. Receives generated Drools rule for deployment

### Use Case 2: Data Engineer

**Persona**: Data Engineer implementing rule validation

**Goal**: Generate Java classes matching data catalog

**Flow**:
1. Calls class generation API
2. Receives generated Java classes with:
   - Lombok annotations (less boilerplate)
   - JPA annotations (database mapping)
   - Proper naming conventions
3. Uses classes in Drools rules
4. Maps directly to database tables

### Use Case 3: DevOps/CI Pipeline

**Persona**: Automated build pipeline

**Goal**: Validate all rules before deployment

**Flow**:
1. Pipeline reads all rule definitions
2. Calls validation API for each rule
3. Fails build if any rule is invalid
4. Generates report of all validation errors
5. Only deploys if all rules are valid

---

## 🔧 Extension Points

### Adding New Validators

Implement custom validators:

```java
@Component
public class CustomBusinessRuleValidator implements RuleValidator {
    
    @Override
    public List<ValidationError> validate(ParsedRule rule) {
        // Custom validation logic
        List<ValidationError> errors = new ArrayList<>();
        
        // Example: Check for banned keywords
        if (rule.getRawConditions().contains("BANNED_WORD")) {
            errors.add(ValidationError.builder()
                .type(ErrorType.BUSINESS_RULE_VIOLATION)
                .message("Rule contains banned keyword")
                .build());
        }
        
        return errors;
    }
}
```

### Adding New Data Sources

Implement alternative catalog repositories:

```java
@Repository
public class DatabaseCatalogRepository extends DataCatalogRepository {
    
    @Override
    public void loadCatalog() {
        // Load from database instead of JSON file
        List<DataCatalogElement> elements = 
            jdbcTemplate.query("SELECT * FROM DATA_CATALOG", 
                new DataCatalogRowMapper());
        // Index elements...
    }
}
```

### Adding Rule Actions

Extend the service to generate complete rules with actions:

```java
public String generateCompleteRule(String whenClause, 
                                  String action,
                                  String errorMessage) {
    return String.format(
        "rule \"%s\"\n" +
        "when\n    %s\n" +
        "then\n" +
        "    ValidationResult result = new ValidationResult();\n" +
        "    result.setAction(\"%s\");\n" +
        "    result.setMessage(\"%s\");\n" +
        "    insert(result);\n" +
        "end",
        ruleName, whenClause, action, errorMessage
    );
}
```

---

## 📦 Deployment

### Prerequisites

- Java 17+
- Maven 3.8+
- Spring Boot 3.x

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/drools-data-catalog-service-1.0.0.jar
```

### Configuration

```yaml
# application.yml
server:
  port: 8080

catalog:
  file-path: /path/to/data-catalog.json
  
spring:
  application:
    name: drools-data-catalog-service
    
logging:
  level:
    com.datacatalog.drools: DEBUG
```

---

## 🧪 Testing Examples

### Example 1: Valid Rule with Full Compilation

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate-full \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>=='\''Y'\'', <Record Type>=='\''XRP'\'')",
    "ruleName": "RULE-001"
  }'
```

**Expected**: 
```json
{
  "validationResult": {
    "valid": true,
    "phase": "SUCCESS",
    "message": "✓ Rule is valid and compiles successfully",
    "compiledClassName": "deposits.DepositContract"
  },
  "processingTimeMs": 892
}
```

---

### Example 2: Property Name Typo (Caught by Compilation)

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate-full \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identiifer>=='\''Y'\'')",
    "ruleName": "RULE-002"
  }'
```

**Expected**:
```json
{
  "validationResult": {
    "valid": false,
    "phase": "CATALOG_VALIDATION",
    "errors": [
      {
        "type": "UNKNOWN_DATA_ELEMENT",
        "message": "Data element not found: Contract Identiifer",
        "location": "Contract Identiifer"
      }
    ]
  }
}
```

---

### Example 3: Type Mismatch (Caught by Drools Compiler)

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate-full \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Spread Rate Percentage>=='\''ABC'\'')",
    "ruleName": "RULE-003"
  }'
```

**Expected**:
```json
{
  "validationResult": {
    "valid": false,
    "phase": "DROOLS_COMPILATION",
    "compilationErrors": [
      {
        "type": "SYNTAX_ERROR",
        "message": "Type mismatch: cannot compare BigDecimal with String",
        "location": "Line 4"
      }
    ]
  }
}
```

---

### Example 4: Comparison - Basic vs Full

**Scenario**: Subtle typo in camelCase conversion

**Rule**: `<Contract Identifier>` should become `contractIdentifier`
**Bug**: Substitution generates `contractidentifier` (all lowercase)

| Validation Type | Result | Why |
|----------------|--------|-----|
| Basic (`/validate`) | ✅ PASSES | Doesn't compile, just checks syntax |
| Full (`/validate-full`) | ❌ FAILS | Drools compiler can't find `getContractidentifier()` method |

**Full Compilation Error**:
```json
{
  "compilationErrors": [
    {
      "message": "Unable to resolve method 'getContractidentifier' on class DepositContract",
      "suggestion": "Check that the property name matches the Java class getter/setter"
    }
  ]
}
```

---

## 🎓 Best Practices

### For Rule Authors

1. **Always use full compilation for final validation**
   - Use `/validate` for quick iterations while writing
   - Use `/validate-full` before submitting for review/deployment

2. **Copy data element names exactly from catalog**
   - Don't type from memory
   - Use catalog browser/search

3. **Test incrementally**
   - Start with one condition
   - Add conditions one at a time
   - Validate after each addition

4. **Pay attention to data types**
   - String comparisons: `=='value'`
   - Numeric comparisons: `> 100`
   - Null checks: `is null` or `!= null`
   - Date comparisons: Use proper date format

---

### For Developers

1. **Integration with existing Java classes**
   - If classes already exist, skip generation
   - Update catalog to match existing getters/setters
   - Use full compilation to verify compatibility

2. **Catalog maintenance**
   ```java
   // Good: Keep catalog in sync with schema
   @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
   public void syncCatalogFromDatabase() {
       List<DataCatalogElement> elements = 
           extractFromInformationSchema();
       catalogRepository.reload(elements);
   }
   ```

3. **Performance optimization**
   ```java
   // Cache compiled classes to avoid recompilation
   @Cacheable("compiledClasses")
   public byte[] getCompiledClass(String className) {
       return compilationService.compile(className);
   }
   ```

4. **Error handling**
   ```java
   // Always handle compilation timeouts
   @Value("${drools.compilation.timeout:30000}")
   private long compilationTimeout;
   
   public ValidationResult validate(String rule) {
       CompletableFuture<ValidationResult> future = 
           CompletableFuture.supplyAsync(() -> 
               compilationService.compile(rule));
               
       try {
           return future.get(compilationTimeout, TimeUnit.MILLISECONDS);
       } catch (TimeoutException e) {
           return ValidationResult.timeout();
       }
   }
   ```

---

### For Operations

1. **CI/CD Pipeline Integration**

   ```yaml
   # .gitlab-ci.yml
   validate-rules:
     stage: validate
     script:
       - |
         for rule in rules/*.drl; do
           curl -X POST http://validator:8080/api/v1/drools/validate-full \
             -H "Content-Type: application/json" \
             -d @$rule || exit 1
         done
     only:
       - merge_requests
   ```

2. **Monitoring alerts**
   ```yaml
   # Alert on high compilation failure rate
   - alert: HighDroolsValidationFailureRate
     expr: rate(drools_validation_failures_total[5m]) > 0.5
     for: 10m
     annotations:
       summary: "High Drools validation failure rate"
   ```

3. **Production deployment checklist**
   - [ ] All rules validated with `/validate-full`
   - [ ] Java classes generated and committed
   - [ ] Compilation success rate > 99%
   - [ ] Performance testing completed
   - [ ] Rollback plan documented

---

### Recommended Workflow

```
┌─────────────────────────────────────────┐
│ 1. Business Analyst writes rule        │
│    using data catalog terminology      │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 2. Quick validation during authoring   │
│    POST /validate (fast feedback)      │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 3. Full compilation before submission  │
│    POST /validate-full                 │
│    ✅ Guaranteed to work                │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 4. CI/CD pipeline validates all rules  │
│    Fails build if any rule invalid     │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 5. Deploy to production                │
│    100% confidence rules will execute  │
└─────────────────────────────────────────┘
```

---

## 📈 Metrics & Monitoring

### Key Metrics

- **Validation Success Rate**: % of rules that pass validation
- **Average Processing Time**: Time to validate a rule
- **Error Distribution**: Most common error types
- **Catalog Size**: Number of data elements

### Monitoring Endpoints

```bash
# Health check
GET /actuator/health

# Metrics
GET /actuator/metrics

# Custom metrics
GET /actuator/metrics/drools.validation.success.rate
GET /actuator/metrics/drools.validation.duration
```

---

## 🐛 Troubleshooting

### Issue: "Data element not found"

**Cause**: Element not in data catalog

**Solution**:
1. Check spelling and spacing
2. Verify element exists in `data-catalog.json`
3. Reload catalog if recently added

### Issue: "Unbalanced parentheses"

**Cause**: Missing opening/closing parenthesis

**Solution**:
1. Count opening `(` and closing `)`
2. Use the location hint in error message
3. Common: missing closing `)` after conditions

### Issue: "Invalid operator"

**Cause**: Wrong operator syntax (e.g., `= =` instead of `==`)

**Solution**:
1. Use `==` for equality
2. Use `!=` for inequality
3. No spaces in operators

---

## 🔐 Security Considerations

1. **Input Validation**: All rule expressions are validated before processing
2. **No Dynamic Code Execution**: Rules are validated, not executed
3. **Catalog Access Control**: Restrict who can modify data catalog
4. **API Authentication**: Add OAuth2/JWT for production use

---

## 📚 References

- [Drools Documentation](https://docs.drools.org/)
- [Spring Boot Best Practices](https://spring.io/guides)
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)

---

## 🤝 Contributing

### Adding Features

1. Create feature branch
2. Implement following SOLID principles
3. Add unit tests (minimum 80% coverage)
4. Update documentation
5. Submit pull request

### Code Style

- Follow Google Java Style Guide
- Use Lombok for reducing boilerplate
- Document public APIs with Javadoc
- Keep methods under 20 lines when possible

---

*Last Updated: January 2026*
