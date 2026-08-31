package pl.receipts.dto.receipt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record ManualReceiptRequest(
        @NotNull Instant capturedAt,
        @Size(max = 200) String storeName,
        @NotEmpty @Valid List<LineItemInput> lineItems) {
}
