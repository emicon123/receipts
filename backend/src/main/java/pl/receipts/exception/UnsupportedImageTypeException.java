package pl.receipts.exception;

/** Maps to 422 — an uploaded file whose content type isn't JPEG/PNG/WebP. */
public class UnsupportedImageTypeException extends RuntimeException {
    public UnsupportedImageTypeException(String message) {
        super(message);
    }
}
