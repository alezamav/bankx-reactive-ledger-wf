package com.bankx.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MockRiskControllerTest {

    @Autowired
    WebTestClient webTestClient;

    // 1) OK (fail=false0)
    @Test
    void allow_ok() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/mock/risk/allow")
                        .queryParam("currency", "PEN")
                        .queryParam("type", "DEBIT")
                        .queryParam("amount", "100")
                        .queryParam("fail", "false")
                        .queryParam("delayMs", "0")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class).isEqualTo(true);
    }

    // 2) error forzado (fail=true)
    @Test
    void allow_forcedFail_returns5xx() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/mock/risk/allow")
                        .queryParam("currency", "PEN")
                        .queryParam("type", "DEBIT")
                        .queryParam("amount", "100")
                        .queryParam("fail", "true")
                        .queryParam("delayMs", "0")
                        .build())
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // 3) Usa delayMs > 1000 -> retardo
    @Test
    void allow_withLargeDelay_ok() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/mock/risk/allow")
                        .queryParam("currency", "PEN")
                        .queryParam("type", "DEBIT")
                        .queryParam("amount", "100")
                        .queryParam("fail", "false")
                        .queryParam("delayMs", "1500")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class).isEqualTo(true);
    }
}
