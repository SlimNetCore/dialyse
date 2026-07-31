package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.shared.port.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Infrastructure adapter for {@link TransactionRunner}, backed by Spring's
 * {@link TransactionTemplate} (REQUIRED propagation). Keeps the transaction
 * framework out of the domain layer (hexagonal architecture — AGENTS.md §3).
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }
}

