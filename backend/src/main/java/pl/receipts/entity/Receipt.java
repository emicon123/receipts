package pl.receipts.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Aggregate root — one row per photographed-or-manually-entered receipt. See
 * docs/architecture/02-domain-model-and-schema.md for the full narrative and
 * V1__init.sql for the executable schema this entity must match exactly (no ddl-auto).
 */
@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private ReceiptStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", nullable = false, updatable = false)
    private ReceiptSource source;

    /** Relative to the configured storage root. Null iff source = MANUAL. Never regenerated. */
    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "store_name", length = 200)
    private String storeName;

    /** Derived — SUM(lineItems.amount). Never written independently of that sum. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "receipt", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReceiptLineItem> lineItems = new ArrayList<>();

    private Receipt(ReceiptStatus status, ReceiptSource source, String imagePath, Instant capturedAt,
                     String storeName) {
        this.status = status;
        this.source = source;
        this.imagePath = imagePath;
        this.capturedAt = capturedAt;
        this.storeName = storeName;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public static Receipt newCameraUpload(String imagePath, Instant capturedAt) {
        return new Receipt(ReceiptStatus.PENDING, ReceiptSource.CAMERA, imagePath, capturedAt, null);
    }

    public static Receipt newManualEntry(Instant capturedAt, String storeName) {
        return new Receipt(ReceiptStatus.PROCESSED, ReceiptSource.MANUAL, null, capturedAt, storeName);
    }

    public void addLineItem(ReceiptLineItem lineItem) {
        lineItems.add(lineItem);
        lineItem.setReceipt(this);
    }
}
