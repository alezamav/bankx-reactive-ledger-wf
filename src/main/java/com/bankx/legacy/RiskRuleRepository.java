package com.bankx.legacy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {
    Optional<RiskRule> findFirstByCurrency(String currency);
}
