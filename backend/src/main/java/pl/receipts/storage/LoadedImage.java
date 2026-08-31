package pl.receipts.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/** A receipt image ready to stream back over HTTP (GET /receipts/{id}/image). */
public record LoadedImage(Resource resource, MediaType mediaType) {
}
