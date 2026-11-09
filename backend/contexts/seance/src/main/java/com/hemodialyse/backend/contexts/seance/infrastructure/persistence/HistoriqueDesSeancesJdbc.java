package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Historique des séances du centre (SE7f, ADR 0100) : requête reprise MOT POUR MOT de {@code HistoriqueFourniLegacy} (plateforme, retiré).
 * Séances jointes à la fiche du patient (une séance dont le patient a disparu est absente ; lecture de {@code patients} transitoire,
 * inscrite dans {@code TableDuPatientCouvertureTest}), filtrées, triées et paginées en SQL ; départage stable (création décroissante,
 * puis identifiant). Les lignes de statut illisible sont exclues et comptées dans un WARN (KB-SEA-07, corrigé) ; un motif de hors
 * planning illisible fait ignorer la ligne, journalisée (garde de l'ancien traducteur {@code HistoriqueParLeLegacy}).
 */
public class HistoriqueDesSeancesJdbc implements HistoriqueDesSeances {

    private static final Logger JOURNAL = LoggerFactory.getLogger(HistoriqueDesSeancesJdbc.class);
    private static final List<String> STATUTS_LISIBLES = List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT");
    private static final String LISTE_LISIBLE = "('CREE','VALIDEE','SIGNEE','FACTUREE','ABSENT')";

    private final JdbcTemplate jdbc;

    public HistoriqueDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PagedResult<LigneDeSeance> rechercher(CentreId centreId, RechercheDeSeances r, Pagination pagination) {
        UUID centre = centreId.valeur();
        List<String> statuts = r.statuts().stream().map(StatutDeSeance::name).toList();
        String ordre = r.ordre().name();
        int page = pagination.page();
        int taille = pagination.taille();

        StringBuilder from = new StringBuilder(" FROM seances s INNER JOIN patients p ON p.id = s.patient_id"
                + " WHERE s.center_id = ? AND p.center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre, centre));
        String base = from.toString();
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
        if (r.texte() != null && !r.texte().isBlank()) {
            for (String mot : r.texte().toLowerCase(Locale.ROOT).split("\\s+")) {
                String motif = "%" + mot.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                from.append(" AND (LOWER(p.nom) LIKE ? ESCAPE '\\' OR LOWER(p.prenom) LIKE ? ESCAPE '\\'"
                        + " OR LOWER(COALESCE(p.code_patient, '')) LIKE ? ESCAPE '\\')");
                parametres.add(motif);
                parametres.add(motif);
                parametres.add(motif);
            }
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, parametres.toArray());

        Long illisibles = jdbc.queryForObject("SELECT COUNT(*)" + base + " AND s.statut NOT IN " + LISTE_LISIBLE, Long.class, centre, centre);
        if (illisibles != null && illisibles > 0) {
            JOURNAL.warn("{} séance(s) du centre {} ignorée(s) de l'historique : statut illisible", illisibles, centre);
        }

        String sens = r.decroissant() ? " DESC" : " ASC";
        String tri = switch (ordre) {
            case "PATIENT" -> "p.nom" + sens + ", p.prenom" + sens;
            case "CODE" -> "p.code_patient" + sens;
            case "STATUT" -> "s.statut" + sens;
            case "CREATION" -> "s.created_at" + sens;
            default -> "s.date_seance" + sens;
        };
        List<Object> paginationSql = new ArrayList<>(parametres);
        paginationSql.add(taille);
        paginationSql.add((long) page * taille);
        List<LigneDeSeance> lignes = new ArrayList<>();
        jdbc.query("SELECT s.id, s.center_id, s.patient_id, p.code_patient, p.nom, p.prenom, s.date_seance, s.statut,"
                        + " s.created_at, s.validated_at, s.signed_infirmier_at, s.signed_medecin_at, s.regularisation_deverrouillee_at,"
                        + " s.hors_planning, s.motif_hors_planning" + from
                        + " ORDER BY " + tri + ", s.created_at DESC, s.id ASC LIMIT ? OFFSET ?",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    try {
                        lignes.add(new LigneDeSeance(new SeanceId(id), new CentreId(rs.getObject("center_id", UUID.class)),
                                new PatientId(rs.getObject("patient_id", UUID.class)), rs.getString("code_patient"), rs.getString("nom"),
                                rs.getString("prenom"), rs.getObject("date_seance", LocalDate.class), StatutDeSeance.valueOf(rs.getString("statut")),
                                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("validated_at", OffsetDateTime.class),
                                rs.getObject("signed_infirmier_at", OffsetDateTime.class), rs.getObject("signed_medecin_at", OffsetDateTime.class),
                                rs.getObject("regularisation_deverrouillee_at", OffsetDateTime.class), rs.getBoolean("hors_planning"),
                                rs.getString("motif_hors_planning") == null ? null : MotifHorsPlanning.valueOf(rs.getString("motif_hors_planning"))));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        JOURNAL.warn("Séance {} ignorée de l'historique : statut ou motif illisible", id);
                    }
                },
                paginationSql.toArray());
        return PagedResult.of(lignes, total == null ? 0 : total, page, taille);
    }
}
