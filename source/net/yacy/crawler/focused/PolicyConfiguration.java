// JSON configuration for focused autocrawler profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Validated, immutable representation of one focused autocrawler profile. */
public final class PolicyConfiguration {

    public static final class ScoringRule {
        private final String id;
        private final int score;
        private final List<String> terms;
        private final List<String> hosts;
        private final List<String> tlds;
        private final String collection;
        private final int minimumTermMatches;

        private ScoringRule(final JSONObject json) {
            this.id = json.optString("id", "rule");
            this.score = json.optInt("score", 0);
            this.terms = strings(json.opt("terms"));
            this.hosts = strings(json.opt("hosts"));
            this.tlds = strings(json.opt("tlds"));
            this.collection = json.optString("collection", "");
            this.minimumTermMatches = Math.max(1, json.optInt("minimumTermMatches", 1));
        }

        public String id() { return this.id; }
        public int score() { return this.score; }
        public List<String> terms() { return this.terms; }
        public List<String> hosts() { return this.hosts; }
        public List<String> tlds() { return this.tlds; }
        public String collection() { return this.collection; }
        public int minimumTermMatches() { return this.minimumTermMatches; }

        private JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("id", this.id).put("score", this.score).put("terms", new JSONArray(this.terms))
                        .put("hosts", new JSONArray(this.hosts)).put("tlds", new JSONArray(this.tlds))
                        .put("minimumTermMatches", this.minimumTermMatches);
                if (!this.collection.isEmpty()) json.put("collection", this.collection);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize scoring rule " + this.id, e);
            }
            return json;
        }
    }

    public static final class CollectionRule {
        private final String id;
        private final List<String> terms;
        private final List<String> sourceTypes;
        private final long recrawlIntervalMillis;
        private final int minimumTermMatches;

        private CollectionRule(final String id, final JSONObject json, final long defaultRecrawl) {
            this.id = id;
            this.terms = strings(json.opt("terms"));
            this.sourceTypes = strings(json.opt("sourceTypes"));
            this.recrawlIntervalMillis = Math.max(0L, json.optLong("recrawlMinutes", defaultRecrawl / 60000L)) * 60000L;
            this.minimumTermMatches = Math.max(1, json.optInt("minimumTermMatches", 1));
        }

        public String id() { return this.id; }
        public List<String> terms() { return this.terms; }
        public List<String> sourceTypes() { return this.sourceTypes; }
        public long recrawlIntervalMillis() { return this.recrawlIntervalMillis; }
        public int minimumTermMatches() { return this.minimumTermMatches; }

        private JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("terms", new JSONArray(this.terms)).put("sourceTypes", new JSONArray(this.sourceTypes))
                        .put("minimumTermMatches", this.minimumTermMatches);
                json.put("recrawlMinutes", this.recrawlIntervalMillis / 60000L);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize collection rule " + this.id, e);
            }
            return json;
        }
    }

    public static final class Limits {
        public static final int DEFAULT_REFILL_BATCH_SIZE = 10000;
        private static final int MAX_REFILL_BATCH_SIZE = 100000;
        private final int queueTarget;
        private final int refillBelow;
        private final int hardMaximum;
        private final int refillBatchSize;
        private final long storageTargetBytes;
        private final int explorationPercent;
        private final int maximumDepth;
        private final int perHostBudget;
        private final int seedBatchSize;
        private final boolean refreshSeeds;
        private final boolean recrawlFocused;
        private final long defaultRecrawlIntervalMillis;
        private final long controlIntervalMillis;

        private Limits(final JSONObject json) {
            this.queueTarget = Math.max(0, json.optInt("queueTarget", 0));
            this.refillBelow = Math.max(0, json.optInt("refillBelow", 0));
            this.hardMaximum = Math.max(0, json.optInt("hardMaximum", 0));
            this.refillBatchSize = Math.max(1, Math.min(MAX_REFILL_BATCH_SIZE,
                    json.optInt("refillBatchSize", DEFAULT_REFILL_BATCH_SIZE)));
            this.storageTargetBytes = Math.max(0L, json.optLong("storageTargetBytes", 0L));
            this.explorationPercent = Math.max(0, Math.min(100, json.optInt("explorationPercent", 10)));
            this.maximumDepth = Math.max(0, json.optInt("maximumDepth", 2));
            this.perHostBudget = Math.max(0, json.optInt("perHostBudget", 0));
            this.seedBatchSize = Math.max(0, json.optInt("seedBatchSize", 0));
            this.refreshSeeds = json.optBoolean("refreshSeeds", false);
            this.recrawlFocused = json.optBoolean("recrawlFocused", false);
            this.defaultRecrawlIntervalMillis = Math.max(0L, json.optLong("defaultRecrawlMinutes", 43200L)) * 60000L;
            this.controlIntervalMillis = Math.max(1000L, json.optLong("controlIntervalSeconds", 300L) * 1000L);
            validateQueueWatermarks();
        }

        private void validateQueueWatermarks() {
            if (this.refillBelow > this.queueTarget) {
                throw new IllegalArgumentException("focused policy limits require refillBelow <= queueTarget");
            }
            if (this.queueTarget > this.hardMaximum) {
                throw new IllegalArgumentException("focused policy limits require queueTarget <= hardMaximum");
            }
        }

        public int queueTarget() { return this.queueTarget; }
        public int refillBelow() { return this.refillBelow; }
        public int hardMaximum() { return this.hardMaximum; }
        /** Maximum frontier URLs admitted in one scheduling cycle. */
        public int refillBatchSize() { return this.refillBatchSize; }
        public long storageTargetBytes() { return this.storageTargetBytes; }
        public int explorationPercent() { return this.explorationPercent; }
        public int maximumDepth() { return this.maximumDepth; }
        public int perHostBudget() { return this.perHostBudget; }
        /**
         * Maximum number of configured roots to submit in one scheduler cycle.
         * A value of zero preserves the legacy behaviour of submitting all
         * roots when a refill is needed.
         */
        public int seedBatchSize() { return this.seedBatchSize; }
        public boolean refreshSeeds() { return this.refreshSeeds; }
        public boolean recrawlFocused() { return this.recrawlFocused; }
        public long defaultRecrawlIntervalMillis() { return this.defaultRecrawlIntervalMillis; }
        public long controlIntervalMillis() { return this.controlIntervalMillis; }

        private JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("queueTarget", this.queueTarget).put("refillBelow", this.refillBelow)
                        .put("hardMaximum", this.hardMaximum).put("refillBatchSize", this.refillBatchSize)
                        .put("storageTargetBytes", this.storageTargetBytes)
                        .put("explorationPercent", this.explorationPercent).put("maximumDepth", this.maximumDepth)
                        .put("perHostBudget", this.perHostBudget).put("seedBatchSize", this.seedBatchSize)
                        .put("refreshSeeds", this.refreshSeeds)
                        .put("recrawlFocused", this.recrawlFocused)
                        .put("defaultRecrawlMinutes", this.defaultRecrawlIntervalMillis / 60000L)
                        .put("controlIntervalSeconds", this.controlIntervalMillis / 1000L);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize focused policy limits", e);
            }
            return json;
        }
    }

    /** Persistent frontier and seed-recovery behavior for one focused profile. */
    public static final class Frontier {
        private final boolean enabled;
        private final String seedMode;
        private final long recoveryGraceMillis;
        private final int maxEntries;
        private final int terminalRetentionDays;
        private final boolean expandIndexedSources;
        private final int indexedSourceBatch;

        private Frontier(final JSONObject json) {
            this.enabled = json.optBoolean("enabled", false);
            final String configuredMode = json.optString("seedMode", "initial-only").trim().toLowerCase(Locale.ROOT);
            this.seedMode = "disabled".equals(configuredMode) || "exhausted".equals(configuredMode)
                    ? configuredMode : "initial-only";
            this.recoveryGraceMillis = Math.max(30000L, json.optLong("recoveryGraceSeconds", 600L) * 1000L);
            this.maxEntries = Math.max(1000, json.optInt("maxEntries", 1000000));
            this.terminalRetentionDays = Math.max(1, json.optInt("terminalRetentionDays", 30));
            this.expandIndexedSources = json.optBoolean("expandIndexedSources", false);
            this.indexedSourceBatch = Math.max(1, json.optInt("indexedSourceBatch", 100));
        }

        public boolean enabled() { return this.enabled; }
        public String seedMode() { return this.seedMode; }
        public long recoveryGraceMillis() { return this.recoveryGraceMillis; }
        public int maxEntries() { return this.maxEntries; }
        public int terminalRetentionDays() { return this.terminalRetentionDays; }
        public boolean expandIndexedSources() { return this.expandIndexedSources; }
        public int indexedSourceBatch() { return this.indexedSourceBatch; }

        private JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("enabled", this.enabled).put("seedMode", this.seedMode)
                        .put("recoveryGraceSeconds", this.recoveryGraceMillis / 1000L)
                        .put("maxEntries", this.maxEntries)
                        .put("terminalRetentionDays", this.terminalRetentionDays)
                        .put("expandIndexedSources", this.expandIndexedSources)
                        .put("indexedSourceBatch", this.indexedSourceBatch);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize focused frontier configuration", e);
            }
            return json;
        }
    }

    /** Configuration for the generic focused frontier novelty policy. */
    public static final class Novelty {
        private final boolean enabled;
        private final int newHostWeight;
        private final int newPathWeight;
        private final int expansionWeight;
        private final int freshnessWeight;
        private final int pathPrefixDepth;
        private final boolean externalRequiresDirectEvidence;
        private final String explorationCollection;
        private final long repeatSuppressionMillis;

        private Novelty(final JSONObject json) {
            this.enabled = json.optBoolean("enabled", false);
            this.newHostWeight = nonNegative(json.optInt("newHostWeight", 45));
            this.newPathWeight = nonNegative(json.optInt("newPathWeight", 35));
            this.expansionWeight = nonNegative(json.optInt("expansionWeight", 15));
            this.freshnessWeight = nonNegative(json.optInt("freshnessWeight", 5));
            this.pathPrefixDepth = Math.max(1, Math.min(8, json.optInt("pathPrefixDepth", 2)));
            this.externalRequiresDirectEvidence = json.optBoolean("externalRequiresDirectEvidence", true);
            this.explorationCollection = json.optString("explorationCollection", "").trim();
            this.repeatSuppressionMillis = Math.max(0L, json.optLong("repeatSuppressionMinutes", 60L)) * 60000L;
        }

        public boolean enabled() { return this.enabled; }
        public int newHostWeight() { return this.newHostWeight; }
        public int newPathWeight() { return this.newPathWeight; }
        public int expansionWeight() { return this.expansionWeight; }
        public int freshnessWeight() { return this.freshnessWeight; }
        public int pathPrefixDepth() { return this.pathPrefixDepth; }
        public boolean externalRequiresDirectEvidence() { return this.externalRequiresDirectEvidence; }
        public String explorationCollection() { return this.explorationCollection; }
        public long repeatSuppressionMillis() { return this.repeatSuppressionMillis; }

        public int totalWeight() {
            return this.newHostWeight + this.newPathWeight + this.expansionWeight + this.freshnessWeight;
        }

        private JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("enabled", this.enabled).put("newHostWeight", this.newHostWeight)
                        .put("newPathWeight", this.newPathWeight).put("expansionWeight", this.expansionWeight)
                        .put("freshnessWeight", this.freshnessWeight).put("pathPrefixDepth", this.pathPrefixDepth)
                        .put("externalRequiresDirectEvidence", this.externalRequiresDirectEvidence)
                        .put("explorationCollection", this.explorationCollection)
                        .put("repeatSuppressionMinutes", this.repeatSuppressionMillis / 60000L);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize focused novelty configuration", e);
            }
            return json;
        }

        private static int nonNegative(final int value) { return Math.max(0, value); }
    }

    private final String id;
    private final String version;
    private final String description;
    private final boolean enabled;
    private final List<String> seeds;
    private final Set<String> trustedHosts;
    private final Set<String> tlds;
    private final Set<String> excludedHosts;
    private final List<ScoringRule> scoringRules;
    private final Map<String, CollectionRule> collections;
    private final int probableThreshold;
    private final int focusedThreshold;
    private final int parentInheritanceDivisor;
    private final boolean controlledExploration;
    private final Limits limits;
    private final Novelty novelty;
    private final Frontier frontier;

    private PolicyConfiguration(final JSONObject json) {
        this.id = required(json, "id");
        this.version = json.optString("version", "1");
        this.description = json.optString("description", "");
        this.enabled = json.optBoolean("enabled", false);
        this.seeds = strings(json.opt("seeds"));
        this.trustedHosts = normalizedSet(json.opt("trustedHosts"));
        this.tlds = normalizedSet(json.opt("tlds"));
        this.excludedHosts = normalizedSet(json.opt("excludedHosts"));
        final JSONObject scoring = json.optJSONObject("scoring");
        final JSONArray rules = asJSONArray(scoring == null ? json.opt("rules") : scoring.opt("rules"));
        this.scoringRules = new ArrayList<>();
        if (rules != null) {
            for (int i = 0; i < rules.length(); i++) {
                try {
                    this.scoringRules.add(new ScoringRule(rules.getJSONObject(i)));
                } catch (final JSONException e) {
                    throw new IllegalArgumentException("invalid focused policy scoring rule at index " + i, e);
                }
            }
        }
        final JSONObject limitsJSON = json.optJSONObject("limits");
        this.limits = new Limits(limitsJSON == null ? new JSONObject() : limitsJSON);
        final JSONObject thresholds = scoring == null ? json.optJSONObject("thresholds") : scoring.optJSONObject("thresholds");
        this.probableThreshold = Math.max(0, thresholds == null ? 15 : thresholds.optInt("probable", 15));
        this.focusedThreshold = Math.max(this.probableThreshold + 1, thresholds == null ? 35 : thresholds.optInt("focused", 35));
        this.parentInheritanceDivisor = Math.max(1, scoring == null ? 4 : scoring.optInt("parentInheritanceDivisor", 4));
        this.controlledExploration = scoring == null || scoring.optBoolean("controlledExploration", true);
        final JSONObject noveltyJSON = json.optJSONObject("novelty");
        this.novelty = new Novelty(noveltyJSON == null ? new JSONObject() : noveltyJSON);
        final JSONObject frontierJSON = json.optJSONObject("frontier");
        this.frontier = new Frontier(frontierJSON == null ? new JSONObject() : frontierJSON);
        final JSONObject collectionJSON = json.optJSONObject("collections");
        this.collections = new LinkedHashMap<>();
        if (collectionJSON != null) {
            for (final String key : collectionJSON.keySet()) {
                final JSONObject rule = collectionJSON.optJSONObject(key);
                if (rule != null) this.collections.put(key, new CollectionRule(key, rule, this.limits.defaultRecrawlIntervalMillis()));
            }
        }
    }

    public static PolicyConfiguration fromJSON(final JSONObject json) throws JSONException {
        return new PolicyConfiguration(json);
    }

    public static PolicyConfiguration load(final File file) throws IOException, JSONException {
        try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return fromJSON(new JSONObject(new JSONTokener(reader)));
        }
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject(true);
        try {
            json.put("id", this.id).put("version", this.version).put("description", this.description).put("enabled", this.enabled);
            json.put("seeds", new JSONArray(this.seeds));
            json.put("trustedHosts", new JSONArray(this.trustedHosts));
            json.put("tlds", new JSONArray(this.tlds));
            json.put("excludedHosts", new JSONArray(this.excludedHosts));
            json.put("limits", this.limits.toJSON());
            final JSONObject scoring = new JSONObject(true);
            final JSONArray rules = new JSONArray();
            for (final ScoringRule rule : this.scoringRules) rules.put(rule.toJSON());
            scoring.put("rules", rules).put("parentInheritanceDivisor", this.parentInheritanceDivisor)
                    .put("controlledExploration", this.controlledExploration);
            final JSONObject thresholds = new JSONObject(true);
            thresholds.put("probable", this.probableThreshold).put("focused", this.focusedThreshold);
            scoring.put("thresholds", thresholds);
            json.put("scoring", scoring);
            json.put("novelty", this.novelty.toJSON());
            json.put("frontier", this.frontier.toJSON());
            final JSONObject collectionJSON = new JSONObject(true);
            for (final CollectionRule rule : this.collections.values()) collectionJSON.put(rule.id(), rule.toJSON());
            json.put("collections", collectionJSON);
        } catch (final JSONException e) {
            throw new IllegalStateException("cannot serialize focused policy " + this.id, e);
        }
        return json;
    }

    private static String required(final JSONObject json, final String key) {
        final String value = json.optString(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("focused policy requires " + key);
        return value;
    }

    private static JSONArray asJSONArray(final Object value) {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof java.util.Collection<?>) return new JSONArray((java.util.Collection<?>) value);
        if (value != null && value.getClass().isArray()) {
            final List<Object> values = new ArrayList<>();
            final int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) values.add(java.lang.reflect.Array.get(value, i));
            return new JSONArray(values);
        }
        return null;
    }

    private static List<String> strings(final Object input) {
        final JSONArray array = asJSONArray(input);
        if (array == null) return Collections.emptyList();
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            final String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<String> normalizedSet(final Object value) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String item : strings(value)) result.add(item.toLowerCase(Locale.ROOT));
        return Collections.unmodifiableSet(result);
    }

    public String id() { return this.id; }
    public String version() { return this.version; }
    public String description() { return this.description; }
    public boolean enabled() { return this.enabled; }
    public List<String> seeds() { return this.seeds; }
    public Set<String> trustedHosts() { return this.trustedHosts; }
    public Set<String> tlds() { return this.tlds; }
    public Set<String> excludedHosts() { return this.excludedHosts; }
    public List<ScoringRule> scoringRules() { return Collections.unmodifiableList(this.scoringRules); }
    public Map<String, CollectionRule> collections() { return Collections.unmodifiableMap(this.collections); }
    public int probableThreshold() { return this.probableThreshold; }
    public int focusedThreshold() { return this.focusedThreshold; }
    public int parentInheritanceDivisor() { return this.parentInheritanceDivisor; }
    public boolean controlledExploration() { return this.controlledExploration; }
    public Limits limits() { return this.limits; }
    public Novelty novelty() { return this.novelty; }
    public Frontier frontier() { return this.frontier; }
}
