package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDAttestation.Intervalle;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesAttestations;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Recherche SQL des attestations du centre : mêmes filtres que la recherche actuelle, tri par fin décroissante. Le patient est
 * joint par son identifiant seul (comme aujourd'hui ; une attestation orpheline a les champs du patient nuls) ; un FILTRE sur le
 * patient exige qu'il soit du même centre que l'attestation. Décision du 2026-11-04 : {@code %}, {@code _} et {@code \} des filtres texte sont des caractères ordinaires.
 */
public class LectureDesAttestationsJdbc implements LectureDesAttestations {

    private final JdbcTemplate jdbc;

    public LectureDesAttestationsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page rechercher(CentreId centre, CriteresDAttestation c) {
        List<String> predicats = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        predicats.add("a.center_id = ?");
        args.add(centre.valeur());
        if (texte(c.code())) patient(predicats, args, "LOWER(COALESCE(p.code_patient, '')) LIKE ? ESCAPE '\\'", terme(c.code()));
        if (texte(c.assurance())) patient(predicats, args, "LOWER(COALESCE(p.numero_assurance, '')) LIKE ? ESCAPE '\\'", terme(c.assurance()));
        if (texte(c.nom())) {
            String t = terme(c.nom());
            predicats.add("EXISTS (SELECT 1 FROM patients p WHERE p.id = a.patient_id AND p.center_id = a.center_id AND ("
                    + "LOWER(COALESCE(p.nom, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(p.prenom, '')) LIKE ? ESCAPE '\\' OR "
                    + "LOWER(COALESCE(p.nom, '') || ' ' || COALESCE(p.prenom, '')) LIKE ? ESCAPE '\\'))");
            args.add(t);
            args.add(t);
            args.add(t);
        }
        if (texte(c.search())) {
            String t = terme(c.search());
            predicats.add("EXISTS (SELECT 1 FROM patients p WHERE p.id = a.patient_id AND p.center_id = a.center_id AND ("
                    + "LOWER(COALESCE(p.code_patient, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(p.nom, '')) LIKE ? ESCAPE '\\' OR "
                    + "LOWER(COALESCE(p.prenom, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(p.numero_assurance, '')) LIKE ? ESCAPE '\\'))");
            for (int i = 0; i < 4; i++) args.add(t);
        }
        intervalle(predicats, args, "a.date_debut", c.debut());
        intervalle(predicats, args, "a.date_fin", c.fin());
        String ou = " WHERE " + String.join(" AND ", predicats);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit a" + ou, Long.class, args.toArray());
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(c.size());
        argsPage.add((long) c.page() * c.size());
        List<Ligne> lignes = jdbc.query("SELECT a.id, a.patient_id, a.date_debut, a.date_fin, p.code_patient, p.nom, p.prenom, "
                + "p.numero_assurance FROM attestation_droit a LEFT JOIN patients p ON p.id = a.patient_id"
                + ou + " ORDER BY a.date_fin DESC LIMIT ? OFFSET ?", (rs, n) -> new Ligne(rs.getObject("id", UUID.class),
                rs.getObject("patient_id", UUID.class), jour(rs.getDate("date_debut")), jour(rs.getDate("date_fin")),
                rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"), rs.getString("numero_assurance")),
                argsPage.toArray());
        return new Page(lignes, total == null ? 0 : total, c.page(), c.size());
    }

    private static void patient(List<String> predicats, List<Object> args, String condition, String terme) {
        predicats.add("EXISTS (SELECT 1 FROM patients p WHERE p.id = a.patient_id AND p.center_id = a.center_id AND " + condition + ")");
        args.add(terme);
    }

    private static void intervalle(List<String> predicats, List<Object> args, String colonne, Intervalle i) {
        if (i == null) return;
        if (i.de() != null) {
            predicats.add(colonne + " >= ?");
            args.add(Date.valueOf(i.de()));
        }
        if (i.a() != null) {
            predicats.add(colonne + " <= ?");
            args.add(Date.valueOf(i.a()));
        }
    }

    private static boolean texte(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /** Sous-chaîne, minuscules, jokers échappés. */
    private static String terme(String valeur) {
        String brut = valeur.toLowerCase(Locale.ROOT).trim();
        return "%" + brut.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static LocalDate jour(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
