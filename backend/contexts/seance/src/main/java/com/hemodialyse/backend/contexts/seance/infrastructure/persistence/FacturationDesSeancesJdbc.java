package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre;
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
 * l'appelant (une seule transaction d'émission). ÉCARTS DÉCIDÉS : KB-SEA-71 (ADR 0097) : {@code lierALAbsence} porte la garde
 * {@code facture_id IS NULL}, comme {@code reinclure} ; lot L3 (ADR 0114, corrections demandées) : KB-SEA-72 (une seule définition de
 * « mémoire présente », {@code MEMOIRE_PRESENTE}, lue et écrite) et KB-SEA-73 ({@code exclure} n'accepte qu'une séance exactement validée
 * ou signée), qui remplacent le « mot pour mot » ci-dessus pour ces points. La jointure {@code patients} / {@code centre_payeur} d'origine (résidu C2-R6) est remplacée en RF3b (ADR 0127) par l'annuaire des payeurs, puis la lecture de {@code patients} par {@link FichesDesPatientsDuCentre} (LEC-PAT ii, ADR 0177 : plus aucun SQL sur les tables de Patient et couverture).
 */
public class FacturationDesSeancesJdbc implements SeancesAFacturerPubliees, ExclusionsEtForfaitsDesSeancesPublies, SeancesDesFacturesPubliees {

    private static final String NON_MODIFIABLE_POUR_EXCLUSION = "Seance introuvable ou non modifiable pour exclusion";

    private static final int TAILLE_DU_LOT = 500;

    private final JdbcTemplate jdbc;
    private final ChaineDesPayeursDuCentre payeurs;
    private final FichesDesPatientsDuCentre fichesDesPatients;

    public FacturationDesSeancesJdbc(JdbcTemplate jdbc, ChaineDesPayeursDuCentre payeurs, FichesDesPatientsDuCentre fichesDesPatients) {
        this.jdbc = jdbc;
        this.payeurs = payeurs;
        this.fichesDesPatients = fichesDesPatients;
    }

    // ---- séances à facturer

    @Override
    public List<SeanceAFacturerPubliee> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin) {
        // RF3b (ADR 0127) : l'agence du centre payeur vient de l'annuaire des payeurs (bornée au centre de la fiche), une lecture par centre payeur.
        Map<UUID, Optional<UUID>> agences = new HashMap<>();
        // LEC-PAT (ii), ARGENT (ADR 0177) : les séances sont lues dans la table de la Séance (ordre date puis identifiant) ; la fiche du
        // patient (code, nom, prénom, état, numéro d'assurance, centre payeur) vient de Patient et couverture, bornée au centre : une séance
        // dont la fiche n'est pas du centre est écartée (comme l'ancienne jointure interne). Le tri nom puis prénom est le rang rendu par la
        // fiche ; le tri est stable, donc l'ordre d'avant (nom, prénom, date, identifiant) est conservé à l'identique.
        record Lue(UUID seance, UUID patient, LocalDate date, String statut, UUID forfait, String forfaitNom, BigDecimal forfaitPrix) {
        }
        List<Lue> lues = jdbc.query("""
                        SELECT s.id AS seance_id, s.patient_id, s.date_seance, s.statut,
                               s.forfait_override_id, s.forfait_override_nom, s.forfait_override_prix
                        FROM seances s
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN ('VALIDEE', 'SIGNEE')
                          AND s.facture_id IS NULL
                        ORDER BY s.date_seance, s.id
                        """,
                (rs, ligne) -> new Lue(rs.getObject("seance_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getString("statut"),
                        rs.getObject("forfait_override_id", UUID.class), rs.getString("forfait_override_nom"),
                        rs.getBigDecimal("forfait_override_prix")),
                centre, Date.valueOf(debut), Date.valueOf(fin));
        Map<UUID, FichesDesPatientsDuCentre.PatientAFacturer> fiches = new HashMap<>();
        fichesDesPatients.aFacturer(centre, lues.stream().map(Lue::patient).distinct().toList())
                .forEach(f -> fiches.put(f.patient(), f));
        List<Lue> retenues = new ArrayList<>(lues.stream().filter(l -> fiches.containsKey(l.patient())).toList());
        retenues.sort(java.util.Comparator.comparingInt(l -> fiches.get(l.patient()).rang()));
        List<SeanceAFacturerPubliee> seances = new ArrayList<>();
        for (Lue l : retenues) {
            FichesDesPatientsDuCentre.PatientAFacturer f = fiches.get(l.patient());
            seances.add(new SeanceAFacturerPubliee(l.seance(), l.date(), l.statut(), l.forfait(), l.forfaitNom(), l.forfaitPrix(),
                    new PatientDeLaSeancePublie(l.patient(), f.code(), f.nom(), f.prenom(), f.etat(), f.numeroAssurance(),
                            f.centrePayeur(), agenceDuCentrePayeur(centre, f.centrePayeur(), agences))));
        }
        return seances;
    }

    private UUID agenceDuCentrePayeur(UUID centre, UUID centrePayeur, Map<UUID, Optional<UUID>> dejaLues) {
        if (centrePayeur == null) return null;
        return dejaLues.computeIfAbsent(centrePayeur, id -> payeurs.agenceDuCentrePayeur(centre, id)).orElse(null);
    }

    // ---- exclusion, réinclusion, forfait imposé

    @Override
    public Optional<SeanceExcluablePublie> trouver(UUID centre, UUID seance) {
        // KB-SEA-72 (ADR 0114) : « mémoire présente » = au moins un caractère hors espace, UNE seule définition (MEMOIRE_PRESENTE), lue ici
        // par le SQL et reprise telle quelle par les deux écritures ci-dessous : lecture et écritures ne divergent plus.
        return jdbc.query("SELECT id, patient_id, date_seance, statut, facture_id, "
                        + "CASE WHEN " + MEMOIRE_PRESENTE + " THEN statut_avant_exclusion END AS statut_avant_exclusion, exclusion_absence_id "
                        + "FROM seances WHERE id = ? AND center_id = ?",
                (rs, ligne) -> new SeanceExcluablePublie(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getString("statut"),
                        rs.getObject("facture_id", UUID.class) != null,
                        rs.getString("statut_avant_exclusion"),
                        rs.getObject("exclusion_absence_id", UUID.class)),
                seance, centre).stream().findFirst();
    }

    /** « Mémoire d'exclusion présente » (KB-SEA-72) : non nulle ET au moins un caractère hors espace ; SQL portable H2 / PostgreSQL. */
    private static final String MEMOIRE_PRESENTE = "(statut_avant_exclusion IS NOT NULL AND LENGTH(TRIM(statut_avant_exclusion)) > 0)";

    /** Séance « facturable » (domaine {@code estFacturable()}) : validée ou signée, en casse exacte (KB-SEA-73 : le port redit la garde du domaine). */
    private static final String STATUT_FACTURABLE = "statut IN ('VALIDEE', 'SIGNEE')";

    @Override
    public void exclure(UUID centre, UUID seance, String statutAvant) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = 'ABSENT', statut_avant_exclusion = ?, exclusion_absence_id = NULL "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = ? AND " + STATUT_FACTURABLE,
                statutAvant, seance, centre, statutAvant), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void lierALAbsence(UUID centre, UUID seance, UUID absence) {
        exigerUneLigne(jdbc.update("UPDATE seances SET exclusion_absence_id = ? "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = 'ABSENT' AND " + MEMOIRE_PRESENTE,
                absence, seance, centre), NON_MODIFIABLE_POUR_EXCLUSION);
    }

    @Override
    public void reinclure(UUID centre, UUID seance, String statutRetabli) {
        exigerUneLigne(jdbc.update("UPDATE seances SET statut = ?, statut_avant_exclusion = NULL, exclusion_absence_id = NULL "
                + "WHERE id = ? AND center_id = ? AND facture_id IS NULL AND statut = 'ABSENT' AND " + MEMOIRE_PRESENTE,
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
        return nombreParFacture(centre, factures, "");
    }

    @Override
    public Map<UUID, Long> nombreParFactureAuStatutFacturee(UUID centre, Collection<UUID> factures) {
        return nombreParFacture(centre, factures, " AND statut = 'FACTUREE'");
    }

    private Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures, String filtre) {
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
                            + String.join(", ", java.util.Collections.nCopies(lot.size(), "?")) + ")" + filtre + " GROUP BY facture_id",
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

    @Override
    public List<SeanceDeFacturePubliee> seancesDeLaFacture(UUID centre, UUID facture) {
        return jdbc.query("SELECT id, date_seance, statut, forfait_override_id FROM seances WHERE center_id = ? AND facture_id = ? "
                        + "ORDER BY date_seance, id",
                (rs, ligne) -> new SeanceDeFacturePubliee(rs.getObject("id", UUID.class), rs.getDate("date_seance").toLocalDate(),
                        rs.getString("statut"), rs.getObject("forfait_override_id", UUID.class)),
                centre, facture);
    }
}
