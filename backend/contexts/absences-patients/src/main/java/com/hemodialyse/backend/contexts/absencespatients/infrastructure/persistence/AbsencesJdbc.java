package com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Absences enregistrées sur la table {@code absence_patient}. Les verrous sont ceux de la ligne ({@code SELECT ... FOR UPDATE},
 * gardés jusqu'à la fin de la transaction du cas d'usage). La valeur (forfait, montants, taux) n'est écrite qu'à la création :
 * aucune mise à jour ne la touche. Deux contraintes de la base tiennent les invariants d'ensemble : une absence par patient et
 * par jour ({@code UNIQUE (center_id, patient_id, date_seance)}) et, sur PostgreSQL, une séance ne rattrape qu'une absence
 * (index unique partiel sur les absences {@code RATTRAPEE}, migration V43).
 */
public class AbsencesJdbc implements AbsencesRepository {

    private static final String COLONNES = "a.id, a.center_id, a.patient_id, a.date_seance, a.source, a.statut, a.motif, "
            + "a.commentaire, a.forfait_id, a.forfait_libelle, a.prix_ttc, a.taux_tva, a.montant_ht, a.declaree_par, "
            + "a.declaree_le, a.qualifiee_par, a.qualifiee_le, a.date_rattrapage, a.modifiee_par, a.modifiee_le";

    private static final RowMapper<AbsencePatient> ABSENCE = (rs, i) -> {
        String motif = rs.getString("motif");
        Date rattrapage = rs.getDate("date_rattrapage");
        UUID forfait = rs.getObject("forfait_id", UUID.class);
        return AbsencePatient.reconstituer(new AbsenceId(rs.getObject("id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), new PatientId(rs.getObject("patient_id", UUID.class)),
                rs.getDate("date_seance").toLocalDate(), SourceAbsence.valueOf(rs.getString("source")),
                new Valorisation(forfait == null ? null : new ForfaitId(forfait), rs.getString("forfait_libelle"),
                        rs.getBigDecimal("montant_ht"), rs.getBigDecimal("taux_tva"), rs.getBigDecimal("prix_ttc")),
                utilisateur(rs.getObject("declaree_par", UUID.class)), instant(rs.getTimestamp("declaree_le")),
                StatutAbsence.valueOf(rs.getString("statut")), motif == null ? null : MotifAbsence.valueOf(motif),
                rs.getString("commentaire"), utilisateur(rs.getObject("qualifiee_par", UUID.class)),
                instant(rs.getTimestamp("qualifiee_le")), rattrapage == null ? null : rattrapage.toLocalDate(),
                utilisateur(rs.getObject("modifiee_par", UUID.class)), instant(rs.getTimestamp("modifiee_le")));
    };

    private final JdbcTemplate jdbc;

    public AbsencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static UtilisateurId utilisateur(UUID valeur) {
        return valeur == null ? null : new UtilisateurId(valeur);
    }

    private static UUID valeur(UtilisateurId utilisateur) {
        return utilisateur == null ? null : utilisateur.valeur();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp timestamp(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    /**
     * Un verrou qu'on ne peut pas prendre (délai dépassé, interblocage détecté) est une écriture concurrente : 409.
     */
    private static <T> T verrouillant(Supplier<T> lecture) {
        return RefusDeVerrou.verrouillant(lecture);
    }

    /**
     * Spring ne classe pas toujours le refus de verrou : PostgreSQL 55P03 (lock_timeout) arrive en exception SQL non catégorisée. Tout autre
     * échec est relancé tel quel. Rend la main (donc lève {@link EcritureConcurrente}) dans les cas de verrou, de doublon et de délai.
     */
    private static void siVerrouImpossibleSinonRelancer(RuntimeException e) {
        RefusDeVerrou.siVerrouImpossibleSinonRelancer(e);
    }

    @Override
    public Optional<AbsencePatient> verrouiller(CentreId centre, AbsenceId id) {
        return verrouillant(() -> jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? "
                + "AND a.id = ? FOR UPDATE", ABSENCE, centre.valeur(), id.valeur()).stream().findFirst());
    }

    @Override
    public Optional<AbsenceEtRattrapages> verrouillerPourRattrapage(CentreId centre, AbsenceId id) {
        List<AbsencePatient> duPatient = verrouillant(() -> jdbc.query("SELECT " + COLONNES + " FROM absence_patient a "
                + "WHERE a.center_id = ? AND a.patient_id = (SELECT b.patient_id FROM absence_patient b "
                + "WHERE b.center_id = ? AND b.id = ?) ORDER BY a.id FOR UPDATE", ABSENCE, centre.valeur(),
                centre.valeur(), id.valeur()));
        return duPatient.stream().filter(a -> a.id().equals(id)).findFirst().map(absence -> new AbsenceEtRattrapages(absence,
                duPatient.stream().filter(a -> a.statut() == StatutAbsence.RATTRAPEE && a.dateDeRattrapage() != null)
                        .map(AbsencePatient::dateDeRattrapage).toList()));
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND patient_id = ? "
                + "AND date_seance = ?", Integer.class, centre.valeur(), patient.valeur(), Date.valueOf(jour));
        return n != null && n > 0;
    }

    @Override
    public List<AbsencePatient> detecteesAQualifierEntre(CentreId centre, LocalDate du, LocalDate au, AbsenceId apres,
                                                         int limite) {
        String curseur = apres == null ? "" : "AND a.id > ? ";
        List<Object> arguments = new java.util.ArrayList<>(List.of(centre.valeur(), SourceAbsence.AUTOMATIQUE.name(),
                StatutAbsence.A_QUALIFIER.name(), Date.valueOf(du), Date.valueOf(au)));
        if (apres != null) {
            arguments.add(apres.valeur());
        }
        arguments.add(limite);
        return jdbc.query("SELECT " + COLONNES + " FROM absence_patient a WHERE a.center_id = ? AND a.source = ? "
                + "AND a.statut = ? AND a.date_seance BETWEEN ? AND ? " + curseur + "ORDER BY a.id LIMIT ?", ABSENCE,
                arguments.toArray());
    }

    @Override
    public void creer(AbsencePatient a) {
        Valorisation v = a.valorisation();
        try {
            jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, "
                            + "commentaire, forfait_id, forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, "
                            + "declaree_le, qualifiee_par, qualifiee_le, date_rattrapage, modifiee_par, modifiee_le) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    a.id().valeur(), a.centre().valeur(), a.patient().valeur(), Date.valueOf(a.dateDeSeance()),
                    a.source().name(), a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(),
                    v.forfait() == null ? null : v.forfait().valeur(), v.libelleDuForfait(), v.montantTtc(), v.tauxDeTva(),
                    v.montantHt(), valeur(a.declareePar()), timestamp(a.declareeLe()), valeur(a.qualifieePar()),
                    timestamp(a.qualifieeLe()), date(a.dateDeRattrapage()), valeur(a.modifieePar()),
                    timestamp(a.modifieeLe()));
        } catch (DuplicateKeyException | PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            siVerrouImpossibleSinonRelancer(e);
            throw new EcritureConcurrente();
        }
    }

    @Override
    public void enregistrer(AbsencePatient a) {
        int modifiees;
        try {
            modifiees = jdbc.update("UPDATE absence_patient SET statut = ?, motif = ?, commentaire = ?, qualifiee_par = ?, "
                            + "qualifiee_le = ?, date_rattrapage = ?, modifiee_par = ?, modifiee_le = ? "
                            + "WHERE id = ? AND center_id = ?",
                    a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(),
                    valeur(a.qualifieePar()), timestamp(a.qualifieeLe()), date(a.dateDeRattrapage()),
                    valeur(a.modifieePar()), timestamp(a.modifieeLe()), a.id().valeur(), a.centre().valeur());
        } catch (DuplicateKeyException | PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            siVerrouImpossibleSinonRelancer(e);
            throw new EcritureConcurrente();
        }
        if (modifiees == 0) {
            throw new EcritureConcurrente();
        }
    }

    @Override
    public void supprimer(CentreId centre, AbsenceId id) {
        jdbc.update("DELETE FROM absence_patient WHERE id = ? AND center_id = ?", id.valeur(), centre.valeur());
    }
}
