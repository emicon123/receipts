package pl.receipts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root directory for receipt images, e.g. the Docker volume mounted per CLAUDE.md's project
 * structure. Backed by {@code RECEIPTS_STORAGE_PATH} (see application*.yml) — DevOps wires the
 * actual volume mount.
 */
@ConfigurationProperties(prefix = "receipts.storage")
public record StorageProperties(String rootPath) {
}
