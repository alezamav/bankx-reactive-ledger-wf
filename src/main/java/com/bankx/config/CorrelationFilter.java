package com.bankx.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
public class CorrelationFilter implements WebFilter {
    private static final String HEADER_CORR = "X-Correlation-Id";
    private static final String HEADER_RISK_FAIL = "X-Risk-Fail";
    private static final String HEADER_RISK_DELAY = "X-Risk-DelayMs";
    private static final String CORR_ID = "corrId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String corrId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HEADER_CORR))
                .orElse(UUID.randomUUID().toString());

        String riskFailHeader  = exchange.getRequest().getHeaders().getFirst(HEADER_RISK_FAIL);
        String riskDelayHeader = exchange.getRequest().getHeaders().getFirst(HEADER_RISK_DELAY);

        MDC.put(CORR_ID, corrId);

        // El contrato exige devolver la cabecera al cliente para poder
        // correlacionar una respuesta con sus logs.
        exchange.getResponse().getHeaders().set(HEADER_CORR, corrId);

        return chain.filter(exchange)
                .contextWrite(ctx -> {
                    var c = ctx.put(CORR_ID, corrId);

                    if (riskFailHeader != null) {
                        c = c.put("riskFail", Boolean.parseBoolean(riskFailHeader));
                    }
                    if (riskDelayHeader != null) {
                        long delay;
                        try { delay = Long.parseLong(riskDelayHeader); }
                        catch (NumberFormatException e) { delay = 0L; }
                        c = c.put("riskDelayMs", delay);
                    }
                    return c;
                })
                .doFinally(sig -> MDC.remove(CORR_ID));
    }
}
