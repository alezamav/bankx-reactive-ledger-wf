package com.bankx.api;

import com.bankx.legacy.RiskRule;
import com.bankx.legacy.RiskRuleRepository;
import com.bankx.repo.AccountRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

// Puerto fijo: el cliente de riesgo llama al mock embebido por HTTP, asi que
// la app tiene que escuchar en la misma URL que apunta bankx.risk.base-url.
// Con RANDOM_PORT esa llamada nunca llegaba y TODO caia al fallback legado.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18084",
                "bankx.risk.base-url=http://localhost:18084/mock/risk"
        })
@AutoConfigureWebTestClient
class TransactionControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    RiskRuleRepository riskRuleRepository; // JPA (bloqueante)

    @Autowired
    AccountRepository accountRepository; // Mongo reactivo

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setupData() {
        // Los tests que fuerzan fallo o timeout hacen abrir el circuito, y el
        // estado sobrevive al test porque el contexto de Spring es compartido.
        // Sin este reset, cualquier test posterior recibiria LEGACY_FALLBACK.
        circuitBreakerRegistry.circuitBreaker("riskClient").reset();

        upsertRisk("PEN", new BigDecimal("5000"));

        accountRepository.findByNumber("001-0001").flatMap(acc -> {
            acc.setBalance(new BigDecimal("2000"));
            return accountRepository.save(acc);
        }).switchIfEmpty(Mono.empty()).block();

        accountRepository.findByNumber("001-0002").flatMap(acc -> {
          acc.setBalance(new BigDecimal("800"));
          return accountRepository.save(acc);
        }).switchIfEmpty(Mono.empty()).block();
  }

  private void upsertRisk(String currency, BigDecimal maxPerTx) {
    riskRuleRepository.findFirstByCurrency(currency).ifPresentOrElse(rule -> {
          rule.setMaxDebitPerTx(maxPerTx);
          riskRuleRepository.save(rule);
        }, () -> riskRuleRepository.save(
                RiskRule.builder().currency(currency).maxDebitPerTx(maxPerTx).build()));
  }

    @Test
    void createTransaction_ok() {
    webTestClient.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {
                "accountNumber": "001-0001",
                "type": "DEBIT",
                "amount": 100
              }
            """).exchange().expectStatus().isCreated().expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON).expectBody().jsonPath("$.id")
                .isNotEmpty().jsonPath("$.status").isEqualTo("OK")
                .jsonPath("$.type").isEqualTo("DEBIT")
                .jsonPath("$.amount").isEqualTo(100);
  }

    @Test
    void createTransaction_insufficientFunds() {
        webTestClient.post().uri("/api/transactions").header("X-Risk-Fail", "false")
                .header("X-Risk-DelayMs", "0").contentType(MediaType.APPLICATION_JSON).bodyValue("""
              {"accountNumber":"001-0002","type":"DEBIT","amount":1000}
            """).exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.error")
                .isEqualTo("insufficient_funds");
  }

    @Test
    void createTransaction_riskRejected() {
        // Forzamos riesgo bajo para que dispare "risk_rejected" aunque haya saldo
        upsertRisk("PEN", new BigDecimal("1500"));

        webTestClient.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {
                "accountNumber": "001-0001",
                "type": "DEBIT",
                "amount": 2000
              }
            """).exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.error")
                .isEqualTo("risk_rejected");
  }

    @Test
    void createTransaction_accountNotFound() {
        webTestClient.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {
                "accountNumber": "001-9999",
                "type": "DEBIT",
                "amount": 100
              }
            """).exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.error")
                .isEqualTo("account_not_found");
  }

    @Test
    void createTransaction_remoteError_usesFallback_allows() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions")
                .header("X-Risk-Fail", "true")      // fuerza fallo remoto
                .header("X-Risk-DelayMs", "0").contentType(MediaType.APPLICATION_JSON).bodyValue("""
              {"accountNumber":"001-0001","type":"DEBIT","amount":100}
            """).exchange().expectStatus().isCreated().expectBody().jsonPath("$.status")
                .isEqualTo("OK");
  }

    @Test
    void createTransaction_timeout_usesFallback_allows() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions").header("X-Risk-Fail", "false")
                .header("X-Risk-DelayMs", "1500")   // mayor que tu TimeLimiter (1s)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""
              {"accountNumber":"001-0001","type":"DEBIT","amount":100}
            """).exchange().expectStatus().isCreated().expectBody().jsonPath("$.status")
                .isEqualTo("OK");
  }

    // ---------------------------------------------------------------
    // riskSource: distingue evaluacion remota de fallback al legado
    // ---------------------------------------------------------------

    @Test
    void createTransaction_ok_riskSourceIsRemote() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions")
                .header("X-Risk-Fail", "false")
                .header("X-Risk-DelayMs", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"DEBIT","amount":100}
            """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.riskSource").isEqualTo("REMOTE");
    }

    @Test
    void createTransaction_remoteError_riskSourceIsLegacyFallback() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions")
                .header("X-Risk-Fail", "true")
                .header("X-Risk-DelayMs", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"DEBIT","amount":100}
            """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.riskSource").isEqualTo("LEGACY_FALLBACK");
    }

    // ---------------------------------------------------------------
    // Validacion: contrato de error homogeneo
    // ---------------------------------------------------------------

    @Test
    void createTransaction_invalidType_returnsValidationError() {
        webTestClient.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"TRANSFER","amount":100}
            """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("validation_error");
    }

    @Test
    void createTransaction_negativeAmount_returnsValidationError() {
        webTestClient.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"DEBIT","amount":-5}
            """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("validation_error");
    }

    @Test
    void createTransaction_missingAccountNumber_returnsValidationError() {
        webTestClient.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"type":"DEBIT","amount":100}
            """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("validation_error");
    }

    // ---------------------------------------------------------------
    // Listado
    // ---------------------------------------------------------------

    @Test
    void listTransactions_returnsArray() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"CREDIT","amount":50}
            """)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/transactions?accountNumber=001-0001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void listTransactions_unknownAccount_returnsAccountNotFound() {
        webTestClient.get().uri("/api/transactions?accountNumber=001-9999")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("account_not_found");
    }

    @Test
    void listTransactions_missingParam_returnsValidationError() {
        webTestClient.get().uri("/api/transactions")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("validation_error");
    }

    // ---------------------------------------------------------------
    // SSE
    // ---------------------------------------------------------------

    @Test
    void streamTransactions_returnsEventStream() {
        webTestClient.get().uri("/api/stream/transactions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    // ---------------------------------------------------------------
    // Correlacion y observabilidad
    // ---------------------------------------------------------------

    @Test
    void correlationId_isEchoedBack() {
        upsertRisk("PEN", new BigDecimal("5000"));

        webTestClient.post().uri("/api/transactions")
                .header("X-Correlation-Id", "test-corr-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
              {"accountNumber":"001-0001","type":"CREDIT","amount":10}
            """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Correlation-Id", "test-corr-123");
    }

    @Test
    void correlationId_isGeneratedWhenAbsent() {
        webTestClient.get().uri("/api/transactions?accountNumber=001-0001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void health_isUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void healthReadiness_isAvailable() {
        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
