package com.bankx.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document("accounts")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {
    @Id private String id;
    private String number;
    private String holderName;
    private String currency;   // "PEN" / "USD"
    private BigDecimal balance;
}
