package pl.receipts.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.web.multipart.MultipartFile;

/**
 * Filesystem read/write for receipt photos, rooted at {@code receipts.storage.root-path} (a
 * Docker volume in prod — see ADR-006, images are kept indefinitely). Streams from disk rather
 * than buffering whole files in memory (a phone photo can be several MB).
 */
public interface ImageStorageService {

    /**
     * Stores the given multipart image under {@code {root}/{year}/{month}/{uuid}.{ext}}
     * (CLAUDE.md § Image storage) and returns the path to persist into
     * {@code receipts.image_path}. Rejects unsupported content types (only JPEG/PNG/WebP).
     */
    StoredImage store(MultipartFile file, Instant capturedAt);

    /** Loads a previously stored image for streaming; {@code relativePath} as read from the DB. */
    LoadedImage load(String relativePath);

    /** Deletes the file if present; never throws — logs and swallows I/O failures (DELETE
     * /receipts/{id} must not fail just because a file is already gone or locked). */
    void deleteQuietly(String relativePath);

    static int monthOf(Instant capturedAt) {
        return capturedAt.atZone(ZoneOffset.UTC).getMonthValue();
    }

    static int yearOf(Instant capturedAt) {
        return capturedAt.atZone(ZoneOffset.UTC).getYear();
    }
}
