package com.bankx.error;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce cualquier fallo a un contrato de error homogéneo:
 * <pre>{"error": "&lt;codigo&gt;"}</pre>
 *
 * <p>Códigos del contrato: {@code account_not_found}, {@code risk_rejected},
 * {@code insufficient_funds} y {@code validation_error}.</p>
 */
@Log4j2
@RestControllerAdvice
public class GlobalErrorHandler {

    private static final String VALIDATION_ERROR = "validation_error";

    /** Reglas de negocio: cuenta inexistente, riesgo rechazado, fondos insuficientes. */
    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleBiz(BusinessException ex) {
        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())));
    }

    /**
     * Fallos de Bean Validation sobre el cuerpo de la petición.
     * Sin este manejador Spring devolvería su formato por defecto, rompiendo
     * la homogeneidad del contrato de error.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(WebExchangeBindException ex) {
        String detalle = ex.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("validation_error details={}", detalle);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", VALIDATION_ERROR);
        body.put("details", detalle);
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    /** Cuerpo ausente o malformado, o parámetros obligatorios faltantes. */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInput(ServerWebInputException ex) {
        log.warn("validation_error reason={}", ex.getReason());
        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", VALIDATION_ERROR)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGen(Exception ex) {
        log.error("internal_error type={} msg={}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error")));
    }
}
