package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link HistoriqueFourni} : séances du centre jointes à la fiche du patient (une séance dont le
 * patient a disparu est absente), filtrées, triées et paginées en SQL. Départage stable (création décroissante, puis identifiant),
 * comme le legacy. Les lignes de statut illisible sont exclues et comptées dans un WARN (KB-SEA-07 : une ligne illisible ne rend plus
 * la liste impossible).
 */
@Component
public class HistoriqueFourniLegacy implements HistoriqueFourni {

    private static final Logger JOURNAL = LoggerFactory.getLogger(HistoriqueFourniLegacy.class);
    private static final List<String> STATUTS_LISIBLES = List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT");
    private static final String LISTE_LISIBLE = "('CREE','VALIDEE','SIGNEE','FACTUREE','ABSENT')";

    private final JdbcTemplate jdbc;

    public HistoriqueFourniLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page rechercher(UUID centre, LocalDate du, LocalDate au, Set<String> statuts, String texte, String ordre, boolean decroissant,
                           Boolean deverrouillee, int page, int taille) {
        StringBuilder from = new StringBuilder(" FROM seances s INNER JOIN patients p ON p.id = s.patient_id"
                + " WHERE s.center_id = ? AND p.center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre, centre));
        String base = from.toString();
        from.append(" AND s.statut IN ").append(LISTE_LISIBLE);
        if (du != null) {
            from.append(" AND s.date_seance >= ?");
            parametres.add(Date.valueOf(du));
        }
        if (au != null) {
            from.append(" AND s.date_seance <= ?");
            parametres.add(Date.valueOf(au));
        }
        if (deverrouillee != null) {
            from.append(deverrouillee ? " AND s.regularisation_deverrouillee_at IS NOT NULL" : " AND s.regularisation_deverrouillee_at IS NULL");
        }
        List<String> retenus = statuts == null ? List.of() : statuts.stream().filter(STATUTS_LISIBLES::contains).sorted().toList();
        if (statuts != null && !statuts.isEmpty()) {
            if (retenus.isEmpty()) {
                from.append(" AND 1 = 0");
            } else {
                from.append(" AND s.statut IN (").append(String.join(",", retenus.stream().map(x -> "?").toList())).append(")");
                parametres.addAll(retenus);
            }
        }
        if (texte != null && !texte.isBlank()) {
            for (String mot : texte.toLowerCase(Locale.ROOT).split("\\s+")) {
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

        String sens = decroissant ? " DESC" : " ASC";
        String tri = switch (ordre == null ? "DATE" : ordre) {
            case "PATIENT" -> "p.nom" + sens + ", p.prenom" + sens;
            case "CODE" -> "p.code_patient" + sens;
            case "STATUT" -> "s.statut" + sens;
            case "CREATION" -> "s.created_at" + sens;
            default -> "s.date_seance" + sens;
        };
        List<Object> pagination = new ArrayList<>(parametres);
        pagination.add(taille);
        pagination.add((long) page * taille);
        List<Ligne> lignes = jdbc.query("SELECT s.id, s.center_id, s.patient_id, p.code_patient, p.nom, p.prenom, s.date_seance, s.statut,"
                        + " s.created_at, s.validated_at, s.signed_infirmier_at, s.signed_medecin_at, s.regularisation_deverrouillee_at,"
                        + " s.hors_planning, s.motif_hors_planning" + from
                        + " ORDER BY " + tri + ", s.created_at DESC, s.id ASC LIMIT ? OFFSET ?",
                (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                        rs.getObject("patient_id", UUID.class), rs.getString("code_patient"), rs.getString("nom"),
                        rs.getString("prenom"), rs.getObject("date_seance", LocalDate.class), rs.getString("statut"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("validated_at", OffsetDateTime.class),
                        rs.getObject("signed_infirmier_at", OffsetDateTime.class), rs.getObject("signed_medecin_at", OffsetDateTime.class),
                        rs.getObject("regularisation_deverrouillee_at", OffsetDateTime.class), rs.getBoolean("hors_planning"),
                        rs.getString("motif_hors_planning")),
                pagination.toArray());
        return new Page(lignes, total == null ? 0 : total);
    }
}
