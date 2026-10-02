package com.hemodialyse.backend.infrastructure.persistence.adapter.absence;

import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.domain.absence.model.SourceAbsence;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.absence.model.ValeurAbsence;
import com.hemodialyse.backend.domain.absence.port.AbsencePatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Absences des patients sur la table {@code absence_patient}.
 */
@Component
public class AbsencePatientJdbcAdapter implements AbsencePatientRepositoryPort {

    private static final String COLONNES = "a.id, a.center_id, a.patient_id, a.date_seance, a.source, a.statut, a.motif, "
            + "a.commentaire, a.forfait_id, a.forfait_libelle, a.prix_ttc, a.taux_tva, a.montant_ht, a.declaree_par, "
            + "a.declaree_le, a.qualifiee_par, a.qualifiee_le, a.date_rattrapage, a.modifiee_par, a.modifiee_le";

    private static final RowMapper<AbsencePatient> MAPPER = (rs, i) -> {
        String motif = rs.getString("motif");
        Date rattrapage = rs.getDate("date_rattrapage");
        return new AbsencePatient(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getDate("date_seance").toLocalDate(),
                SourceAbsence.valueOf(rs.getString("source")),
                new ValeurAbsence(rs.getObject("forfait_id", UUID.class), rs.getString("forfait_libelle"),
                        rs.getBigDecimal("prix_ttc"), rs.getBigDecimal("taux_tva"), rs.getBigDecimal("montant_ht")),
                rs.getObject("declaree_par", UUID.class), instant(rs.getTimestamp("declaree_le")),
                StatutAbsence.valueOf(rs.getString("statut")), motif == null ? null : MotifAbsence.valueOf(motif),
                rs.getString("commentaire"), rs.getObject("qualifiee_par", UUID.class),
                instant(rs.getTimestamp("qualifiee_le")), rattrapage == null ? null : rattrapage.toLocalDate(),
                rs.getObject("modifiee_par", UUID.class), instant(rs.getTimestamp("modifiee_le")));
    };

    private final JdbcTemplate jdbc;

    public AbsencePatientJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp timestamp(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    static String nomComplet(String prenom, String nom) {
        if (nom == null) return null;
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }

    @Override
    public AbsencePatient save(AbsencePatient a) {
        ValeurAbsence v = a.valeur();
        int maj = jdbc.update("UPDATE absence_patient SET statut = ?, motif = ?, commentaire = ?, qualifiee_par = ?, "
                        + "qualifiee_le = ?, date_rattrapage = ?, modifiee_par = ?, modifiee_le = ? "
                        + "WHERE id = ? AND center_id = ?",
                a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(), a.qualifieePar(),
                timestamp(a.qualifieeLe()), a.dateRattrapage() == null ? null : Date.valueOf(a.dateRattrapage()),
                a.modifieePar(), timestamp(a.modifieeLe()), a.id(), a.centerId());
        if (maj == 0) {
            jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, "
                            + "commentaire, forfait_id, forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, "
                            + "declaree_le, qualifiee_par, qualifiee_le, date_rattrapage, modifiee_par, modifiee_le) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    a.id(), a.centerId(), a.patientId(), Date.valueOf(a.dateSeance()), a.source().name(),
                    a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(), v.forfaitId(),
                    v.forfaitLibelle(), v.prixTtc(), v.tauxTva(), v.montantHt(), a.declareePar(),
                    timestamp(a.declareeLe()), a.qualifieePar(), timestamp(a.qualifieeLe()),
                    a.dateRattrapage() == null ? null : Date.valueOf(a.dateRattrapage()), a.modifieePar(),
                    timestamp(a.modifieeLe()));
        }
        return a;
    }

    @Override
    public Optional<AbsencePatient> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? AND a.id = ?", MAPPER,
                centerId, id).stream().findFirst();
    }

    @Override
    public Optional<AbsencePatient> findByPatientAndDate(UUID centerId, UUID patientId, LocalDate dateSeance) {
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a "
                        + "WHERE a.center_id = ? AND a.patient_id = ? AND a.date_seance = ?", MAPPER, centerId,
                patientId, Date.valueOf(dateSeance)).stream().findFirst();
    }

    @Override
    public PagedResult<AbsenceLigne> findPaged(UUID centerId, AbsenceFiltre f, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE a.center_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(centerId);
        if (f.statut() != null) {
            where.append(" AND a.statut = ?");
            args.add(f.statut().name());
        }
        if (f.motif() != null) {
            where.append(" AND a.motif = ?");
            args.add(f.motif().name());
        }
        if (f.patientId() != null) {
            where.append(" AND a.patient_id = ?");
            args.add(f.patientId());
        }
        if (f.from() != null) {
            where.append(" AND a.date_seance >= ?");
            args.add(Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            where.append(" AND a.date_seance <= ?");
            args.add(Date.valueOf(f.to()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AbsenceLigne> items = jdbc.query("SELECT " + COLONNES + ", p.nom AS p_nom, p.prenom AS p_prenom "
                        + "FROM absence_patient a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id"
                        + where + " ORDER BY a.date_seance DESC, a.id LIMIT ? OFFSET ?",
                (rs, i) -> new AbsenceLigne(MAPPER.mapRow(rs, i), nomComplet(rs.getString("p_prenom"), rs.getString("p_nom"))),
                pageArgs.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public List<AbsencePatient> findBetween(UUID centerId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? "
                        + "AND a.date_seance BETWEEN ? AND ? AND a.statut <> 'ANNULEE' ORDER BY a.date_seance, a.id",
                MAPPER, centerId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public long countAQualifier(UUID centerId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ?",
                Long.class, centerId, StatutAbsence.A_QUALIFIER.name());
        return n == null ? 0 : n;
    }

    @Override
    public long countEnRetard(UUID centerId, LocalDate limite) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = ? "
                        + "AND date_seance < ?", Long.class, centerId, StatutAbsence.A_QUALIFIER.name(),
                Date.valueOf(limite));
        return n == null ? 0 : n;
    }
}
