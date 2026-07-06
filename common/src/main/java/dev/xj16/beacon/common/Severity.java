package dev.xj16.beacon.common;

/**
 * Maps textual log levels to a normalized numeric severity so events can be range-queried and
 * aggregated in Elasticsearch (e.g. "everything at or above WARN").
 */
public enum Severity {
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    FATAL(5);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * Resolve a free-form level string to a numeric severity. Unknown or null values map to
     * {@link #INFO} so a malformed producer never breaks the pipeline.
     */
    public static int toNumeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return INFO.level;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase()).level;
        } catch (IllegalArgumentException e) {
            return INFO.level;
        }
    }
}
