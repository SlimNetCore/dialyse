package com.hemodialyse.backend.domain.shared.port;

/**
 * Port Out — runs a unit of work inside a transaction boundary.
 * <p>
 * Lets pure domain/orchestration code (e.g. an asynchronous recalculation job)
 * obtain transactional atomicity without depending on Spring's
 * {@code @Transactional} (hexagonal architecture — AGENTS.md §3). The
 * infrastructure adapter binds it to the real transaction manager.
 */
public interface TransactionRunner {

    /**
     * Executes {@code work} within a new transaction (REQUIRED semantics).
     */
    void run(Runnable work);
}

