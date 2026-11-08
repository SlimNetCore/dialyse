package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.seance.application.PatientsDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientDuMois;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers la table des patients ({@code patients}, tenue par le Patient et couverture), en lecture seule : la programmation des
 * patients du centre, les patients ayant une séance réalisée dans le mois, et leur identité. Reprend les trois requêtes du legacy à
 * l'identique. Résidu daté de l'ADR 0095 (R2) : à remplacer par un langage publié du Patient (effectif et programmation d'un mois).
 * <p>
 * Lecture tolérante comme le legacy : un booléen peut être un nombre (non nul : vrai), une date peut être {@link LocalDate} ou
 * {@link Date} (autre type : absente).
 */
public class PatientsDuTableauDeBordJdbc implements PatientsDuTableauDeBord {

    private final JdbcTemplate jdbc;

    public PatientsDuTableauDeBordJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PatientDuMois> duCentre(CentreId centre) {
        return jdbc.query("""
                        SELECT id, sexe, date_naissance, date_admission, en_sommeil, etat_patient, date_evenement_etat,
                               jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, jour_dimanche
                        FROM patients
                        WHERE center_id = ?
                        """,
                (rs, i) -> {
                    EnumSet<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
                    ajouterSi(jours, DayOfWeek.MONDAY, rs, "jour_lundi");
                    ajouterSi(jours, DayOfWeek.TUESDAY, rs, "jour_mardi");
                    ajouterSi(jours, DayOfWeek.WEDNESDAY, rs, "jour_mercredi");
                    ajouterSi(jours, DayOfWeek.THURSDAY, rs, "jour_jeudi");
                    ajouterSi(jours, DayOfWeek.FRIDAY, rs, "jour_vendredi");
                    ajouterSi(jours, DayOfWeek.SATURDAY, rs, "jour_samedi");
                    ajouterSi(jours, DayOfWeek.SUNDAY, rs, "jour_dimanche");
                    return new PatientDuMois(new PatientId(rs.getObject("id", UUID.class)), rs.getString("sexe"),
                            date(rs.getObject("date_naissance")), date(rs.getObject("date_admission")),
                            booleen(rs.getObject("en_sommeil")), rs.getString("etat_patient"), date(rs.getObject("date_evenement_etat")), jours);
                },
                centre.valeur());
    }

    @Override
    public List<Demographie> presentsDuMois(CentreId centre, MoisDuTableauDeBord mois) {
        return jdbc.query("""
                        SELECT DISTINCT p.id, p.sexe, p.date_naissance
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')
                        """,
                (rs, i) -> new Demographie(new PatientId(rs.getObject("id", UUID.class)), rs.getString("sexe"),
                        date(rs.getObject("date_naissance"))),
                centre.valeur(), Date.valueOf(mois.premierJour()), Date.valueOf(mois.dernierJour()));
    }

    @Override
    public Map<PatientId, Identite> identites(CentreId centre) {
        Map<PatientId, Identite> identites = new LinkedHashMap<>();
        jdbc.query("SELECT id, nom, prenom FROM patients WHERE center_id = ?",
                (rs, i) -> identites.put(new PatientId(rs.getObject("id", UUID.class)),
                        new Identite(texte(rs.getObject("nom")), texte(rs.getObject("prenom")))),
                centre.valeur());
        return identites;
    }

    private static void ajouterSi(EnumSet<DayOfWeek> jours, DayOfWeek jour, ResultSet rs, String colonne) throws SQLException {
        if (booleen(rs.getObject(colonne))) jours.add(jour);
    }

    private static boolean booleen(Object valeur) {
        if (valeur instanceof Boolean b) return b;
        if (valeur instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate d) return d;
        if (valeur instanceof Date d) return d.toLocalDate();
        return null;
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString();
    }
}
