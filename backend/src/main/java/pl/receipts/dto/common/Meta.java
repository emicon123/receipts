package pl.receipts.dto.common;

import java.time.Instant;
import java.util.UUID;

/** Envelope metadata attached to every response — see docs/openapi.yaml components.schemas.Meta. */
public record Meta(UUID requestId, Instant timestamp) {
    public static Meta now() {
        return new Meta(UUID.randomUUID(), Instant.now());
    }
}
