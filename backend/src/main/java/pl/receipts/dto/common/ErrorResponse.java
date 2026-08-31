package pl.receipts.dto.common;

import java.util.List;

public record ErrorResponse(List<ErrorDetail> errors, Meta meta) {
    public ErrorResponse(List<ErrorDetail> errors) {
        this(errors, Meta.now());
    }

    public ErrorResponse(ErrorDetail error) {
        this(List.of(error), Meta.now());
    }
}
