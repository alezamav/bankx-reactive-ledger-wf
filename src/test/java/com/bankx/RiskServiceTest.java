package com.bankx;

import com.bankx.legacy.RiskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

@SpringBootTest
class RiskServiceTest {

    @Autowired
    RiskService riskService;

    @Test
    void allowsDebitUnderLimit() {
        StepVerifier.create(riskService.isAllowed("PEN", "DEBIT", new BigDecimal("100")))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void rejectsDebitOverLimit() {
        StepVerifier.create(riskService.isAllowed("PEN", "DEBIT", new BigDecimal("5000")))
                .expectNext(false)
                .verifyComplete();
    }
}
