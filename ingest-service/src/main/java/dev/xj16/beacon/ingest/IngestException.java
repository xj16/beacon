package dev.xj16.beacon.ingest;

/** Raised when an event cannot be indexed after enrichment. */
public class IngestException extends RuntimeException {
    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
