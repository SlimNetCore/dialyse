package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

/**
 * Aides communes des adapters JDBC des fiches cliniques (antécédents, allergies, abords vasculaires) : détection de PostgreSQL, verrou
 * consultatif de transaction, traduction d'un verrou non obtenu dans le délai (SQLSTATE 55P03) en écriture concurrente du contexte.
 * Ordre global des verrous : centre (Planification), fiches, puis {@code app_user}, puis verrous consultatifs.
 */
final class VerrousJdbc {
    private final JdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    VerrousJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Pose un verrou consultatif de transaction sur la clé (PostgreSQL seulement ; H2 ne l'a pas). L'espace de clés
     * ({@code hashtextextended}) est partagé avec les autres adapters : chaque clé porte un préfixe littéral propre
     * ({@code antecedent|…}, {@code serologie|…}) ; tout nouveau verrou consultatif doit avoir le sien.
     */
    void consultatif(String cle) {
        if (estPostgreSql()) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
            }, cle);
        }
    }

    /** Un verrou non obtenu dans le délai de la transaction devient l'exception demandée. */
    static <T> T verrouille(Supplier<T> lecture, Supplier<? extends RuntimeException> concurrente) {
        try {
            return lecture.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw concurrente.get();
        }
    }

    private boolean estPostgreSql() {
        Boolean lu = postgreSql;
        if (lu == null) {
            lu = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
            postgreSql = lu;
        }
        return lu;
    }

    static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(java.sql.ResultSet rs, String colonne) throws java.sql.SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }
}
