package com.bankx.service;

import com.bankx.domain.Account;
import com.bankx.domain.Transaction;
import com.bankx.error.BusinessException;
import com.bankx.logging.LogContext;
import com.bankx.repo.AccountRepository;
import com.bankx.risk.RiskRemoteClient;
import com.bankx.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Instant;

@Log4j2
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final RiskRemoteClient riskRemoteClient;
    private final Sinks.Many<Transaction> txSink;
    private final LogContext logContext;

    public Mono<Transaction> create(String accountNumber, String type, BigDecimal amount) {
        log.debug("create_tx_start account={} type={} amount={}", accountNumber, type, amount);

        return logContext.withMdc( // inyecta corrId del Reactor Context al MDC de Log4j2
                accountRepo.findByNumber(accountNumber)
                        .switchIfEmpty(Mono.error(new BusinessException("account_not_found")))
                        .flatMap(acc -> validateAndApply(acc, type, amount))
                        .onErrorMap(IllegalStateException.class, e -> new BusinessException(e.getMessage()))
                        .doOnSuccess(tx -> {
                            if (tx != null) {
                                log.info("create_tx_ok account={} type={} amount={} id={}",
                                        accountNumber, type, amount, tx.getId());
                            }
                        })
                        .doOnError(e -> log.warn("create_tx_error account={} type={} amount={} error={}",
                                accountNumber, type, amount, e.getMessage()))
        );
    }

    private Mono<Transaction> validateAndApply(Account acc, String typeRaw, BigDecimal amount) {
        final String type = typeRaw.toUpperCase();

        // 1) Riesgo (bloqueante envuelto -> elastic)
        return riskRemoteClient
                .isAllowed(acc.getCurrency(), type, amount)
                .flatMap(decision -> {
                    if (!decision.allowed()) {
                        log.warn("risk_rejected account={} currency={} type={} amount={} riskSource={}",
                                acc.getNumber(), acc.getCurrency(), type, amount, decision.source());
                        return Mono.error(new BusinessException("risk_rejected"));
                    }

                    // 2) Reglas de negocio
                    if ("DEBIT".equals(type) && acc.getBalance().compareTo(amount) < 0) {
                        log.warn("insufficient_funds account={} balance={} amount={}",
                                acc.getNumber(), acc.getBalance(), amount);
                        return Mono.error(new BusinessException("insufficient_funds"));
                    }

                    // 3) Actualiza balance
                    return Mono.just(acc)
                            .publishOn(Schedulers.parallel())
                            .map(a -> {
                                BigDecimal newBal = "DEBIT".equals(type)
                                        ? a.getBalance().subtract(amount)
                                        : a.getBalance().add(amount);
                                a.setBalance(newBal);
                                return a;
                            })
                            .flatMap(accountRepo::save)
                            // 4) Persiste la transacción
                            .flatMap(saved -> txRepo.save(Transaction.builder()
                                    .accountId(saved.getId())
                                    .type(type)
                                    .amount(amount)
                                    .timestamp(Instant.now())
                                    .status("OK")
                                    .riskSource(decision.source())
                                    .build()))
                            // 5) Notifica por SSE
                            .doOnNext(tx -> {
                                txSink.tryEmitNext(tx);
                                log.debug("sse_emit accountId={} txId={} type={} amount={} riskSource={}",
                                        tx.getAccountId(), tx.getId(), tx.getType(), tx.getAmount(), tx.getRiskSource());
                            });
                });
  }

  /**
  * Obtiene el listado de transacciones asociadas a un número de cuenta.
  * <p>
  * Si la cuenta no existe, se emite un error de tipo
  * {@link BusinessException} con el mensaje {@code account_not_found}.
  * </p>
  *
  * @param accountNumber número de cuenta a consultar
  * @return un {@link Flux} con las transacciones de la cuenta en orden
  *         descendente por fecha de creación
  */
  public Flux<Transaction> byAccount(String accountNumber) {
    log.debug("list_tx account={}", accountNumber);
    return accountRepo.findByNumber(accountNumber)
              .switchIfEmpty(Mono.error(new BusinessException("account_not_found")))
              .flatMapMany(acc -> txRepo.findByAccountIdOrderByTimestampDesc(acc.getId()));
  }

  /**
  * Expone un flujo reactivo de transacciones en tiempo real mediante
  * Server-Sent Events (SSE).
  * <p>
  * Cada vez que se crea una nueva transacción, se emite un evento
  * con el objeto {@link Transaction} correspondiente.
  * </p>
  *
  * @return un {@link Flux} de {@link ServerSentEvent} que transmite
  *         las transacciones en vivo bajo el evento "transaction".
  */
  public Flux<ServerSentEvent<Transaction>> stream() {
    log.debug("subscribe_sse_transactions");
    return txSink.asFlux().map(tx ->
                ServerSentEvent.builder(tx).event("transaction").build()
        );
  }
}
