package com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture du suivi sur la table {@code absence_patient} uniquement (le nom des patients passe par l'ACL
 * {@code NomsDesPatients}). Mêmes requêtes que le legacy (slice A1).
 */
public class LectureDesAbsencesJdbc implements LectureDesAbsences {

    private static final String COLONNES = "a.id, a.center_id, a.patient_id, a.date_seance, a.source, a.statut, a.motif, "
            + "a.commentaire, a.forfait_libelle, a.prix_ttc, a.taux_tva, a.montant_ht, a.declaree_le, a.qualifiee_le, "
            + "a.date_rattrapage";

    private static final RowMapper<AbsenceSuivie> ABSENCE = (rs, i) -> {
        String motif = rs.getString("motif");
        Date rattrapage = rs.getDate("date_rattrapage");
        return new AbsenceSuivie(new AbsenceId(rs.getObject("id", UUID.class)),
                new PatientId(rs.getObject("patient_id", UUID.class)), rs.getDate("date_seance").toLocalDate(),
                SourceAbsence.valueOf(rs.getString("source")), StatutAbsence.valueOf(rs.getString("statut")),
                motif == null ? null : MotifAbsence.valueOf(motif), rs.getString("commentaire"),
                rs.getString("forfait_libelle"), rs.getBigDecimal("prix_ttc"), rs.getBigDecimal("taux_tva"),
                rs.getBigDecimal("montant_ht"), instant(rs.getTimestamp("declaree_le")),
                instant(rs.getTimestamp("qualifiee_le")), rattrapage == null ? null : rattrapage.toLocalDate());
    };

    private final JdbcTemplate jdbc;

    public LectureDesAbsencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    @Override
    public PageDeResultats<AbsenceSuivie> lister(CentreId centre, FiltreDesAbsences f, Pagination pagination) {
        StringBuilder where = new StringBuilder(" WHERE a.center_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(centre.valeur());
        if (f.statut() != null) {
            where.append(" AND a.statut = ?");
            args.add(f.statut().name());
        }
        if (f.motif() != null) {
            where.append(" AND a.motif = ?");
            args.add(f.motif().name());
        }
        if (f.patient() != null) {
            where.append(" AND a.patient_id = ?");
            args.add(f.patient().valeur());
        }
        if (f.du() != null) {
            where.append(" AND a.date_seance >= ?");
            args.add(Date.valueOf(f.du()));
        }
        if (f.au() != null) {
            where.append(" AND a.date_seance <= ?");
            args.add(Date.valueOf(f.au()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient a" + where, Long.class, args.toArray());
        List<Object> argsDeLaPage = new ArrayList<>(args);
        argsDeLaPage.add(pagination.taille());
        argsDeLaPage.add(pagination.decalage());
        List<AbsenceSuivie> lignes = jdbc.query("SELECT " + COLONNES + " FROM absence_patient a" + where
                        + " ORDER BY a.date_seance DESC, a.id LIMIT ? OFFSET ?", ABSENCE, argsDeLaPage.toArray());
        return new PageDeResultats<>(lignes, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public List<AbsenceSuivie> absencesNonAnnuleesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? "
                        + "AND a.date_seance BETWEEN ? AND ? AND a.statut <> 'ANNULEE' ORDER BY a.date_seance, a.id",
                ABSENCE, centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public long nombreAQualifier(CentreId centre) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ?",
                Long.class, centre.valeur(), StatutAbsence.A_QUALIFIER.name());
        return n == null ? 0 : n;
    }

    @Override
    public long nombreAQualifierAvant(CentreId centre, LocalDate limite) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ? "
                        + "AND date_seance < ?", Long.class, centre.valeur(), StatutAbsence.A_QUALIFIER.name(),
                Date.valueOf(limite));
        return n == null ? 0 : n;
    }

    @Override
    public long nombreAQualifierEntre(CentreId centre, LocalDate du, LocalDate au) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ? "
                        + "AND date_seance BETWEEN ? AND ?", Long.class, centre.valeur(), StatutAbsence.A_QUALIFIER.name(),
                Date.valueOf(du), Date.valueOf(au));
        return n == null ? 0 : n;
    }

    @Override
    public long nombreAQualifierDansLesMois(CentreId centre, java.util.Collection<java.time.YearMonth> mois, LocalDate avant) {
        if (mois.isEmpty()) {
            return 0;
        }
        String jokers = String.join(", ", java.util.Collections.nCopies(mois.size(), "?"));
        java.util.List<Object> arguments = new java.util.ArrayList<>(List.of(centre.valeur(), StatutAbsence.A_QUALIFIER.name()));
        mois.forEach(m -> arguments.add(m.getYear() * 12 + m.getMonthValue()));
        String borne = "";
        if (avant != null) {
            borne = " AND date_seance < ?";
            arguments.add(Date.valueOf(avant));
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ? "
                        + "AND (EXTRACT(YEAR FROM date_seance) * 12 + EXTRACT(MONTH FROM date_seance)) IN (" + jokers + ")" + borne,
                Long.class, arguments.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public Optional<AbsenceSuivie> absence(CentreId centre, AbsenceId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? AND a.id = ?", ABSENCE,
                centre.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public List<LocalDate> datesDeRattrapageUtilisees(CentreId centre, PatientId patient) {
        return jdbc.query("SELECT date_rattrapage FROM absence_patient WHERE center_id = ? AND patient_id = ? "
                        + "AND statut = ? AND date_rattrapage IS NOT NULL",
                (rs, i) -> rs.getDate(1).toLocalDate(), centre.valeur(), patient.valeur(),
                StatutAbsence.RATTRAPEE.name());
    }
}
