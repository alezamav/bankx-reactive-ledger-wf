package com.bankx.risk;

import com.bankx.legacy.RiskService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Log4j2
@Service
@RequiredArgsConstructor
public class RiskRemoteClient {

    private final WebClient riskWebClient;
    private final RiskService legacy;
    private static final String BACKEND = "riskClient";

    /**
     * Evalúa el riesgo contra el servicio remoto.
     *
     * <p>Protegido por TimeLimiter, Retry y CircuitBreaker. Cuando cualquiera de
     * los tres se dispara, {@link #fallback} resuelve con el módulo legado y
     * marca la decisión como {@code LEGACY_FALLBACK}.</p>
     */
    @TimeLimiter(name = BACKEND)
    @Retry(name = BACKEND)
    @CircuitBreaker(name = BACKEND, fallbackMethod = "fallback")
    public Mono<RiskDecision> isAllowed(String currency, String type, BigDecimal amount) {
        return Mono.deferContextual(ctx -> {
            boolean fail = ctx.getOrDefault("riskFail", false);
            long delayMs = ctx.getOrDefault("riskDelayMs", 0L);

            log.debug("risk_remote_call currency={} type={} amount={} fail={} delayMs={}",
                    currency, type, amount, fail, delayMs);

            return riskWebClient.get()
                    .uri(u -> u.path("/allow")
                            .queryParam("currency", currency)
                            .queryParam("type", type)
                            .queryParam("amount", amount)
                            .queryParam("fail", fail)
                            .queryParam("delayMs", delayMs)
                            .build())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .map(RiskDecision::remote);
        });
    }

    private Mono<RiskDecision> fallback(String currency, String type, BigDecimal amount, Throwable ex) {
        log.warn("risk_remote_fallback reason={}", ex.toString(), ex);
        return legacy.isAllowed(currency, type, amount)
                .map(RiskDecision::legacyFallback);
    }
}
