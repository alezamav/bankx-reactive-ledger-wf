package com.bankx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientCfg {

    /**
     * Cliente del servicio remoto de riesgo.
     *
     * <p>La URL es configurable ({@code bankx.risk.base-url} o la variable de
     * entorno {@code RISK_BASE_URL}). En local apunta al mock embebido; en
     * contenedor o AKS se inyecta la del servicio real. Estaba fija a
     * {@code localhost:8084}, lo que impedía alcanzar el remoto en cualquier
     * entorno con otro puerto — incluidos los tests.</p>
     */
    @Bean
    public WebClient riskWebClient(
            @Value("${bankx.risk.base-url:http://localhost:8084/mock/risk}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
