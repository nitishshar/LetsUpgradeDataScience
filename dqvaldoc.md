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

### 1. Validate Drools Rule

**Endpoint**: `POST /api/v1/drools/validate`

**Purpose**: Validates a partial Drools rule containing data catalog placeholders

**Request Body**:
```json
{
  "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>=='Y', <Record Type>=='XRP', <Spread Rate Percentage> is null)",
  "ruleName": "RULE-DQ-001",
  "ruleDescription": "Validate spread rate for XRP contracts",
  "action": "ERROR"
}
```

**Response** (Success - Valid Rule):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "validationResult": {
    "valid": true,
    "substitutedRule": "DepositContract(depositContract.contractIdentifier=='Y', depositContract.recordType=='XRP', depositContract.spreadRatePercentage is null)",
    "errors": [],
    "substitutions": {
      "Contract Identifier": "CONTRACT_ID",
      "Record Type": "RECORD_TYPE",
      "Spread Rate Percentage": "SPREAD_RATE_PCT"
    },
    "droolsRule": "package deposits;\n\nrule \"Generated Rule\"\nwhen\n    DepositContract(depositContract.contractIdentifier=='Y', depositContract.recordType=='XRP', depositContract.spreadRatePercentage is null)\nthen\n    // Action to be defined\nend"
  },
  "processingTimeMs": 45
}
```

**Response** (Failure - Unknown Data Element):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "validationResult": {
    "valid": false,
    "substitutedRule": null,
    "errors": [
      {
        "type": "UNKNOWN_DATA_ELEMENT",
        "message": "Data element not found in catalog: Invalid Field",
        "location": "Invalid Field",
        "suggestion": "Check data catalog for valid elements"
      }
    ],
    "substitutions": {
      "Contract Identifier": "CONTRACT_ID"
    },
    "droolsRule": null
  },
  "processingTimeMs": 23
}
```

**Response** (Failure - Syntax Error):
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "validationResult": {
    "valid": false,
    "substitutedRule": "DepositContract(depositContract.contractIdentifier='=Y'",
    "errors": [
      {
        "type": "SYNTAX_ERROR",
        "message": "Invalid operator: '= =' should be '=='",
        "location": null,
        "suggestion": "Use '==' for equality comparison"
      },
      {
        "type": "MISSING_PARENTHESIS",
        "message": "Unbalanced parentheses in expression",
        "location": "Missing closing parenthesis",
        "suggestion": "Ensure all opening parentheses have matching closing ones"
      }
    ],
    "substitutions": {
      "Contract Identifier": "CONTRACT_ID"
    },
    "droolsRule": null
  },
  "processingTimeMs": 18
}
```

### 2. Generate Java Classes

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
    },
    {
      "className": "AccountDetails",
      "packageName": "deposits",
      "filePath": "/path/to/output/src/main/java/deposits/AccountDetails.java",
      "attributeCount": 7
    }
  ],
  "totalClassesGenerated": 2,
  "outputPath": "/path/to/output/src/main/java"
}
```

**Generated Java Class Example**:
```java
package deposits;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "DEPOSIT_CONTRACT")
public class DepositContract {

    @Column(name = "CONTRACT_ID")
    private String contractIdentifier;

    @Column(name = "RECORD_TYPE")
    private String recordType;

    @Column(name = "SPREAD_RATE_PCT")
    private BigDecimal spreadRatePercentage;

    @Column(name = "MATURITY_DT")
    private LocalDate maturityDate;

}
```

---

## 📊 Data Catalog Format

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

### Validation Flow (Scenario 1: Success)

```
1. User submits partial rule
   ↓
2. DroolsRuleParser parses structure
   - Extracts dictionary, collection
   - Extracts data element placeholders
   ↓
3. DroolsRuleValidatorService validates
   - Checks each data element exists in catalog
   - Maps placeholders to physical columns
   ↓
4. DroolsRuleSubstitutionService substitutes
   - Replaces <Collection> with Java class name
   - Replaces <Attributes> with camelCase properties
   ↓
5. DroolsSyntaxValidator validates syntax
   - Checks parenthesis balance
   - Validates operators
   ↓
6. Generate complete Drools rule
   ↓
7. Return ValidationResult (valid=true)
```

### Validation Flow (Scenario 2: Unknown Data Element)

```
1. User submits partial rule with invalid element
   ↓
2. DroolsRuleParser parses structure
   ↓
3. DroolsRuleValidatorService validates
   - Finds unknown data element "Invalid Field"
   ↓
4. Return ValidationResult
   - valid = false
   - error: UNKNOWN_DATA_ELEMENT
   - suggestion: Check data catalog
```

### Validation Flow (Scenario 3: Syntax Error)

```
1. User submits rule with syntax error
   ↓
2. DroolsRuleParser parses structure
   ↓
3. DroolsRuleValidatorService validates data elements (pass)
   ↓
4. DroolsRuleSubstitutionService substitutes placeholders
   ↓
5. DroolsSyntaxValidator detects errors
   - Unbalanced parentheses
   - Invalid operator '= ='
   ↓
6. Return ValidationResult
   - valid = false
   - errors: [SYNTAX_ERROR, MISSING_PARENTHESIS]
   - specific suggestions for each error
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

### Example 1: Valid Rule

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>=='\''Y'\'', <Record Type>=='\''XRP'\'')",
    "ruleName": "RULE-001"
  }'
```

**Expected**: `valid: true` with complete Drools rule

### Example 2: Unknown Data Element

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Invalid Field>=='\''Y'\'')",
    "ruleName": "RULE-002"
  }'
```

**Expected**: `valid: false` with UNKNOWN_DATA_ELEMENT error

### Example 3: Syntax Error

**Request**:
```bash
curl -X POST http://localhost:8080/api/v1/drools/validate \
  -H "Content-Type: application/json" \
  -d '{
    "partialDroolRule": "<Deposits.Deposit Contract>:(<Contract Identifier>= ='\''Y'\''",
    "ruleName": "RULE-003"
  }'
```

**Expected**: `valid: false` with SYNTAX_ERROR

---

## 🎓 Best Practices

### For Rule Authors

1. **Use exact data catalog names**: Copy names from catalog to avoid typos
2. **Start simple**: Test single conditions before combining
3. **Validate early**: Check rules before writing full business logic
4. **Use descriptive rule names**: Make rules traceable

### For Developers

1. **Keep catalog updated**: Sync with database schema changes
2. **Version catalog**: Track changes to data definitions
3. **Monitor validation errors**: Track common mistakes
4. **Cache catalog**: Load once at startup for performance

### For Operations

1. **Validate in CI/CD**: Fail builds on invalid rules
2. **Log validation results**: Track rule quality metrics
3. **Monitor API performance**: Track validation times
4. **Backup catalog**: Version control data-catalog.json

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
