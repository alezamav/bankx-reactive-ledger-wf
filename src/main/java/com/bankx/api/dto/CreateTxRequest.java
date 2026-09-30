package com.bankx.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTxRequest {

    @NotBlank
    private String accountNumber;

    /**
     * Tipo de movimiento. Solo se admiten DEBIT y CREDIT; cualquier otro valor
     * se rechaza en la capa de validación y nunca llega al servicio.
     */
    @NotBlank
    @Pattern(regexp = "(?i)DEBIT|CREDIT", message = "type debe ser DEBIT o CREDIT")
    private String type;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
}
