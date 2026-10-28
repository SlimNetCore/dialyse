package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL sur la table {@code seances} (module Séance, sans dépendre de lui) : les colonnes de surcharge de forfait
 * ({@code forfait_override_*}) et, depuis la migration V39, la mémoire de l'exclusion ({@code statut_avant_exclusion},
 * {@code exclusion_absence_id}, ADR 0031). Chaque écriture est gardée par l'état attendu de la séance (non facturée, statut
 * attendu) : sans effet sur une ligne, elle échoue (422), ce qui annule la transaction.
 */
public class SeancesDeFacturationJdbc implements SeancesDeFacturation {

    private static final String NON_MODIFIABLE_POUR_EXCLUSION = "Seance introuvable ou non modifiable pour exclusion";

    private final JdbcTemplate jdbc;

    public SeancesDeFacturationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SeanceDeFacturation> trouver(CentreId centre, UUID seance) {
        return jdbc.query("SELECT id, patient_id, date_seance, statut, facture_id, statut_avant_exclusion, exclusion_absence_id "
                        + "FROM seances WHERE id = ? AND center_id = ?",
                (rs, ligne) -> {
                    String avant = rs.getString("statut_avant_exclusion");
                    return new SeanceDeFacturation(rs.getObject("id", UUID.class), centre, rs.getObject("patient_id", UUID.class),
                            rs.getDate("date_seance").toLocalDate(), new StatutDeSeance(rs.getString("statut")),
                            rs.getObject("facture_id", UUID.class) != null,
                            avant == null || avant.isBlank() ? null : new StatutDeSeance(avant),
                            rs.getObject("exclusion_absence_id", UUID.class));
                },
                seance, centre.valeur()).stream().findFirst();
    }

    @Override
    public void exclure(CentreId centre, UUID seance, StatutDeSeance statutAvant) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = 'ABSENT', statut_avant_exclusion = ?, exclusion_absence_id = NULL "
                        + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = ?",
                statutAvant.valeur(), seance, centre.valeur(), statutAvant.valeur()), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void lierALAbsence(CentreId centre, UUID seance, UUID absence) {
        exigerUneLigne(jdbc.update("UPDATE seances SET exclusion_absence_id = ? "
                        + "WHERE id = ? AND center_id = ? AND statut = 'ABSENT' AND statut_avant_exclusion IS NOT NULL",
                absence, seance, centre.valeur()), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void reinclure(CentreId centre, UUID seance, StatutDeSeance statutRetabli) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = ?, statut_avant_exclusion = NULL, exclusion_absence_id = NULL "
                        + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = 'ABSENT' "
                        + "AND statut_avant_exclusion IS NOT NULL",
                statutRetabli.valeur(), seance, centre.valeur()), "Seance introuvable ou non reinclusable");
    }

    @Override
    public void poserLeForfait(CentreId centre, UUID seance, ForfaitApplique forfait, String auteur, Instant le) {
        exigerUneLigne(jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ?, "
                                + "forfait_override_updated_at = ?, forfait_override_updated_by = ? "
                                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut IN ('VALIDEE', 'SIGNEE')",
                        forfait.id(), forfait.libelle(), forfait.prixHt(), Timestamp.from(le), auteur, seance, centre.valeur()),
                "Seance introuvable ou non modifiable pour changement forfait");
    }

    private static void exigerUneLigne(int modifiees, String message) {
        if (modifiees != 1) {
            throw new IllegalStateException(message);
        }
    }
}
