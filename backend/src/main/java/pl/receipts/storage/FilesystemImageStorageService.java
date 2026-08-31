package pl.receipts.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.receipts.config.StorageProperties;
import pl.receipts.exception.UnsupportedImageTypeException;

@Service
public class FilesystemImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(FilesystemImageStorageService.class);

    /** Content type -> file extension for the three types docs/openapi.yaml declares. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final Path root;

    public FilesystemImageStorageService(StorageProperties properties) {
        this.root = Path.of(properties.rootPath()).toAbsolutePath().normalize();
    }

    @Override
    public StoredImage store(MultipartFile file, Instant capturedAt) {
        String extension = EXTENSION_BY_CONTENT_TYPE.get(file.getContentType());
        if (extension == null) {
            throw new UnsupportedImageTypeException(
                    "unsupported image content type '" + file.getContentType() + "'; must be JPEG, PNG, or WebP");
        }

        int year = ImageStorageService.yearOf(capturedAt);
        int month = ImageStorageService.monthOf(capturedAt);
        String relativePath = "%d/%02d/%s.%s".formatted(year, month, UUID.randomUUID(), extension);
        Path target = root.resolve(relativePath).normalize();

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to store receipt image", e);
        }

        return new StoredImage(relativePath);
    }

    @Override
    public LoadedImage load(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new java.util.NoSuchElementException("receipt image not found on disk: " + relativePath);
            }
            return new LoadedImage(resource, mediaTypeFor(relativePath));
        } catch (java.net.MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deleteQuietly(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(relativePath).normalize());
        } catch (IOException e) {
            log.warn("failed to delete receipt image file at {}: {}", relativePath, e.getMessage());
        }
    }

    private MediaType mediaTypeFor(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
