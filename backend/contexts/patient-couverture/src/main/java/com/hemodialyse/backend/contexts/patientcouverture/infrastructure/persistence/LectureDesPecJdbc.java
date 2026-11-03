package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPec;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Recherche SQL des PEC du centre : mêmes filtres que la recherche actuelle (critères JPA traduits en SQL), tri par fin de
 * demande décroissante. Le patient est joint par son identifiant (comme aujourd'hui) ; un filtre sur le patient exige qu'il soit
 * du même centre que la PEC. {@code %} et {@code _} ne sont pas échappés (reproduit).
 */
public class LectureDesPecJdbc implements LectureDesPec {

    private final JdbcTemplate jdbc;

    public LectureDesPecJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Intervalle(LocalDate de, LocalDate a) {
    }

    @Override
    public Page rechercher(CentreId centre, CriteresDePec c) {
        List<String> predicats = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        predicats.add("c.center_id = ?");
        args.add(centre.valeur());
        if (texte(c.code())) patient(predicats, args, "LOWER(COALESCE(p.code_patient, '')) LIKE ?", terme(c.code()));
        if (texte(c.assurance())) patient(predicats, args, "LOWER(COALESCE(p.numero_assurance, '')) LIKE ?", terme(c.assurance()));
        if (texte(c.nom())) {
            String t = terme(c.nom());
            predicats.add("EXISTS (SELECT 1 FROM patients p WHERE p.id = c.patient_id AND p.center_id = c.center_id AND ("
                    + "LOWER(COALESCE(p.nom, '')) LIKE ? OR LOWER(COALESCE(p.prenom, '')) LIKE ? OR "
                    + "LOWER(COALESCE(p.nom, '') || ' ' || COALESCE(p.prenom, '')) LIKE ?))");
            args.add(t);
            args.add(t);
            args.add(t);
        }
        if (texte(c.statut())) {
            predicats.add("LOWER(COALESCE(c.statut, '')) LIKE ?");
            args.add(terme(c.statut()));
        }
        if (texte(c.search())) {
            String t = terme(c.search());
            predicats.add("(EXISTS (SELECT 1 FROM patients p WHERE p.id = c.patient_id AND p.center_id = c.center_id AND ("
                    + "LOWER(COALESCE(p.code_patient, '')) LIKE ? OR LOWER(COALESCE(p.nom, '')) LIKE ? OR "
                    + "LOWER(COALESCE(p.prenom, '')) LIKE ? OR LOWER(COALESCE(p.numero_assurance, '')) LIKE ?)) "
                    + "OR LOWER(COALESCE(c.statut, '')) LIKE ?)");
            for (int i = 0; i < 5; i++) args.add(t);
        }
        intervalle(predicats, args, "c.date_debut_demande", lire(c.debut()));
        intervalle(predicats, args, "c.date_fin_demande", lire(c.fin()));
        String ou = " WHERE " + String.join(" AND ", predicats);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM prise_en_charge c" + ou, Long.class, args.toArray());
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(c.size());
        argsPage.add((long) c.page() * c.size());
        List<Ligne> lignes = jdbc.query("SELECT c.id, c.patient_id, c.date_debut_demande, c.date_fin_demande, c.statut, "
                + "c.date_debut_effectif, c.date_fin_effectif, p.code_patient, p.nom, p.prenom, p.numero_assurance "
                + "FROM prise_en_charge c LEFT JOIN patients p ON p.id = c.patient_id" + ou
                + " ORDER BY c.date_fin_demande DESC LIMIT ? OFFSET ?", (rs, n) -> new Ligne(rs.getObject("id", UUID.class),
                rs.getObject("patient_id", UUID.class), jour(rs.getDate("date_debut_demande")), jour(rs.getDate("date_fin_demande")),
                rs.getString("statut"), jour(rs.getDate("date_debut_effectif")), jour(rs.getDate("date_fin_effectif")),
                rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"), rs.getString("numero_assurance")),
                argsPage.toArray());
        return new Page(lignes, total == null ? 0 : total, c.page(), c.size());
    }

    private static void patient(List<String> predicats, List<Object> args, String condition, String terme) {
        predicats.add("EXISTS (SELECT 1 FROM patients p WHERE p.id = c.patient_id AND p.center_id = c.center_id AND " + condition + ")");
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

    private static String terme(String valeur) {
        return "%" + valeur.toLowerCase(Locale.ROOT).trim() + "%";
    }

    private static LocalDate jour(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static Intervalle lire(String valeur) {
        String brut = valeur == null ? "" : valeur.trim();
        if (brut.isBlank()) return null;
        if (!brut.contains("..")) {
            LocalDate exacte = date(brut);
            return exacte == null ? null : new Intervalle(exacte, exacte);
        }
        String[] parties = brut.split("\\.\\.", 2);
        String deBrut = parties.length > 0 ? parties[0].trim() : "";
        String aBrut = parties.length > 1 ? parties[1].trim() : "";
        LocalDate de = deBrut.isBlank() ? null : date(deBrut);
        LocalDate a = aBrut.isBlank() ? null : date(aBrut);
        if ((!deBrut.isBlank() && de == null) || (!aBrut.isBlank() && a == null)) return null;
        if (de != null && a != null && de.isAfter(a)) return new Intervalle(a, de);
        return new Intervalle(de, a);
    }

    private static LocalDate date(String valeur) {
        String brut = valeur == null ? "" : valeur.trim();
        if (brut.isBlank()) return null;
        if (brut.contains("T")) brut = brut.substring(0, brut.indexOf('T'));
        if (brut.contains(" ")) brut = brut.substring(0, brut.indexOf(' '));
        try {
            return LocalDate.parse(brut);
        } catch (Exception ignoree) {
            // essai du format jour/mois/année
        }
        try {
            return LocalDate.parse(brut, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignoree) {
            return null;
        }
    }
}
