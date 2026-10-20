package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ACL d'écriture sur les séances du module Séance : une séance validée ou signée, non facturée, du centre passe à l'état
 * {@code FACTUREE} et garde l'identité de sa facture. La mise à jour est gardée par l'état attendu : sans effet sur une ligne, la
 * séance n'est plus éligible et l'émission entière est annulée (RG-FAC-030).
 */
public class LiaisonDesSeancesJdbc implements LiaisonDesSeances {

    private final JdbcTemplate jdbc;

    public LiaisonDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lier(CentreId centre, UUID facture, List<UUID> seances) {
        for (UUID seance : seances) {
            int modifiees = jdbc.update("""
                            UPDATE seances
                            SET statut = 'FACTUREE', facture_id = ?
                            WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut IN ('VALIDEE', 'SIGNEE')
                            """,
                    facture, seance, centre.valeur());
            if (modifiees != 1) {
                throw new IllegalStateException("Une ou plusieurs seances ne sont plus eligibles a la validation");
            }
        }
    }
}
