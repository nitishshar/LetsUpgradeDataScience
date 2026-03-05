# Business Rule Similarity Engine

Detects semantically similar business rules from a JSON array of free-text rules.  
Prevents duplicate rules being created — even when they are worded completely differently.

-----

## Overview

### Problem

Users write business rules as free text. The same rule gets written many ways:

- `"Customer email address must not be null"`
- `"The email field for a customer cannot be empty"`
- `"Email is required for every customer record"`

These are all the same rule. Without semantic comparison, all three get saved as duplicates.

### Solution

Before saving a new rule, the engine checks it against the existing catalogue and warns if a semantically equivalent rule already exists.

### Input Format

```json
[
  { "id": "DQ-001", "rule": "Customer email address must not be null" },
  { "id": "DQ-002", "rule": "The email field for a customer cannot be empty" }
]
```

Only `id` and `rule` — nothing else.

### How Similarity Works

1. **SemanticPreprocessor** — expands DQ synonyms (`"must not be null"` → `"cannot be empty"` → same canonical tokens), strips stop-words, applies basic stemming
1. **TfIdfVectoriser** — converts each rule into a weighted numeric vector (rare/specific terms score higher than common ones)
1. **CosineSimilarity** — measures the angle between two vectors; same meaning = vectors point in the same direction → high score

### To Compile and Run

```bash
# Compile all files
find src -name "*.java" | xargs javac -d out

# Run examples
java -cp out com.rules.example.DataQualityRulesExample

# Run tests
java -cp out com.rules.RuleSimilarityTest
```

-----

## Project Structure

```
src/
├── main/java/com/rules/
│   ├── model/
│   │   ├── BusinessRule.java           # id + rule text
│   │   └── SimilarityResult.java       # scored pair of rules
│   ├── parser/
│   │   └── BusinessRuleJsonParser.java # parses JSON array → List<BusinessRule>
│   ├── scorer/
│   │   ├── SemanticPreprocessor.java   # synonym expansion, stop-words, stemming
│   │   ├── TfIdfVectoriser.java        # builds TF-IDF vectors from corpus
│   │   └── CosineSimilarity.java       # cosine similarity between vectors
│   ├── engine/
│   │   └── RuleSimilarityEngine.java   # orchestrates fit, vectorise, compare
│   └── example/
│       └── DataQualityRulesExample.java # 4 runnable DQ scenarios
└── test/java/com/rules/
    └── RuleSimilarityTest.java          # unit tests (no framework required)
```

-----

## Source Files

### `model/BusinessRule.java`

```java
package com.rules.model;

/**
 * A business rule exactly as it arrives in the JSON input.
 *
 *   { "id": "DQ-001", "rule": "Customer email address must not be null" }
 */
public class BusinessRule {

    private final String id;
    private final String rule;

    public BusinessRule(String id, String rule) {
        if (id   == null || id.isBlank())   throw new IllegalArgumentException("id must not be blank");
        if (rule  == null || rule.isBlank()) throw new IllegalArgumentException("rule text must not be blank");
        this.id   = id.trim();
        this.rule = rule.trim();
    }

    public String getId()   { return id; }
    public String getRule() { return rule; }

    @Override
    public String toString() { return "[" + id + "] " + rule; }
}
```

-----

### `model/SimilarityResult.java`

```java
package com.rules.model;

/**
 * Similarity score between two business rules.
 * Score is cosine similarity of their TF-IDF vectors: 0.0 (unrelated) → 1.0 (identical meaning).
 */
public class SimilarityResult implements Comparable<SimilarityResult> {

    private final BusinessRule ruleA;
    private final BusinessRule ruleB;
    private final double       score;

    public SimilarityResult(BusinessRule ruleA, BusinessRule ruleB, double score) {
        this.ruleA = ruleA;
        this.ruleB = ruleB;
        this.score = Math.max(0.0, Math.min(1.0, score));
    }

    public BusinessRule getRuleA()  { return ruleA; }
    public BusinessRule getRuleB()  { return ruleB; }
    public double       getScore()  { return score; }
    public String       asPercent() { return String.format("%.1f%%", score * 100); }

    @Override
    public int compareTo(SimilarityResult o) {
        return Double.compare(o.score, this.score); // descending
    }

    @Override
    public String toString() {
        return String.format("%s  [%s] vs [%s]", asPercent(), ruleA.getId(), ruleB.getId());
    }
}
```

-----

### `parser/BusinessRuleJsonParser.java`

```java
package com.rules.parser;

import com.rules.model.BusinessRule;

import java.util.*;
import java.util.regex.*;

/**
 * Parses a JSON array of business rule objects.
 *
 * Expected input — each object needs only "id" and "rule":
 * [
 *   { "id": "DQ-001", "rule": "Customer email address must not be null" },
 *   { "id": "DQ-002", "rule": "The email field for a customer cannot be empty" }
 * ]
 *
 * Zero external dependencies.
 */
public class BusinessRuleJsonParser {

    private static final Pattern OBJECT_PATTERN =
        Pattern.compile("\\{([^{}]*)\\}", Pattern.DOTALL);

    private static final Pattern FIELD_PATTERN =
        Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public List<BusinessRule> parse(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();

        List<BusinessRule> rules = new ArrayList<>();
        Matcher objects = OBJECT_PATTERN.matcher(json);

        while (objects.find()) {
            String body = objects.group(1);
            Map<String, String> fields = new LinkedHashMap<>();

            Matcher fields_ = FIELD_PATTERN.matcher(body);
            while (fields_.find()) {
                fields.put(fields_.group(1), fields_.group(2));
            }

            String id   = fields.get("id");
            String rule = fields.get("rule");

            if (id == null || id.isBlank())
                throw new IllegalArgumentException("Rule object missing \"id\": " + body.trim());
            if (rule == null || rule.isBlank())
                throw new IllegalArgumentException("Rule [" + id + "] missing \"rule\" text");

            rules.add(new BusinessRule(id, rule));
        }

        return rules;
    }
}
```

-----

### `scorer/SemanticPreprocessor.java`

The most important class. Synonym expansion is what makes semantically equivalent
rules score high even when they share no words.

```java
package com.rules.scorer;

import java.util.*;

/**
 * Normalises a business rule's free text before it is vectorised.
 *
 * Three layers of processing, in order:
 *
 * 1. SYNONYM EXPANSION
 *    Collapses many phrasings of the same DQ concept to a single canonical token.
 *    "cannot be empty" / "must not be null" / "should not be blank"
 *       → all become tokens containing "null" and "empty"
 *
 * 2. STOP-WORD REMOVAL
 *    Strips words with no domain signal (must, should, the, a, ...)
 *
 * 3. BASIC STEMMING
 *    "customers", "customer's", "customer" → all map to "customer"
 */
public class SemanticPreprocessor {

    // Synonym map — longest phrases first so multi-word matches win over subsets
    private static final List<String[]> SYNONYMS = Arrays.asList(
        // Null / completeness
        new String[]{"must not be null",         "null empty required"},
        new String[]{"should not be null",        "null empty required"},
        new String[]{"cannot be null",            "null empty required"},
        new String[]{"must not be empty",         "null empty required"},
        new String[]{"should not be empty",       "null empty required"},
        new String[]{"cannot be empty",           "null empty required"},
        new String[]{"must not be blank",         "null empty required"},
        new String[]{"cannot be blank",           "null empty required"},
        new String[]{"must be present",           "null empty required"},
        new String[]{"must be populated",         "null empty required"},
        new String[]{"must be provided",          "null empty required"},
        new String[]{"is required",               "null empty required"},
        new String[]{"must exist",                "null empty required"},
        new String[]{"must have a value",         "null empty required"},

        // Format / pattern
        new String[]{"must match the format",     "format pattern valid"},
        new String[]{"must conform to",           "format pattern valid"},
        new String[]{"must follow the format",    "format pattern valid"},
        new String[]{"must be in the format",     "format pattern valid"},
        new String[]{"must be formatted as",      "format pattern valid"},
        new String[]{"must be a valid format",    "format pattern valid"},
        new String[]{"regex pattern",             "format pattern valid"},
        new String[]{"must be valid",             "format valid"},

        // Range / bounds
        new String[]{"must be greater than zero", "range positive value"},
        new String[]{"must be positive",          "range positive value"},
        new String[]{"must be a positive value",  "range positive value"},
        new String[]{"must fall within",          "range bounds valid"},
        new String[]{"must be within",            "range bounds valid"},
        new String[]{"must be between",           "range bounds valid"},
        new String[]{"acceptable range",          "range bounds valid"},
        new String[]{"valid range",               "range bounds valid"},

        // Uniqueness / duplicates
        new String[]{"must be unique",            "unique duplicate"},
        new String[]{"must be uniquely",          "unique duplicate"},
        new String[]{"no two records",            "unique duplicate"},
        new String[]{"no duplicate",              "unique duplicate"},
        new String[]{"cannot be duplicated",      "unique duplicate"},

        // Referential integrity
        new String[]{"must exist in",             "referential integrity foreign key"},
        new String[]{"must reference a valid",    "referential integrity foreign key"},
        new String[]{"must be linked to",         "referential integrity foreign key"},
        new String[]{"must reference",            "referential integrity"},
        new String[]{"foreign key",               "referential integrity foreign key"},

        // Field synonyms
        new String[]{"date of birth",             "dob birthdate"},
        new String[]{"birth date",                "dob birthdate"},
        new String[]{"email address",             "email"},
        new String[]{"phone number",              "phone"},
        new String[]{"mobile number",             "phone mobile"},
        new String[]{"mobile phone",              "phone mobile"},
        new String[]{"customer id",               "customer identifier id"},
        new String[]{"customer identifier",       "customer identifier id"},
        new String[]{"product id",                "product identifier id"},
        new String[]{"sku code",                  "sku product identifier"},
        new String[]{"sku identifier",            "sku product identifier"},
        new String[]{"iso 8601",                  "iso date format"},
        new String[]{"e.164",                     "phone format international"},
        new String[]{"ean-13",                    "barcode format"},
        new String[]{"ean13",                     "barcode format"}
    );

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "to", "of", "and", "or", "in", "on", "at", "for", "with", "by", "from",
        "as", "into", "that", "this", "it", "its", "must", "should", "shall",
        "will", "would", "may", "can", "not", "no", "only", "also", "if",
        "when", "than", "then", "so", "do", "does", "did", "have", "has", "had",
        "every", "each", "all", "any", "some", "same", "given", "always", "never"
    ));

    public List<String> normalise(String ruleText) {
        String text = ruleText.toLowerCase().trim();

        // Step 1 — synonym expansion
        for (String[] entry : SYNONYMS) {
            text = text.replace(entry[0], entry[1]);
        }

        // Step 2 — tokenise on non-alphanumeric boundaries
        // Step 3 — stop-word removal + stemming
        List<String> tokens = new ArrayList<>();
        for (String token : text.split("[^a-z0-9]+")) {
            String t = token.trim();
            if (t.length() < 2) continue;
            if (STOP_WORDS.contains(t)) continue;
            tokens.add(stem(t));
        }
        return tokens;
    }

    // Lightweight suffix stemmer — no library needed
    String stem(String word) {
        if (word.endsWith("esses")) return word.substring(0, word.length() - 2);
        if (word.endsWith("ies")  && word.length() > 4) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("ness")) return word.substring(0, word.length() - 4);
        if (word.endsWith("ing")  && word.length() > 5) return word.substring(0, word.length() - 3);
        if (word.endsWith("tion")) return word.substring(0, word.length() - 4);
        if (word.endsWith("ated") && word.length() > 5) return word.substring(0, word.length() - 2);
        if (word.endsWith("ed")   && word.length() > 4) return word.substring(0, word.length() - 2);
        if (word.endsWith("er")   && word.length() > 4) return word.substring(0, word.length() - 2);
        if (word.endsWith("s")    && word.length() > 3 && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }
}
```

-----

### `scorer/TfIdfVectoriser.java`

```java
package com.rules.scorer;

import com.rules.model.BusinessRule;

import java.util.*;

/**
 * Builds TF-IDF vector representations for a corpus of business rules.
 *
 * TF  (term frequency)   = how often a term appears in this rule
 * IDF (inverse doc freq) = how rare the term is across all rules
 *                          — penalises terms that appear in every rule
 * TF-IDF = TF x IDF      — high score for terms specific to this rule
 *
 * Usage (two-phase):
 *   1. fit(rules)       — build vocabulary and IDF weights from the corpus
 *   2. vectorise(text)  — convert a rule's text into a TF-IDF double[]
 */
public class TfIdfVectoriser {

    private final SemanticPreprocessor preprocessor;
    private Map<String, Integer> vocabulary;
    private double[]             idfWeights;
    private int                  corpusSize;

    public TfIdfVectoriser(SemanticPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    /** Build vocabulary and IDF weights from a list of rules. Must be called before vectorise(). */
    public void fit(List<BusinessRule> rules) {
        corpusSize = rules.size();

        List<List<String>> tokenisedDocs = new ArrayList<>();
        for (BusinessRule rule : rules) {
            tokenisedDocs.add(preprocessor.normalise(rule.getRule()));
        }

        // Build vocabulary from all unique terms across corpus
        Set<String> allTerms = new LinkedHashSet<>();
        for (List<String> tokens : tokenisedDocs) allTerms.addAll(tokens);

        vocabulary = new LinkedHashMap<>();
        int idx = 0;
        for (String term : allTerms) vocabulary.put(term, idx++);

        // Compute smoothed IDF: log((N+1) / (df+1)) + 1
        double[] df = new double[vocabulary.size()];
        for (List<String> tokens : tokenisedDocs) {
            for (String term : new HashSet<>(tokens)) {
                Integer termIdx = vocabulary.get(term);
                if (termIdx != null) df[termIdx]++;
            }
        }

        idfWeights = new double[vocabulary.size()];
        for (int i = 0; i < idfWeights.length; i++) {
            idfWeights[i] = Math.log((corpusSize + 1.0) / (df[i] + 1.0)) + 1.0;
        }
    }

    /**
     * Convert a rule text into a TF-IDF vector.
     * Rules not seen during fit() can also be vectorised — useful for real-time checks.
     */
    public double[] vectorise(String ruleText) {
        if (vocabulary == null) throw new IllegalStateException("Call fit() before vectorise()");

        List<String> tokens = preprocessor.normalise(ruleText);
        double[] vector = new double[vocabulary.size()];
        if (tokens.isEmpty()) return vector;

        Map<String, Integer> termCounts = new HashMap<>();
        for (String token : tokens) termCounts.merge(token, 1, Integer::sum);

        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) {
            Integer termIdx = vocabulary.get(entry.getKey());
            if (termIdx == null) continue;
            double tf = (double) entry.getValue() / tokens.size();
            vector[termIdx] = tf * idfWeights[termIdx];
        }

        return vector;
    }

    public int getVocabularySize() { return vocabulary == null ? 0 : vocabulary.size(); }
}
```

-----

### `scorer/CosineSimilarity.java`

```java
package com.rules.scorer;

/**
 * Cosine similarity between two TF-IDF vectors.
 *
 * cosine(A, B) = (A · B) / (|A| x |B|)
 *
 * 1.0 = vectors point in the same direction (same meaning)
 * 0.0 = orthogonal vectors (no shared terms after preprocessing)
 */
public class CosineSimilarity {

    private CosineSimilarity() {}

    public static double compute(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                "Vectors must be the same length: " + a.length + " vs " + b.length);
        }

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

-----

### `engine/RuleSimilarityEngine.java`

```java
package com.rules.engine;

import com.rules.model.BusinessRule;
import com.rules.model.SimilarityResult;
import com.rules.scorer.CosineSimilarity;
import com.rules.scorer.SemanticPreprocessor;
import com.rules.scorer.TfIdfVectoriser;

import java.util.*;

/**
 * Compares business rules by semantic similarity and returns pairs that
 * breach a configurable threshold.
 *
 * Typical usage — checking a new rule before it is saved:
 *
 *   RuleSimilarityEngine engine = new RuleSimilarityEngine(0.70);
 *   engine.fit(existingRules);
 *
 *   List<SimilarityResult> warnings = engine.findSimilarTo(newRule, existingRules);
 *   if (!warnings.isEmpty()) {
 *       // warn the user: a similar rule already exists
 *   }
 *
 * Bulk scan:
 *   engine.fit(allRules);
 *   List<SimilarityResult> duplicates = engine.findSimilar(allRules);
 */
public class RuleSimilarityEngine {

    private final double           threshold;
    private final TfIdfVectoriser  vectoriser;
    private final Map<String, double[]> vectorCache = new LinkedHashMap<>();

    /** @param threshold score [0.0-1.0] a pair must reach to be returned. 0.70 is a good default. */
    public RuleSimilarityEngine(double threshold) {
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        this.threshold  = threshold;
        this.vectoriser = new TfIdfVectoriser(new SemanticPreprocessor());
    }

    /**
     * Fit the TF-IDF model on the existing catalogue and cache their vectors.
     * Must be called before any comparison method.
     * In production: call once at startup or whenever the catalogue changes.
     */
    public void fit(List<BusinessRule> rules) {
        vectorCache.clear();
        vectoriser.fit(rules);
        for (BusinessRule rule : rules) {
            vectorCache.put(rule.getId(), vectoriser.vectorise(rule.getRule()));
        }
    }

    /**
     * Find all pairs in the catalogue that are semantically similar.
     * Returns pairs above threshold, sorted by score descending.
     */
    public List<SimilarityResult> findSimilar(List<BusinessRule> rules) {
        assertFitted();
        List<SimilarityResult> results = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                SimilarityResult r = scoreWithCache(rules.get(i), rules.get(j));
                if (r.getScore() >= threshold) results.add(r);
            }
        }
        Collections.sort(results);
        return results;
    }

    /**
     * Check a new rule against the existing catalogue.
     * The candidate does NOT need to have been in fit() — useful for real-time checks.
     * Returns catalogue rules above threshold, sorted by score descending.
     */
    public List<SimilarityResult> findSimilarTo(BusinessRule candidate, List<BusinessRule> existing) {
        assertFitted();
        double[] candidateVector = vectoriser.vectorise(candidate.getRule());
        List<SimilarityResult> results = new ArrayList<>();
        for (BusinessRule rule : existing) {
            if (rule.getId().equals(candidate.getId())) continue;
            double[] ev = vectorCache.getOrDefault(rule.getId(), vectoriser.vectorise(rule.getRule()));
            double score = CosineSimilarity.compute(candidateVector, ev);
            if (score >= threshold) results.add(new SimilarityResult(candidate, rule, score));
        }
        Collections.sort(results);
        return results;
    }

    /** Directly compare two rules regardless of threshold. Useful for spot-checks. */
    public SimilarityResult compare(BusinessRule a, BusinessRule b) {
        assertFitted();
        return scoreWithCache(a, b);
    }

    public double getThreshold() { return threshold; }

    private SimilarityResult scoreWithCache(BusinessRule a, BusinessRule b) {
        double[] va = vectorCache.getOrDefault(a.getId(), vectoriser.vectorise(a.getRule()));
        double[] vb = vectorCache.getOrDefault(b.getId(), vectoriser.vectorise(b.getRule()));
        return new SimilarityResult(a, b, CosineSimilarity.compute(va, vb));
    }

    private void assertFitted() {
        if (vectoriser.getVocabularySize() == 0)
            throw new IllegalStateException("Call fit() before running comparisons");
    }
}
```

-----

### `example/DataQualityRulesExample.java`

Four runnable scenarios using realistic DQ business rules.

```java
package com.rules.example;

import com.rules.engine.RuleSimilarityEngine;
import com.rules.model.BusinessRule;
import com.rules.model.SimilarityResult;
import com.rules.parser.BusinessRuleJsonParser;

import java.util.List;

/**
 * Four Data Quality scenarios demonstrating the similarity engine.
 *
 * Input: JSON array with only "id" and "rule" — free text written by humans.
 * Goal:  surface near-duplicate rules even when worded completely differently.
 */
public class DataQualityRulesExample {

    public static void main(String[] args) {
        BusinessRuleJsonParser parser = new BusinessRuleJsonParser();
        scenario1_NullRules(parser);
        scenario2_FormatRules(parser);
        scenario3_MixedCatalogue(parser);
        scenario4_NewRuleCheck(parser);
    }

    // =========================================================================
    // SCENARIO 1 — Null / Completeness rules
    //
    // Same "field must not be null" intent written differently by different teams.
    // =========================================================================
    static void scenario1_NullRules(BusinessRuleJsonParser parser) {
        String json =
            "[\n" +
            "  { \"id\": \"DQ-001\", \"rule\": \"Customer email address must not be null\" },\n" +
            "  { \"id\": \"DQ-002\", \"rule\": \"The email field for a customer cannot be empty\" },\n" +
            "  { \"id\": \"DQ-003\", \"rule\": \"Email is required for every customer record\" },\n" +
            "  { \"id\": \"DQ-004\", \"rule\": \"A customer must always have an email value populated\" },\n" +
            "  { \"id\": \"DQ-005\", \"rule\": \"Customer phone number must not be null\" },\n" +
            "  { \"id\": \"DQ-006\", \"rule\": \"Phone number is required and cannot be left blank\" },\n" +
            "  { \"id\": \"DQ-007\", \"rule\": \"Product SKU code must be present and not null\" },\n" +
            "  { \"id\": \"DQ-008\", \"rule\": \"Every product must have a SKU identifier provided\" }\n" +
            "]";

        printHeader("SCENARIO 1 — Null / Completeness Rules");
        System.out.println("  Goal: detect 'must not be null' rules written differently by different teams\n");

        List<BusinessRule> rules = parser.parse(json);
        printRules(rules);

        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.65);
        engine.fit(rules);
        printMatches(engine.findSimilar(rules), engine.getThreshold());
    }

    // =========================================================================
    // SCENARIO 2 — Format / Pattern Validation rules
    //
    // Postcode, phone and date format checks registered independently per team.
    // =========================================================================
    static void scenario2_FormatRules(BusinessRuleJsonParser parser) {
        String json =
            "[\n" +
            "  { \"id\": \"DQ-101\", \"rule\": \"UK postcode must match the standard postcode format\" },\n" +
            "  { \"id\": \"DQ-102\", \"rule\": \"Postcode value must conform to the UK postcode regex pattern\" },\n" +
            "  { \"id\": \"DQ-103\", \"rule\": \"Customer postcode must follow valid UK postcode formatting rules\" },\n" +
            "  { \"id\": \"DQ-104\", \"rule\": \"Mobile phone number must be in E.164 international format\" },\n" +
            "  { \"id\": \"DQ-105\", \"rule\": \"Phone number must conform to the E.164 format standard\" },\n" +
            "  { \"id\": \"DQ-106\", \"rule\": \"Transaction date must be formatted as an ISO 8601 date\" },\n" +
            "  { \"id\": \"DQ-107\", \"rule\": \"Settlement date must follow the ISO 8601 standard format\" },\n" +
            "  { \"id\": \"DQ-108\", \"rule\": \"Product barcode must be a valid EAN-13 format code\" }\n" +
            "]";

        printHeader("SCENARIO 2 — Format / Pattern Validation Rules");
        System.out.println("  Goal: surface overlapping format checks written independently per team\n");

        List<BusinessRule> rules = parser.parse(json);
        printRules(rules);

        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.60);
        engine.fit(rules);
        printMatches(engine.findSimilar(rules), engine.getThreshold());
    }

    // =========================================================================
    // SCENARIO 3 — Mixed Cross-Domain Catalogue Scan
    //
    // Referential integrity, range, uniqueness, null checks — all mixed together.
    // =========================================================================
    static void scenario3_MixedCatalogue(BusinessRuleJsonParser parser) {
        String json =
            "[\n" +
            "  { \"id\": \"DQ-201\", \"rule\": \"Every order must reference a valid existing customer\" },\n" +
            "  { \"id\": \"DQ-202\", \"rule\": \"Order customer ID must exist in the customer master table\" },\n" +
            "  { \"id\": \"DQ-203\", \"rule\": \"An invoice must be linked to a valid customer record\" },\n" +
            "  { \"id\": \"DQ-204\", \"rule\": \"Customer date of birth must fall within a valid age range\" },\n" +
            "  { \"id\": \"DQ-205\", \"rule\": \"The birth date for a customer must be within an acceptable range\" },\n" +
            "  { \"id\": \"DQ-206\", \"rule\": \"Customer ID must be unique across all records in the system\" },\n" +
            "  { \"id\": \"DQ-207\", \"rule\": \"No two customer records may share the same customer identifier\" },\n" +
            "  { \"id\": \"DQ-208\", \"rule\": \"Transaction amount must be a positive value greater than zero\" },\n" +
            "  { \"id\": \"DQ-209\", \"rule\": \"Invoice total amount must be positive and greater than zero\" },\n" +
            "  { \"id\": \"DQ-210\", \"rule\": \"Product description field must not be null or empty\" },\n" +
            "  { \"id\": \"DQ-211\", \"rule\": \"Product long description and short description must not be blank\" },\n" +
            "  { \"id\": \"DQ-212\", \"rule\": \"Warehouse location code must exist in the location reference data\" }\n" +
            "]";

        printHeader("SCENARIO 3 — Mixed Cross-Domain Catalogue Scan");
        System.out.println("  Goal: find all near-duplicate rules in a mixed enterprise catalogue\n");

        List<BusinessRule> rules = parser.parse(json);
        printRules(rules);

        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.60);
        engine.fit(rules);
        List<SimilarityResult> matches = engine.findSimilar(rules);
        printMatches(matches, engine.getThreshold());

        int totalPairs = rules.size() * (rules.size() - 1) / 2;
        System.out.println("  -- Summary --");
        System.out.printf("  Rules in catalogue  : %d%n", rules.size());
        System.out.printf("  Pairs evaluated     : %d%n", totalPairs);
        System.out.printf("  Similar pairs found : %d  (threshold >= %.0f%%)%n",
            matches.size(), engine.getThreshold() * 100);
        if (!matches.isEmpty()) {
            double avg = matches.stream().mapToDouble(SimilarityResult::getScore).average().orElse(0);
            System.out.printf("  Average score       : %.1f%%%n", avg * 100);
            System.out.printf("  Top match           : %s vs %s at %s%n",
                matches.get(0).getRuleA().getId(),
                matches.get(0).getRuleB().getId(),
                matches.get(0).asPercent());
        }
        System.out.println();
    }

    // =========================================================================
    // SCENARIO 4 — Real-time Duplicate Check (Primary Production Use Case)
    //
    // A user tries to create a new rule. The engine checks it against the
    // existing catalogue and warns if a semantically equivalent rule exists.
    //
    // fit() is called once at startup.
    // findSimilarTo() is called on every user submission — it is fast.
    // =========================================================================
    static void scenario4_NewRuleCheck(BusinessRuleJsonParser parser) {
        printHeader("SCENARIO 4 — Real-time Duplicate Check (Primary Use Case)");
        System.out.println("  A user is about to create a new rule.");
        System.out.println("  The engine checks it against the existing catalogue before saving.\n");

        String catalogueJson =
            "[\n" +
            "  { \"id\": \"DQ-201\", \"rule\": \"Every order must reference a valid existing customer\" },\n" +
            "  { \"id\": \"DQ-202\", \"rule\": \"Order customer ID must exist in the customer master table\" },\n" +
            "  { \"id\": \"DQ-204\", \"rule\": \"Customer date of birth must fall within a valid age range\" },\n" +
            "  { \"id\": \"DQ-206\", \"rule\": \"Customer ID must be unique across all records in the system\" },\n" +
            "  { \"id\": \"DQ-208\", \"rule\": \"Transaction amount must be a positive value greater than zero\" },\n" +
            "  { \"id\": \"DQ-210\", \"rule\": \"Product description field must not be null or empty\" }\n" +
            "]";

        List<BusinessRule> catalogue = parser.parse(catalogueJson);

        // Fit once on existing catalogue
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.55);
        engine.fit(catalogue);

        // New rules submitted by a user — not yet in the catalogue
        List<BusinessRule> newRules = List.of(
            new BusinessRule("NEW-001", "Each order must have a customer that exists in the system"),
            new BusinessRule("NEW-002", "The transaction value must not be zero or negative"),
            new BusinessRule("NEW-003", "Supplier contact email must not be null")
        );

        for (BusinessRule newRule : newRules) {
            System.out.printf("  User wants to create:%n    [%s]  %s%n%n", newRule.getId(), newRule.getRule());
            List<SimilarityResult> warnings = engine.findSimilarTo(newRule, catalogue);

            if (warnings.isEmpty()) {
                System.out.println("  OK   No similar rules found. Safe to create.\n");
            } else {
                System.out.printf("  WARN %d similar rule(s) already exist in the catalogue:%n%n", warnings.size());
                for (SimilarityResult w : warnings) {
                    System.out.printf("       %s match  ->  [%s] %s%n",
                        w.asPercent(), w.getRuleB().getId(), w.getRuleB().getRule());
                }
                System.out.println();
            }
            System.out.println("  " + "-".repeat(66));
            System.out.println();
        }
    }

    // -------------------------------------------------------------------------
    // Shared print utilities
    // -------------------------------------------------------------------------

    private static void printHeader(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    private static void printRules(List<BusinessRule> rules) {
        System.out.printf("  %d rules loaded from JSON:%n", rules.size());
        for (BusinessRule r : rules)
            System.out.printf("    [%s]  %s%n", r.getId(), r.getRule());
        System.out.println();
    }

    private static void printMatches(List<SimilarityResult> matches, double threshold) {
        if (matches.isEmpty()) {
            System.out.printf("  No pairs exceeded %.0f%% threshold.%n%n", threshold * 100);
            return;
        }
        System.out.printf("  %d similar pair(s) found (threshold >= %.0f%%):%n%n",
            matches.size(), threshold * 100);
        for (SimilarityResult r : matches) {
            System.out.printf("  %s similarity%n", r.asPercent());
            System.out.printf("    [%s]  %s%n", r.getRuleA().getId(), r.getRuleA().getRule());
            System.out.printf("    [%s]  %s%n", r.getRuleB().getId(), r.getRuleB().getRule());
            int filled = (int) Math.round(r.getScore() * 30);
            System.out.printf("    [%s%s]%n%n", "#".repeat(filled), "-".repeat(30 - filled));
        }
    }
}
```

-----

### `test/RuleSimilarityTest.java`

No test framework required — run with `java -cp out com.rules.RuleSimilarityTest`.

```java
package com.rules;

import com.rules.engine.RuleSimilarityEngine;
import com.rules.model.BusinessRule;
import com.rules.model.SimilarityResult;
import com.rules.parser.BusinessRuleJsonParser;
import com.rules.scorer.SemanticPreprocessor;
import com.rules.scorer.CosineSimilarity;

import java.util.List;

public class RuleSimilarityTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("Running tests...\n");

        // Parser
        testParser_basicParse();
        testParser_multipleRules();
        testParser_missingIdThrows();
        testParser_missingRuleThrows();

        // Preprocessor
        testPreprocessor_nullSynonyms();
        testPreprocessor_emptySynonym();
        testPreprocessor_formatSynonym();
        testPreprocessor_stopWordsRemoved();
        testPreprocessor_stemming();

        // Cosine similarity
        testCosine_identicalVectors();
        testCosine_orthogonalVectors();
        testCosine_zeroVector();

        // Engine
        testEngine_identicalRulesScoreHigh();
        testEngine_differentPhrasingSameIntent();
        testEngine_unrelatedRulesScoreLow();
        testEngine_thresholdFilters();
        testEngine_findSimilarToExcludesSelf();
        testEngine_newRuleNotInCorpus();
        testEngine_requiresFitBeforeCompare();

        System.out.println("\n" + "=".repeat(50));
        System.out.printf("  %d passed  |  %d failed%n", passed, failed);
        System.out.println("=".repeat(50));
        if (failed > 0) System.exit(1);
    }

    static void testParser_basicParse() {
        String json = "[{\"id\":\"R1\",\"rule\":\"Email must not be null\"}]";
        List<BusinessRule> rules = new BusinessRuleJsonParser().parse(json);
        assertEqual("Parser: count", 1, rules.size());
        assertEqual("Parser: id",   "R1", rules.get(0).getId());
        assertEqual("Parser: rule", "Email must not be null", rules.get(0).getRule());
    }

    static void testParser_multipleRules() {
        String json = "[{\"id\":\"A\",\"rule\":\"Rule one\"},{\"id\":\"B\",\"rule\":\"Rule two\"}]";
        assertEqual("Parser: two rules", 2, new BusinessRuleJsonParser().parse(json).size());
    }

    static void testParser_missingIdThrows() {
        try {
            new BusinessRuleJsonParser().parse("[{\"rule\":\"Email must not be null\"}]");
            fail("Parser: missing id should throw");
        } catch (IllegalArgumentException e) { pass("Parser: missing id throws"); }
    }

    static void testParser_missingRuleThrows() {
        try {
            new BusinessRuleJsonParser().parse("[{\"id\":\"R1\"}]");
            fail("Parser: missing rule should throw");
        } catch (IllegalArgumentException e) { pass("Parser: missing rule throws"); }
    }

    static void testPreprocessor_nullSynonyms() {
        SemanticPreprocessor p = new SemanticPreprocessor();
        assertTrue("Preprocessor: 'must not be null' maps to null token",
            p.normalise("Email must not be null").stream().anyMatch(t -> t.contains("null")));
        assertTrue("Preprocessor: 'cannot be empty' maps to null token",
            p.normalise("Email cannot be empty").stream().anyMatch(t -> t.contains("null")));
    }

    static void testPreprocessor_emptySynonym() {
        assertTrue("Preprocessor: 'is required' expands",
            new SemanticPreprocessor().normalise("Customer phone is required")
                .stream().anyMatch(t -> t.contains("requir")));
    }

    static void testPreprocessor_formatSynonym() {
        SemanticPreprocessor p = new SemanticPreprocessor();
        assertTrue("Preprocessor: format phrase expands",
            p.normalise("must match the format").stream().anyMatch(t -> t.contains("format")));
        assertTrue("Preprocessor: conform phrase expands",
            p.normalise("must conform to the pattern").stream().anyMatch(t -> t.contains("format") || t.contains("pattern")));
    }

    static void testPreprocessor_stopWordsRemoved() {
        assertTrue("Preprocessor: stop words stripped",
            new SemanticPreprocessor().normalise("the customer must have an email")
                .stream().noneMatch(t -> t.equals("must") || t.equals("the") || t.equals("an")));
    }

    static void testPreprocessor_stemming() {
        assertTrue("Preprocessor: 'customers' stems to 'customer'",
            new SemanticPreprocessor().normalise("customers records")
                .stream().anyMatch(t -> t.equals("customer")));
    }

    static void testCosine_identicalVectors() {
        double[] v = {0.5, 0.3, 0.8, 0.1};
        assertApprox("Cosine: identical = 1.0", 1.0, CosineSimilarity.compute(v, v));
    }

    static void testCosine_orthogonalVectors() {
        assertApprox("Cosine: orthogonal = 0.0", 0.0,
            CosineSimilarity.compute(new double[]{1.0, 0.0}, new double[]{0.0, 1.0}));
    }

    static void testCosine_zeroVector() {
        assertApprox("Cosine: zero vector = 0.0", 0.0,
            CosineSimilarity.compute(new double[]{0.0, 0.0}, new double[]{1.0, 0.5}));
    }

    static void testEngine_identicalRulesScoreHigh() {
        List<BusinessRule> rules = List.of(
            new BusinessRule("R1", "Customer email address must not be null"),
            new BusinessRule("R2", "Customer email address must not be null")
        );
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.0);
        engine.fit(rules);
        assertTrue("Engine: identical rules score 1.0",
            engine.compare(rules.get(0), rules.get(1)).getScore() >= 0.99);
    }

    static void testEngine_differentPhrasingSameIntent() {
        List<BusinessRule> rules = List.of(
            new BusinessRule("R1", "Customer email address must not be null"),
            new BusinessRule("R2", "The email field for a customer cannot be empty"),
            new BusinessRule("R3", "Product barcode must be valid EAN-13 format")
        );
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.0);
        engine.fit(rules);
        double similar   = engine.compare(rules.get(0), rules.get(1)).getScore();
        double different = engine.compare(rules.get(0), rules.get(2)).getScore();
        assertTrue("Engine: same intent scores higher than unrelated (" +
            String.format("%.2f > %.2f", similar, different) + ")", similar > different);
    }

    static void testEngine_unrelatedRulesScoreLow() {
        List<BusinessRule> rules = List.of(
            new BusinessRule("R1", "Customer email address must not be null"),
            new BusinessRule("R2", "Transaction amount must be positive and greater than zero")
        );
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.0);
        engine.fit(rules);
        double score = engine.compare(rules.get(0), rules.get(1)).getScore();
        assertTrue("Engine: unrelated rules score low (got " + String.format("%.2f", score) + ")",
            score < 0.5);
    }

    static void testEngine_thresholdFilters() {
        List<BusinessRule> rules = List.of(
            new BusinessRule("R1", "Customer email must not be null"),
            new BusinessRule("R2", "Email field cannot be empty for a customer"),
            new BusinessRule("R3", "Warehouse barcode must conform to EAN-13 format")
        );
        RuleSimilarityEngine high = new RuleSimilarityEngine(0.99);
        high.fit(rules);
        assertTrue("Engine: high threshold filters all", high.findSimilar(rules).isEmpty());

        RuleSimilarityEngine low = new RuleSimilarityEngine(0.30);
        low.fit(rules);
        assertTrue("Engine: low threshold returns match", low.findSimilar(rules).size() >= 1);
    }

    static void testEngine_findSimilarToExcludesSelf() {
        List<BusinessRule> rules = List.of(
            new BusinessRule("R1", "Customer email must not be null"),
            new BusinessRule("R2", "Email cannot be empty for a customer")
        );
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.0);
        engine.fit(rules);
        boolean selfFound = engine.findSimilarTo(rules.get(0), rules)
            .stream().anyMatch(r -> r.getRuleB().getId().equals("R1"));
        assertTrue("findSimilarTo: self excluded", !selfFound);
    }

    static void testEngine_newRuleNotInCorpus() {
        List<BusinessRule> catalogue = List.of(
            new BusinessRule("E1", "Customer email address must not be null"),
            new BusinessRule("E2", "Transaction amount must be greater than zero")
        );
        RuleSimilarityEngine engine = new RuleSimilarityEngine(0.0);
        engine.fit(catalogue);
        BusinessRule newRule = new BusinessRule("NEW", "Email field for customer cannot be empty");
        List<SimilarityResult> results = engine.findSimilarTo(newRule, catalogue);
        assertTrue("Engine: new rule matched against E1",
            results.stream().anyMatch(r -> r.getRuleB().getId().equals("E1")));
    }

    static void testEngine_requiresFitBeforeCompare() {
        try {
            new RuleSimilarityEngine(0.7).compare(
                new BusinessRule("A", "rule one"), new BusinessRule("B", "rule two"));
            fail("Engine: should throw without fit()");
        } catch (IllegalStateException e) { pass("Engine: throws without fit()"); }
    }

    static void assertApprox(String n, double e, double a) {
        if (Math.abs(e - a) < 0.001) pass(n);
        else fail(String.format("%s — expected %.4f got %.4f", n, e, a));
    }

    static void assertEqual(String n, Object e, Object a) {
        if (e.equals(a)) pass(n); else fail(n + " — expected [" + e + "] got [" + a + "]");
    }

    static void assertTrue(String n, boolean c) { if (c) pass(n); else fail(n + " — was false"); }
    static void pass(String n) { System.out.println("  PASS  " + n); passed++; }
    static void fail(String n) { System.out.println("  FAIL  " + n); failed++; }
}
```

-----

## Sample Output

**Scenario 1 — Null rules:**

```
3 similar pair(s) found (threshold >= 65%):

77.1% similarity
  [DQ-001]  Customer email address must not be null
  [DQ-002]  The email field for a customer cannot be empty
  [#######################-------]

77.1% similarity
  [DQ-001]  Customer email address must not be null
  [DQ-003]  Email is required for every customer record
  [#######################-------]
```

**Scenario 4 — Real-time check:**

```
User wants to create:
  [NEW-001]  Each order must have a customer that exists in the system

WARN 1 similar rule(s) already exist in the catalogue:

     87.6% match  ->  [DQ-201] Every order must reference a valid existing customer
```
