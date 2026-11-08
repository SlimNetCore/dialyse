package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures et écritures publiées de la Séance pour la Facturation (ADR 0097, lot c2a), sur la table {@code seances}. Les requêtes sont celles de
 * {@code SeancesDeFacturationJdbc}, {@code SeancesFacturablesJdbc} et des comptages de {@code LectureDeLaSyntheseJdbc} /
 * {@code LectureDuTableauDeBordJdbc} qu'elles remplaceront, mot pour mot : mêmes colonnes, mêmes gardes, même casse du statut (KB-SEA-73),
 * mêmes messages de refus. Aucun montant n'est recalculé : prix copiés à l'identique. N'ouvre aucune transaction : participe à celle de
 * l'appelant (une seule transaction d'émission). SEUL ÉCART DÉCIDÉ (KB-SEA-71, ADR 0097) : {@code lierALAbsence} porte la garde
 * {@code facture_id IS NULL}, comme {@code reinclure} (une absence ne se lie pas à une séance facturée). La jointure {@code patients} / {@code centre_payeur} est celle d'origine (résidu C2-R6).
 */
public class FacturationDesSeancesJdbc implements SeancesAFacturerPubliees, ExclusionsEtForfaitsDesSeancesPublies, SeancesDesFacturesPubliees {

    private static final String NON_MODIFIABLE_POUR_EXCLUSION = "Seance introuvable ou non modifiable pour exclusion";

    private static final int TAILLE_DU_LOT = 500;

    private final JdbcTemplate jdbc;

    public FacturationDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- séances à facturer

    @Override
    public List<SeanceAFacturerPubliee> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin) {
        return jdbc.query("""
                        SELECT s.id AS seance_id, s.patient_id, s.date_seance, s.statut,
                               s.forfait_override_id, s.forfait_override_nom, s.forfait_override_prix,
                               p.code_patient, p.nom, p.prenom, p.etat_patient,
                               p.numero_assurance, p.centre_payeur_id, cp.agence_id
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id
                        LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN ('VALIDEE', 'SIGNEE')
                          AND s.facture_id IS NULL
                        ORDER BY p.nom, p.prenom, s.date_seance, s.id
                        """,
                (rs, ligne) -> new SeanceAFacturerPubliee(rs.getObject("seance_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getString("statut"),
                        rs.getObject("forfait_override_id", UUID.class), rs.getString("forfait_override_nom"),
                        rs.getBigDecimal("forfait_override_prix"),
                        new PatientDeLaSeancePublie(rs.getObject("patient_id", UUID.class), rs.getString("code_patient"),
                                rs.getString("nom"), rs.getString("prenom"), rs.getString("etat_patient"),
                                rs.getString("numero_assurance"), rs.getObject("centre_payeur_id", UUID.class),
                                rs.getObject("agence_id", UUID.class))),
                centre, Date.valueOf(debut), Date.valueOf(fin));
    }

    // ---- exclusion, réinclusion, forfait imposé

    @Override
    public Optional<SeanceExcluablePublie> trouver(UUID centre, UUID seance) {
        return jdbc.query("SELECT id, patient_id, date_seance, statut, facture_id, statut_avant_exclusion, exclusion_absence_id "
                        + "FROM seances WHERE id = ? AND center_id = ?",
                (rs, ligne) -> {
                    String avant = rs.getString("statut_avant_exclusion");
                    return new SeanceExcluablePublie(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                            rs.getDate("date_seance").toLocalDate(), rs.getString("statut"),
                            rs.getObject("facture_id", UUID.class) != null,
                            avant == null || avant.isBlank() ? null : avant,
                            rs.getObject("exclusion_absence_id", UUID.class));
                },
                seance, centre).stream().findFirst();
    }

    @Override
    public void exclure(UUID centre, UUID seance, String statutAvant) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = 'ABSENT', statut_avant_exclusion = ?, exclusion_absence_id = NULL "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = ?",
                statutAvant, seance, centre, statutAvant), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void lierALAbsence(UUID centre, UUID seance, UUID absence) {
        exigerUneLigne(jdbc.update("UPDATE seances SET exclusion_absence_id = ? "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = 'ABSENT' AND statut_avant_exclusion IS NOT NULL",
                absence, seance, centre), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void reinclure(UUID centre, UUID seance, String statutRetabli) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = ?, statut_avant_exclusion = NULL, exclusion_absence_id = NULL "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = 'ABSENT' "
                + "AND statut_avant_exclusion IS NOT NULL",
                statutRetabli, seance, centre), "Seance introuvable ou non reinclusable");
    }

    @Override
    public void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le) {
        exigerUneLigne(jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ?, "
                        + "forfait_override_updated_at = ?, forfait_override_updated_by = ? "
                        + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut IN ('VALIDEE', 'SIGNEE')",
                forfait, libelle, prixHt, Timestamp.from(le), auteur, seance, centre),
                "Seance introuvable ou non modifiable pour changement forfait");
    }

    private static void exigerUneLigne(int modifiees, String message) {
        if (modifiees != 1) {
            throw new IllegalStateException(message);
        }
    }

    // ---- séances facturées (deux définitions, KB-SEA-75)

    @Override
    public Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures) {
        Map<UUID, Long> nombres = new HashMap<>();
        List<UUID> demandees = new ArrayList<>(new java.util.LinkedHashSet<>(factures));
        for (int debut = 0; debut < demandees.size(); debut += TAILLE_DU_LOT) {
            List<UUID> lot = demandees.subList(debut, Math.min(debut + TAILLE_DU_LOT, demandees.size()));
            Object[] criteres = new Object[lot.size() + 1];
            criteres[0] = centre;
            for (int i = 0; i < lot.size(); i++) {
                criteres[i + 1] = lot.get(i);
            }
            jdbc.query("SELECT facture_id, COUNT(1) AS nombre FROM seances WHERE center_id = ? AND facture_id IN ("
                            + String.join(", ", java.util.Collections.nCopies(lot.size(), "?")) + ") GROUP BY facture_id",
                    rs -> {
                        nombres.put(rs.getObject("facture_id", UUID.class), rs.getLong("nombre"));
                    }, criteres);
        }
        return nombres;
    }

    @Override
    public long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin) {
        Long nombre = jdbc.queryForObject("SELECT COUNT(1) FROM seances WHERE center_id = ? AND statut = 'FACTUREE' AND date_seance BETWEEN ? AND ?",
                Long.class, centre, Date.valueOf(debut), Date.valueOf(fin));
        return nombre == null ? 0L : nombre;
    }
}
