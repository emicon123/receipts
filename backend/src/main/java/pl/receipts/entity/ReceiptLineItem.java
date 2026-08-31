package pl.receipts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One product line on a receipt. {@code corrected} is sticky — set true only by
 * PUT /receipts/{id}/line-items/{itemId}; never touched/set by a classification-batch replace.
 */
@Entity
@Table(name = "receipt_line_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private Receipt receipt;

    @Column(name = "product_name", nullable = false, length = 300)
    private String productName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false)
    private SpendCategory category;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "quantity", precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "corrected", nullable = false)
    private boolean corrected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReceiptLineItem(String productName, SpendCategory category, BigDecimal amount, BigDecimal quantity) {
        this.productName = productName;
        this.category = category;
        this.amount = amount;
        this.quantity = quantity;
        this.corrected = false;
        this.createdAt = Instant.now();
    }
}
