package pl.receipts.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.receipts.dto.common.ErrorDetail;
import pl.receipts.dto.common.ErrorResponse;

/**
 * Single error-envelope translator for the whole API — every error response is
 * {@code { errors: [{code, field, message}], meta: {...} }} (docs/openapi.yaml
 * components.schemas.ErrorResponse). See docs/architecture/05-api-contract.md for the
 * per-endpoint 400 vs 422 split this class implements:
 * <ul>
 *   <li>400 — the request body/params are structurally malformed (unparseable JSON, wrong
 *       query-param type, missing multipart part, invalid query-param combination).</li>
 *   <li>422 — the request parsed fine but fails a semantic/business constraint (bean validation
 *       on a body, an out-of-enum category on a human-input path).</li>
 *   <li>404 / 409 — resource-state problems (not found / reprocess conflict).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 400: malformed request ----

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("MALFORMED_REQUEST", null, "request body is missing or not valid JSON");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return badRequest("MALFORMED_REQUEST", ex.getRequestPartName(), "required part is missing");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("VALIDATION_ERROR", ex.getParameterName(), "required parameter is missing");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("VALIDATION_ERROR", ex.getName(),
                "invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(this::toErrorDetail)
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(errors));
    }

    @ExceptionHandler(InvalidQueryParamException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQueryParam(InvalidQueryParamException ex) {
        return badRequest("VALIDATION_ERROR", ex.getField(), ex.getMessage());
    }

    @ExceptionHandler(MalformedBatchRequestException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBatch(MalformedBatchRequestException ex) {
        return badRequest("VALIDATION_ERROR", null, ex.getMessage());
    }

    // ---- 422: semantically invalid input ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleNotValid(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorDetail)
                .toList();
        if (errors.isEmpty()) {
            errors = List.of(new ErrorDetail("VALIDATION_ERROR", "request body failed validation"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(errors));
    }

    @ExceptionHandler(InvalidLineItemException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLineItem(InvalidLineItemException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(new ErrorDetail("VALIDATION_ERROR", ex.getField(), ex.getMessage())));
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedImageType(UnsupportedImageTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(new ErrorDetail("VALIDATION_ERROR", "image", ex.getMessage())));
    }

    // ---- 404 ----

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorDetail("NOT_FOUND", ex.getMessage())));
    }

    // ---- 409 ----

    @ExceptionHandler(ReceiptStateConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ReceiptStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(new ErrorDetail("STATE_CONFLICT", ex.getMessage())));
    }

    // ---- 500: everything else ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(new ErrorDetail("INTERNAL_ERROR", "an unexpected error occurred")));
    }

    private ResponseEntity<ErrorResponse> badRequest(String code, String field, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(new ErrorDetail(code, field, message)));
    }

    private ErrorDetail toErrorDetail(FieldError fieldError) {
        return new ErrorDetail("VALIDATION_ERROR", fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ErrorDetail toErrorDetail(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
        return new ErrorDetail("VALIDATION_ERROR", path, violation.getMessage());
    }
}
