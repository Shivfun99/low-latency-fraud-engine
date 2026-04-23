package com.fraudshield.orchestrator.exception;
import com.fraudshield.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.UUID;
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception in Orchestrator: {}", traceId, ex.getMessage(), ex);
        ApiError error = new ApiError(
            "Internal orchestration error",
            request.getRequestURI(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            traceId
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ApiError error = new ApiError(
            ex.getMessage(),
            request.getRequestURI(),
            HttpStatus.BAD_REQUEST.value(),
            traceId
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
