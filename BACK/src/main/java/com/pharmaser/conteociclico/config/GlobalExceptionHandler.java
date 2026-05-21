package com.pharmaser.conteociclico.config;

import com.pharmaser.conteociclico.dto.ApiError;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ApiError("NOT_FOUND", ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDuplicate(DataIntegrityViolationException ex) {
        logger.warn("Violación de integridad de datos: {}", ex.getMessage());
        return ResponseEntity.status(409)
                .body(new ApiError("CONFLICT", "Registro duplicado o restricción de integridad violada", LocalDateTime.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ApiError("FORBIDDEN", "No tiene permisos para realizar esta acción", LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
                .body(new ApiError("BAD_REQUEST", ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413)
                .body(new ApiError("FILE_TOO_LARGE", "El archivo supera el tamaño máximo permitido (50 MB)", LocalDateTime.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntime(RuntimeException ex) {
        // Se loguea el mensaje real internamente pero NO se expone al cliente
        // para evitar fugas de info (stack traces, SQL, rutas internas, etc.)
        logger.error("RuntimeException no controlada: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500)
                .body(new ApiError("INTERNAL_ERROR", "Error interno del servidor", LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        logger.error("Excepción no controlada", ex);
        return ResponseEntity.status(500)
                .body(new ApiError("INTERNAL_ERROR", "Error interno del servidor", LocalDateTime.now()));
    }
}
