package com.bankx.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("transactions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Transaction {
    @Id private String id;
    private String accountId;
    private String type; // "CREDIT" or "DEBIT"
    private BigDecimal amount;
    private Instant timestamp;
    private String status;// "OK" or "REJECTED"
    private String reason;// null si OK
    private String riskSource;// "REMOTE" o "LEGACY_FALLBACK"
}
