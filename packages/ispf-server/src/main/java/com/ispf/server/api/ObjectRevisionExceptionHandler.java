package com.ispf.server.api;

import com.ispf.server.object.ObjectRevisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ObjectRevisionExceptionHandler {

    @ExceptionHandler(ObjectRevisionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleRevisionConflict(ObjectRevisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "REVISION_CONFLICT",
                "message", ex.getMessage(),
                "objectPath", ex.objectPath(),
                "expectedRevision", ex.expectedRevision(),
                "currentRevision", ex.currentRevision(),
                "changedBy", ex.changedBy() != null ? ex.changedBy() : "",
                "changedAt", ex.changedAt() != null ? ex.changedAt().toString() : ""
        ));
    }
}
