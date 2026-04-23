package com.fraudshield.gateway.exception;
import com.fraudshield.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception: {}", traceId, ex.getMessage(), ex);
        ApiError error = new ApiError(
            "An internal error occurred",
            request.getRequestURI(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            traceId
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            details.put(fieldName, errorMessage);
        });
        ApiError error = new ApiError(
            "Validation failed",
            request.getRequestURI(),
            HttpStatus.BAD_REQUEST.value(),
            traceId,
            details
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ApiError error = new ApiError(
            "Access denied: " + ex.getMessage(),
            request.getRequestURI(),
            HttpStatus.FORBIDDEN.value(),
            traceId
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
}
