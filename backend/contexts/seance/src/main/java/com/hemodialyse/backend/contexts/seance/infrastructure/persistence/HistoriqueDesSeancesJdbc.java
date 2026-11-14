package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientsDeLHistorique;
import com.hemodialyse.backend.contexts.seance.domain.PatientsDeLHistorique.PatientDeLHistorique;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Historique des séances du centre (SE7f, ADR 0100 ; LEC-PAT iii, ADR 0178) : requête reprise de {@code HistoriqueFourniLegacy}
 * (plateforme, retiré) sans plus lire {@code patients}. Les séances sont filtrées et ordonnées en SQL sur les seules colonnes de la
 * Séance ; la fiche du patient (code, nom, prénom, filtre de texte, tri et collation) vient de {@link PatientsDeLHistorique} (langage
 * publié de Patient et couverture). Une séance dont le patient n'est pas du centre ou a disparu est absente ; le départage est stable
 * (création décroissante, puis identifiant) : l'ordre par patient est un tri STABLE par rang du patient d'une liste déjà ordonnée par la
 * base de la Séance, donc identique à l'ancien {@code ORDER BY p.nom, p.prenom, s.created_at DESC, s.id}. La pagination se fait sur les
 * identifiants retenus, puis les lignes de la page sont lues. Les lignes de statut illisible sont exclues et comptées dans un WARN
 * (KB-SEA-07, corrigé) ; un motif de hors planning illisible fait ignorer la ligne, journalisée (garde de l'ancien traducteur
 * {@code HistoriqueParLeLegacy}).
 */
public class HistoriqueDesSeancesJdbc implements HistoriqueDesSeances {

    private static final Logger JOURNAL = LoggerFactory.getLogger(HistoriqueDesSeancesJdbc.class);
    private static final List<String> STATUTS_LISIBLES = List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT");
    private static final String LISTE_LISIBLE = "('CREE','VALIDEE','SIGNEE','FACTUREE','ABSENT')";

    private final JdbcTemplate jdbc;
    private final PatientsDeLHistorique patients;

    public HistoriqueDesSeancesJdbc(JdbcTemplate jdbc, PatientsDeLHistorique patients) {
        this.jdbc = jdbc;
        this.patients = patients;
    }

    /** Une séance retenue avant la pagination : ses seuls identifiants (la fiche et les colonnes de la page sont lues ensuite). */
    private record Retenue(UUID seance, UUID patient) {
    }

    @Override
    public PagedResult<LigneDeSeance> rechercher(CentreId centreId, RechercheDeSeances r, Pagination pagination) {
        UUID centre = centreId.valeur();
        List<String> statuts = r.statuts().stream().map(StatutDeSeance::name).toList();
        String ordre = r.ordre().name();
        int page = pagination.page();
        int taille = pagination.taille();

        StringBuilder from = new StringBuilder(" FROM seances s WHERE s.center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre));
        from.append(" AND s.statut IN ").append(LISTE_LISIBLE);
        if (r.du() != null) {
            from.append(" AND s.date_seance >= ?");
            parametres.add(Date.valueOf(r.du()));
        }
        if (r.au() != null) {
            from.append(" AND s.date_seance <= ?");
            parametres.add(Date.valueOf(r.au()));
        }
        if (r.deverrouillee() != null) {
            from.append(r.deverrouillee() ? " AND s.regularisation_deverrouillee_at IS NOT NULL" : " AND s.regularisation_deverrouillee_at IS NULL");
        }
        List<String> retenus = statuts.stream().filter(STATUTS_LISIBLES::contains).sorted().toList();
        if (!statuts.isEmpty()) {
            if (retenus.isEmpty()) {
                from.append(" AND 1 = 0");
            } else {
                from.append(" AND s.statut IN (").append(String.join(",", retenus.stream().map(x -> "?").toList())).append(")");
                parametres.addAll(retenus);
            }
        }

        // La fiche : les patients du centre qui répondent au texte, dans l'ordre demandé quand il porte sur le patient
        boolean parPatient = ordre.equals("PATIENT") || ordre.equals("CODE");
        PatientsDeLHistorique.Ordre ordreDesPatients = ordre.equals("PATIENT") ? PatientsDeLHistorique.Ordre.NOM_PUIS_PRENOM
                : ordre.equals("CODE") ? PatientsDeLHistorique.Ordre.CODE : PatientsDeLHistorique.Ordre.AUCUN;
        List<PatientDeLHistorique> trouves = patients.duCentre(centre, r.texte(), ordreDesPatients, r.decroissant());
        Map<UUID, PatientDeLHistorique> fiches = new HashMap<>();
        trouves.forEach(p -> fiches.put(p.patient(), p));

        journaliserLesStatutsIllisibles(centre, r, fiches);

        String sens = r.decroissant() ? " DESC" : " ASC";
        String tri = switch (ordre) {
            case "STATUT" -> "s.statut" + sens + ", ";
            case "CREATION" -> "s.created_at" + sens + ", ";
            case "PATIENT", "CODE" -> "";
            default -> "s.date_seance" + sens + ", ";
        };
        List<Retenue> toutes = new ArrayList<>();
        jdbc.query("SELECT s.id, s.patient_id" + from + " ORDER BY " + tri + "s.created_at DESC, s.id ASC", rs -> {
            UUID patient = rs.getObject("patient_id", UUID.class);
            if (fiches.containsKey(patient)) toutes.add(new Retenue(rs.getObject("id", UUID.class), patient));
        }, parametres.toArray());
        if (parPatient) {
            // tri STABLE : à rang égal (homonymes, même code) l'ordre de la base (création décroissante, identifiant) est conservé
            toutes.sort(Comparator.comparingInt((Retenue s) -> fiches.get(s.patient()).rang()));
        }

        int debut = (int) Math.min((long) page * taille, toutes.size());
        int fin = (int) Math.min((long) debut + taille, toutes.size());
        List<Retenue> dePage = toutes.subList(debut, fin);
        return PagedResult.of(lire(dePage, fiches), toutes.size(), page, taille);
    }

    /** Les séances de statut illisible dont le patient est du centre : exclues de la liste, comptées dans un WARN (KB-SEA-07). */
    private void journaliserLesStatutsIllisibles(UUID centre, RechercheDeSeances r, Map<UUID, PatientDeLHistorique> fiches) {
        List<UUID> illisibles = jdbc.query("SELECT s.patient_id FROM seances s WHERE s.center_id = ? AND s.statut NOT IN " + LISTE_LISIBLE,
                (rs, i) -> rs.getObject("patient_id", UUID.class), centre);
        if (illisibles.isEmpty()) return;
        Set<UUID> duCentre = new HashSet<>(fiches.keySet());
        if (r.texte() != null && !r.texte().isBlank()) {
            duCentre.clear();
            patients.duCentre(centre, null, PatientsDeLHistorique.Ordre.AUCUN, false).forEach(p -> duCentre.add(p.patient()));
        }
        long nombre = illisibles.stream().filter(duCentre::contains).count();
        if (nombre > 0) {
            JOURNAL.warn("{} séance(s) du centre {} ignorée(s) de l'historique : statut illisible", nombre, centre);
        }
    }

    /** Les lignes de la page, dans l'ordre des identifiants retenus ; une ligne au motif illisible est ignorée et journalisée. */
    private List<LigneDeSeance> lire(List<Retenue> dePage, Map<UUID, PatientDeLHistorique> fiches) {
        if (dePage.isEmpty()) return List.of();
        List<UUID> ids = dePage.stream().map(Retenue::seance).toList();
        Map<UUID, LigneDeSeance> lues = new LinkedHashMap<>();
        jdbc.query("SELECT s.id, s.center_id, s.patient_id, s.date_seance, s.statut, s.created_at, s.validated_at, s.signed_infirmier_at,"
                        + " s.signed_medecin_at, s.regularisation_deverrouillee_at, s.hors_planning, s.motif_hors_planning"
                        + " FROM seances s WHERE s.id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    try {
                        UUID patient = rs.getObject("patient_id", UUID.class);
                        PatientDeLHistorique fiche = fiches.get(patient);
                        lues.put(id, new LigneDeSeance(new SeanceId(id), new CentreId(rs.getObject("center_id", UUID.class)),
                                new PatientId(patient), fiche.code(), fiche.nom(), fiche.prenom(),
                                rs.getObject("date_seance", LocalDate.class), StatutDeSeance.valueOf(rs.getString("statut")),
                                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("validated_at", OffsetDateTime.class),
                                rs.getObject("signed_infirmier_at", OffsetDateTime.class), rs.getObject("signed_medecin_at", OffsetDateTime.class),
                                rs.getObject("regularisation_deverrouillee_at", OffsetDateTime.class), rs.getBoolean("hors_planning"),
                                rs.getString("motif_hors_planning") == null ? null : MotifHorsPlanning.valueOf(rs.getString("motif_hors_planning"))));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        JOURNAL.warn("Séance {} ignorée de l'historique : statut ou motif illisible", id);
                    }
                },
                ids.toArray());
        List<LigneDeSeance> lignes = new ArrayList<>();
        for (UUID id : ids) {
            LigneDeSeance ligne = lues.get(id);
            if (ligne != null) lignes.add(ligne);
        }
        return lignes;
    }
}
