package pl.receipts.storage;

/** Result of a successful image save — {@code relativePath} is what gets persisted verbatim
 * into {@code receipts.image_path} (relative to the configured storage root). */
public record StoredImage(String relativePath) {
}
