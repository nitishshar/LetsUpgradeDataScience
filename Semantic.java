import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**

- Business Rule Similarity Engine — single file, zero dependencies, Java 11+
- 
- PURPOSE:
- Before a user saves a new business rule, check whether a semantically
- equivalent rule already exists in the catalogue — even if it is worded
- completely differently.
- 
- INPUT FORMAT (JSON array, id + rule text only):
- [
- ```
  { "id": "DQ-001", "rule": "Customer email address must not be null" },
  ```
- ```
  { "id": "DQ-002", "rule": "The email field for a customer cannot be empty" }
  ```
- ]
- 
- HOW SIMILARITY WORKS:
- 1. SemanticPreprocessor   — expands DQ synonyms (“must not be null” →
- ```
                            "cannot be empty" → same canonical tokens),
  ```
- ```
                            strips stop-words, applies basic stemming
  ```
- 1. TfIdfVectoriser        — converts each rule into a weighted vector
- ```
                            (rare/specific terms score higher than common ones)
  ```
- 1. CosineSimilarity       — measures the angle between two vectors;
- ```
                            same meaning = vectors point in same direction
  ```
- 
- TO COMPILE AND RUN:
- javac RuleSimilarityEngine.java
- java  RuleSimilarityEngine
  */
  public class RuleSimilarityEngine {

```
// =========================================================================
// MAIN — runs all four example scenarios
// =========================================================================

public static void main(String[] args) {
    BusinessRuleJsonParser parser = new BusinessRuleJsonParser();

    scenario1_NullRules(parser);
    scenario2_FormatRules(parser);
    scenario3_MixedCatalogue(parser);
    scenario4_RealTimeNewRuleCheck(parser);
}

// =========================================================================
// SCENARIO 1 — Null / Completeness rules
//
// The same "field must not be null" intent written in many different ways
// by different teams. The engine must surface them as near-duplicates even
// though the sentences share almost no words.
//
// Input JSON — only id and rule text, nothing else:
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
    System.out.println("  Goal: detect rules with same 'must not be null' intent written differently\n");

    List<BusinessRule> rules = parser.parse(json);
    printRules(rules);

    Engine engine = new Engine(0.65);
    engine.fit(rules);
    printMatches(engine.findSimilar(rules), engine.threshold);
}

// =========================================================================
// SCENARIO 2 — Format / Pattern Validation rules
//
// Format checks (postcode, phone, date) registered independently by
// multiple teams for the same logical field.
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

    Engine engine = new Engine(0.60);
    engine.fit(rules);
    printMatches(engine.findSimilar(rules), engine.threshold);
}

// =========================================================================
// SCENARIO 3 — Mixed Cross-Domain Catalogue Scan
//
// A realistic enterprise DQ catalogue: referential integrity, range checks,
// uniqueness, null checks — all mixed together across multiple domains.
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
    System.out.println("  Goal: find all near-duplicate rules across a mixed enterprise catalogue\n");

    List<BusinessRule> rules = parser.parse(json);
    printRules(rules);

    Engine engine = new Engine(0.60);
    engine.fit(rules);
    List<SimilarityResult> matches = engine.findSimilar(rules);
    printMatches(matches, engine.threshold);

    int totalPairs = rules.size() * (rules.size() - 1) / 2;
    System.out.println("  ── Summary ──");
    System.out.printf("  Rules in catalogue  : %d%n", rules.size());
    System.out.printf("  Pairs evaluated     : %d%n", totalPairs);
    System.out.printf("  Similar pairs found : %d  (threshold >= %.0f%%)%n",
        matches.size(), engine.threshold * 100);
    if (!matches.isEmpty()) {
        double avg = matches.stream().mapToDouble(r -> r.score).average().orElse(0);
        System.out.printf("  Average score       : %.1f%%%n", avg * 100);
        System.out.printf("  Top match           : %s vs %s at %s%n",
            matches.get(0).ruleA.id, matches.get(0).ruleB.id, matches.get(0).asPercent());
    }
    System.out.println();
}

// =========================================================================
// SCENARIO 4 — Real-time Duplicate Check (Primary Production Use Case)
//
// A user is about to create a new rule. Before saving, check it against
// the existing catalogue. Warn if a semantically equivalent rule exists —
// even if it is phrased completely differently.
//
// In production: fit() is called once at startup (or when catalogue changes).
// findSimilarTo() is called on every user submission — it is fast.
// =========================================================================
static void scenario4_RealTimeNewRuleCheck(BusinessRuleJsonParser parser) {
    printHeader("SCENARIO 4 — Real-time Duplicate Check (Primary Use Case)");
    System.out.println("  A user is about to create a new rule.");
    System.out.println("  The engine checks it against the existing catalogue before saving.\n");

    // Existing catalogue in the system
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

    // Fit once on the existing catalogue
    Engine engine = new Engine(0.55);
    engine.fit(catalogue);

    // New rules the user is trying to create
    List<BusinessRule> newRules = Arrays.asList(
        new BusinessRule("NEW-001", "Each order must have a customer that exists in the system"),
        new BusinessRule("NEW-002", "The transaction value must not be zero or negative"),
        new BusinessRule("NEW-003", "Supplier contact email must not be null")
    );

    for (BusinessRule newRule : newRules) {
        System.out.printf("  User wants to create:%n    [%s]  %s%n%n", newRule.id, newRule.rule);

        List<SimilarityResult> warnings = engine.findSimilarTo(newRule, catalogue);

        if (warnings.isEmpty()) {
            System.out.println("  ✓  No similar rules found. Safe to create.\n");
        } else {
            System.out.printf("  ⚠  WARNING: %d similar rule(s) already exist in the catalogue:%n%n",
                warnings.size());
            for (SimilarityResult w : warnings) {
                System.out.printf("       %s match  →  [%s] %s%n",
                    w.asPercent(), w.ruleB.id, w.ruleB.rule);
            }
            System.out.println();
        }
        System.out.println("  " + "-".repeat(68));
        System.out.println();
    }
}

// =========================================================================
// DOMAIN MODEL
// =========================================================================

/** A business rule as it arrives from JSON: only id and rule text. */
static class BusinessRule {
    final String id;
    final String rule;

    BusinessRule(String id, String rule) {
        if (id   == null || id.isBlank())   throw new IllegalArgumentException("id must not be blank");
        if (rule == null || rule.isBlank()) throw new IllegalArgumentException("rule must not be blank");
        this.id   = id.trim();
        this.rule = rule.trim();
    }
}

/** Similarity score between two rules — cosine similarity of their TF-IDF vectors. */
static class SimilarityResult implements Comparable<SimilarityResult> {
    final BusinessRule ruleA;
    final BusinessRule ruleB;
    final double       score; // 0.0 = unrelated, 1.0 = identical meaning

    SimilarityResult(BusinessRule ruleA, BusinessRule ruleB, double score) {
        this.ruleA = ruleA;
        this.ruleB = ruleB;
        this.score = Math.max(0.0, Math.min(1.0, score));
    }

    String asPercent() { return String.format("%.1f%%", score * 100); }

    @Override public int compareTo(SimilarityResult o) {
        return Double.compare(o.score, this.score); // descending
    }
}

// =========================================================================
// PARSER — converts JSON array to List<BusinessRule>, zero dependencies
// =========================================================================

static class BusinessRuleJsonParser {

    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}", Pattern.DOTALL);
    private static final Pattern FIELD  = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    List<BusinessRule> parse(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        List<BusinessRule> rules = new ArrayList<>();
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher f = FIELD.matcher(m.group(1));
            while (f.find()) fields.put(f.group(1), f.group(2));

            String id   = fields.get("id");
            String rule = fields.get("rule");
            if (id   == null || id.isBlank())   throw new IllegalArgumentException("Rule object missing \"id\"");
            if (rule == null || rule.isBlank()) throw new IllegalArgumentException("Rule [" + id + "] missing \"rule\"");
            rules.add(new BusinessRule(id, rule));
        }
        return rules;
    }
}

// =========================================================================
// SEMANTIC PREPROCESSOR
// Converts raw rule text into normalised tokens.
//
// Key step: synonym expansion maps the many ways humans express the same
// DQ concept onto a single canonical set of tokens before vectorisation.
// This is what makes "must not be null" and "cannot be empty" score high.
// =========================================================================

static class SemanticPreprocessor {

    // Synonym map — longest phrases first so multi-word matches win over subsets
    private static final List<String[]> SYNONYMS = Arrays.asList(
        // Null / completeness
        new String[]{"must not be null",          "null empty required"},
        new String[]{"should not be null",         "null empty required"},
        new String[]{"cannot be null",             "null empty required"},
        new String[]{"must not be empty",          "null empty required"},
        new String[]{"should not be empty",        "null empty required"},
        new String[]{"cannot be empty",            "null empty required"},
        new String[]{"must not be blank",          "null empty required"},
        new String[]{"cannot be blank",            "null empty required"},
        new String[]{"must be present",            "null empty required"},
        new String[]{"must be populated",          "null empty required"},
        new String[]{"must be provided",           "null empty required"},
        new String[]{"is required",                "null empty required"},
        new String[]{"must have a value",          "null empty required"},
        // Format / pattern
        new String[]{"must match the format",      "format pattern valid"},
        new String[]{"must conform to",            "format pattern valid"},
        new String[]{"must follow the format",     "format pattern valid"},
        new String[]{"must be in the format",      "format pattern valid"},
        new String[]{"must be formatted as",       "format pattern valid"},
        new String[]{"must be a valid format",     "format pattern valid"},
        new String[]{"must follow valid",          "format pattern valid"},
        new String[]{"formatting rules",           "format pattern valid"},
        new String[]{"regex pattern",              "format pattern valid"},
        new String[]{"must be valid",              "format valid"},
        // Range / bounds
        new String[]{"must be greater than zero",  "range positive value"},
        new String[]{"must be positive",           "range positive value"},
        new String[]{"must be a positive value",   "range positive value"},
        new String[]{"must not be zero or negative","range positive value"},
        new String[]{"must fall within",           "range bounds valid"},
        new String[]{"must be within",             "range bounds valid"},
        new String[]{"must be between",            "range bounds valid"},
        new String[]{"acceptable range",           "range bounds valid"},
        new String[]{"valid age range",            "range bounds valid"},
        // Uniqueness
        new String[]{"must be unique",             "unique duplicate"},
        new String[]{"must be uniquely",           "unique duplicate"},
        new String[]{"no two records",             "unique duplicate"},
        new String[]{"no duplicate",               "unique duplicate"},
        new String[]{"cannot be duplicated",       "unique duplicate"},
        // Referential integrity
        new String[]{"must exist in",              "referential integrity exists"},
        new String[]{"must reference a valid",     "referential integrity exists"},
        new String[]{"must be linked to a valid",  "referential integrity exists"},
        new String[]{"must have a customer",       "referential integrity exists"},
        new String[]{"must reference",             "referential integrity"},
        // Field synonyms
        new String[]{"date of birth",              "dob birthdate"},
        new String[]{"birth date",                 "dob birthdate"},
        new String[]{"email address",              "email"},
        new String[]{"phone number",               "phone"},
        new String[]{"mobile number",              "phone mobile"},
        new String[]{"mobile phone",               "phone mobile"},
        new String[]{"customer id",                "customer identifier"},
        new String[]{"customer identifier",        "customer identifier"},
        new String[]{"product id",                 "product identifier"},
        new String[]{"sku code",                   "sku identifier"},
        new String[]{"iso 8601",                   "iso date format"},
        new String[]{"e.164",                      "phone format international"},
        new String[]{"ean-13",                     "barcode format"},
        new String[]{"ean13",                      "barcode format"}
    );

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "to", "of", "and", "or", "in", "on", "at", "for", "with", "by", "from",
        "as", "into", "that", "this", "it", "its", "must", "should", "shall",
        "will", "would", "may", "can", "not", "no", "only", "also", "if",
        "when", "than", "then", "so", "do", "does", "did", "have", "has", "had",
        "every", "each", "all", "any", "some", "same", "given", "always", "never",
        "left", "across", "two", "zero"
    ));

    List<String> normalise(String text) {
        String t = text.toLowerCase().trim();
        for (String[] s : SYNONYMS) t = t.replace(s[0], s[1]);

        List<String> tokens = new ArrayList<>();
        for (String tok : t.split("[^a-z0-9]+")) {
            String w = tok.trim();
            if (w.length() < 2 || STOP_WORDS.contains(w)) continue;
            tokens.add(stem(w));
        }
        return tokens;
    }

    // Lightweight suffix stemmer — no library needed
    private String stem(String w) {
        if (w.endsWith("esses") && w.length() > 5) return w.substring(0, w.length() - 2);
        if (w.endsWith("ies")   && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        if (w.endsWith("ness")  && w.length() > 5) return w.substring(0, w.length() - 4);
        if (w.endsWith("ing")   && w.length() > 5) return w.substring(0, w.length() - 3);
        if (w.endsWith("tion")  && w.length() > 5) return w.substring(0, w.length() - 4);
        if (w.endsWith("ated")  && w.length() > 5) return w.substring(0, w.length() - 2);
        if (w.endsWith("ed")    && w.length() > 4) return w.substring(0, w.length() - 2);
        if (w.endsWith("er")    && w.length() > 4) return w.substring(0, w.length() - 2);
        if (w.endsWith("s")     && w.length() > 3 && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}

// =========================================================================
// TF-IDF VECTORISER
// Converts rule text into a weighted numeric vector.
//
// TF  (term frequency)     = how often a term appears in this rule
// IDF (inverse doc freq)   = how rare the term is across all rules
//                            — penalises terms that appear in every rule
// TF-IDF = TF × IDF        — high score for terms that are specific to this rule
// =========================================================================

static class TfIdfVectoriser {

    private final SemanticPreprocessor preprocessor = new SemanticPreprocessor();
    private Map<String, Integer> vocabulary;
    private double[]             idfWeights;

    /** Phase 1: build vocabulary + IDF weights from the rule corpus. */
    void fit(List<BusinessRule> rules) {
        List<List<String>> docs = new ArrayList<>();
        for (BusinessRule r : rules) docs.add(preprocessor.normalise(r.rule));

        // Build vocabulary
        Set<String> allTerms = new LinkedHashSet<>();
        for (List<String> d : docs) allTerms.addAll(d);
        vocabulary = new LinkedHashMap<>();
        int i = 0;
        for (String t : allTerms) vocabulary.put(t, i++);

        // Compute smoothed IDF: log((N+1)/(df+1)) + 1
        double[] df = new double[vocabulary.size()];
        for (List<String> d : docs) {
            for (String t : new HashSet<>(d)) {
                Integer idx = vocabulary.get(t);
                if (idx != null) df[idx]++;
            }
        }
        idfWeights = new double[vocabulary.size()];
        for (int j = 0; j < idfWeights.length; j++) {
            idfWeights[j] = Math.log((rules.size() + 1.0) / (df[j] + 1.0)) + 1.0;
        }
    }

    /** Phase 2: convert a rule text into its TF-IDF vector. */
    double[] vectorise(String ruleText) {
        if (vocabulary == null) throw new IllegalStateException("Call fit() first");
        List<String> tokens = preprocessor.normalise(ruleText);
        double[] vec = new double[vocabulary.size()];
        if (tokens.isEmpty()) return vec;

        Map<String, Integer> counts = new HashMap<>();
        for (String t : tokens) counts.merge(t, 1, Integer::sum);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Integer idx = vocabulary.get(e.getKey());
            if (idx == null) continue;
            vec[idx] = ((double) e.getValue() / tokens.size()) * idfWeights[idx];
        }
        return vec;
    }

    int vocabularySize() { return vocabulary == null ? 0 : vocabulary.size(); }
}

// =========================================================================
// COSINE SIMILARITY
// Measures the angle between two TF-IDF vectors.
// 1.0 = same direction = same meaning. 0.0 = orthogonal = unrelated.
// =========================================================================

static double cosineSimilarity(double[] a, double[] b) {
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
        dot   += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

// =========================================================================
// ENGINE — orchestrates fit, vectorise, compare, threshold filtering
// =========================================================================

static class Engine {

    final double threshold;
    private final TfIdfVectoriser vectoriser = new TfIdfVectoriser();
    private final Map<String, double[]> cache = new LinkedHashMap<>();

    Engine(double threshold) {
        if (threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Threshold must be 0.0–1.0");
        this.threshold = threshold;
    }

    /** Fit model on the existing catalogue and cache vectors. Must be called first. */
    void fit(List<BusinessRule> rules) {
        cache.clear();
        vectoriser.fit(rules);
        for (BusinessRule r : rules) cache.put(r.id, vectoriser.vectorise(r.rule));
    }

    /** Find all pairs in the catalogue that are semantically similar. */
    List<SimilarityResult> findSimilar(List<BusinessRule> rules) {
        assertFitted();
        List<SimilarityResult> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++)
            for (int j = i + 1; j < rules.size(); j++) {
                SimilarityResult r = score(rules.get(i), rules.get(j));
                if (r.score >= threshold) out.add(r);
            }
        Collections.sort(out);
        return out;
    }

    /**
     * Check a new rule against the existing catalogue.
     * The new rule does NOT need to have been in fit().
     */
    List<SimilarityResult> findSimilarTo(BusinessRule candidate, List<BusinessRule> catalogue) {
        assertFitted();
        double[] cv = vectoriser.vectorise(candidate.rule);
        List<SimilarityResult> out = new ArrayList<>();
        for (BusinessRule r : catalogue) {
            if (r.id.equals(candidate.id)) continue;
            double[] rv = cache.getOrDefault(r.id, vectoriser.vectorise(r.rule));
            double s = cosineSimilarity(cv, rv);
            if (s >= threshold) out.add(new SimilarityResult(candidate, r, s));
        }
        Collections.sort(out);
        return out;
    }

    private SimilarityResult score(BusinessRule a, BusinessRule b) {
        double[] va = cache.getOrDefault(a.id, vectoriser.vectorise(a.rule));
        double[] vb = cache.getOrDefault(b.id, vectoriser.vectorise(b.rule));
        return new SimilarityResult(a, b, cosineSimilarity(va, vb));
    }

    private void assertFitted() {
        if (vectoriser.vocabularySize() == 0)
            throw new IllegalStateException("Call fit() before running comparisons");
    }
}

// =========================================================================
// PRINT UTILITIES
// =========================================================================

private static void printHeader(String title) {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("  " + title);
    System.out.println("=".repeat(70));
}

private static void printRules(List<BusinessRule> rules) {
    System.out.printf("  %d rules loaded from JSON:%n", rules.size());
    for (BusinessRule r : rules)
        System.out.printf("    [%s]  %s%n", r.id, r.rule);
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
        System.out.printf("    [%s]  %s%n", r.ruleA.id, r.ruleA.rule);
        System.out.printf("    [%s]  %s%n", r.ruleB.id, r.ruleB.rule);
        int filled = (int) Math.round(r.score * 30);
        System.out.printf("    [%s%s]%n%n", "#".repeat(filled), "-".repeat(30 - filled));
    }
}
```

}
