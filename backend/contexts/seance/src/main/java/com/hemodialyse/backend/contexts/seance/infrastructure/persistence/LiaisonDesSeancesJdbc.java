package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiaisonDesSeancesAUneFacturePubliee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Rattachement des séances à leur facture (ADR 0097, lot c2b), ARGENT : le SEUL écrivain de {@code facture_id} hors de la Séance (SE6). Une
 * séance validée ou signée, non facturée, du centre passe à {@code FACTUREE} et porte l'identité de la facture. Sans effet sur une ligne,
 * la séance n'est plus éligible : {@link IllegalStateException} au même message, et l'appelant annule sa transaction entière. N'ouvre aucune
 * transaction : il participe à celle de l'émission (une seule transaction, ADR 0097 décision 2).
 * <p>
 * KB-SEA-74 corrigé (ADR 0114) : la facture visée doit EXISTER et appartenir au MÊME centre ({@code EXISTS} sur {@code factures}, dans le
 * même UPDATE : la facture est enregistrée avant la liaison, dans la même transaction). C'est la garde applicative, testée sur H2 et
 * PostgreSQL ; en production PostgreSQL elle est doublée par la clé étrangère {@code fk_seances_facture} (migration V54, NOT VALID).
 */
public class LiaisonDesSeancesJdbc implements LiaisonDesSeancesAUneFacturePubliee {

    private final JdbcTemplate jdbc;

    public LiaisonDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lier(UUID centre, UUID facture, List<UUID> seances) {
        for (UUID seance : seances) {
            int modifiees = jdbc.update("""
                            UPDATE seances
                            SET statut = 'FACTUREE', facture_id = ?
                            WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut IN ('VALIDEE', 'SIGNEE')
                              AND EXISTS (SELECT 1 FROM factures f WHERE f.id = ? AND f.center_id = ?)
                            """,
                    facture, seance, centre, facture, centre);
            if (modifiees != 1) {
                throw new IllegalStateException("Une ou plusieurs seances ne sont plus eligibles a la validation");
            }
        }
    }
}
