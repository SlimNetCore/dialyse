package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.DerogationHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitImpose;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les séances sur la table RÉELLE {@code seances} (ADR 0024 et 0088 : aucune colonne ni contrainte ajoutée, aucune autre table lue). La
 * création écrit neuf colonnes (identité, centre, patient, jour, statut, création, hors planning, motif, précision) ; les autres colonnes
 * (validation, signatures, forfait surchargé, place, régularisation, facture, exclusion) appartiennent aux slices SE2 à SE6 et restent
 * NULL à la création, comme le legacy. Toute lecture est bornée au centre. Une ligne dont le statut ou le motif ne se lit pas lève
 * {@link SeanceIllisible} (KB-SEA-07) ; l'identifiant sert au journal, jamais un nom de classe.
 * <p>
 * Verrou du jour : {@code pg_advisory_xact_lock} sur (centre, patient, jour) sur PostgreSQL, gardé jusqu'à la fin de la transaction ; un
 * verrou non obtenu dans le délai (SQLSTATE 55P03) devient {@link EcritureConcurrente}. Clé littérale préfixée {@code seance-du-jour|} :
 * tout autre verrou consultatif du dépôt doit avoir son propre préfixe.
 */
public class SeancesJdbc implements Seances {

    private static final String COLONNES = "id, center_id, patient_id, date_seance, statut, created_at, hors_planning, motif_hors_planning, "
            + "precision_hors_planning, validated_at, signed_infirmier_at, signed_medecin_at, regularisation_deverrouillee_at, "
            + "signed_infirmier_by, signed_medecin_by, regularisation_deverrouillee_by, salle_id, creneau_id, generateur_id, "
            + "forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix, forfait_override_updated_at, "
            + "forfait_override_updated_by";

    private final JdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public SeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(Seance s) {
        var derogation = s.derogation();
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at, hors_planning, motif_hors_planning, "
                        + "precision_hors_planning) VALUES (?,?,?,?,?,?,?,?,?)", s.id().valeur(), s.centre().valeur(), s.patient().valeur(),
                java.sql.Date.valueOf(s.jour()), s.statut().name(), horodatage(s.creeLe()), s.horsPlanning(),
                derogation.map(d -> d.motif().name()).orElse(null), derogation.map(DerogationHorsPlanning::precision).orElse(null));
    }

    @Override
    public void verrouillerLeJour(CentreId centre, PatientId patient, LocalDate jour) {
        if (!estPostgreSql()) return;
        try {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
            }, "seance-du-jour|" + centre.valeur() + "|" + patient.valeur() + "|" + jour);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            throw refusDeVerrou(e);
        }
    }

    /** Un refus de verrou (délai dépassé, SQLSTATE 55P03) devient {@link EcritureConcurrente} ; toute autre erreur technique est relancée. */
    private static RuntimeException refusDeVerrou(RuntimeException e) {
        if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) return e;
        return new EcritureConcurrente();
    }

    @Override
    public Optional<Seance> trouverPourModifier(CentreId centre, SeanceId id) {
        try {
            return jdbc.query("SELECT " + COLONNES + " FROM seances WHERE id = ? AND center_id = ? FOR UPDATE", (rs, i) -> seance(rs),
                    id.valeur(), centre.valeur()).stream().findFirst();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            throw refusDeVerrou(e);
        }
    }

    @Override
    public void enregistrer(Seance s) {
        Seance.Jalons j = s.jalons();
        Seance.Traces t = s.traces();
        var place = t.place();
        int lignes = jdbc.update("UPDATE seances SET statut = ?, validated_at = ?, signed_infirmier_at = ?, signed_medecin_at = ?, "
                        + "regularisation_deverrouillee_at = ?, signed_infirmier_by = ?, signed_medecin_by = ?, "
                        + "regularisation_deverrouillee_by = ?, salle_id = ?, creneau_id = ?, generateur_id = ? WHERE id = ? AND center_id = ?",
                s.statut().name(), horodatage(j.valideeLe()), horodatage(j.signeeParLInfirmierLe()), horodatage(j.signeeParLeMedecinLe()),
                horodatage(j.deverrouilleePourRegularisationLe()), t.infirmier(), t.medecin(), t.deverrouilleePar(),
                place == null ? null : place.salle(), place == null ? null : place.creneau(), place == null ? null : place.generateur(),
                s.id().valeur(), s.centre().valeur());
        if (lignes != 1) throw new IllegalStateException("Séance " + s.id().valeur() + " introuvable à l'enregistrement");
    }

    @Override
    public void enregistrerLeForfaitImpose(Seance s) {
        ForfaitImpose impose = s.forfaitImpose().orElseThrow(() -> new IllegalStateException("Aucun forfait imposé à enregistrer"));
        int lignes = jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = ?, forfait_override_nom = ?, "
                        + "forfait_override_prix = ?, forfait_override_updated_at = ?, forfait_override_updated_by = ? "
                        + "WHERE id = ? AND center_id = ?", impose.forfait().valeur(), impose.code(), impose.nom(), impose.prix(),
                horodatage(impose.imposeLe()), impose.auteur(), s.id().valeur(), s.centre().valeur());
        if (lignes != 1) throw new IllegalStateException("Séance " + s.id().valeur() + " introuvable à l'enregistrement du forfait");
    }

    @Override
    public List<Seance> duJour(CentreId centre, PatientId patient, LocalDate jour) {
        return jdbc.query("SELECT " + COLONNES + " FROM seances WHERE center_id = ? AND patient_id = ? AND date_seance = ? "
                        + "ORDER BY created_at ASC NULLS LAST, id ASC", (rs, i) -> seance(rs), centre.valeur(), patient.valeur(),
                java.sql.Date.valueOf(jour));
    }

    @Override
    public Optional<Seance> trouver(CentreId centre, SeanceId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM seances WHERE id = ? AND center_id = ?", (rs, i) -> seance(rs), id.valeur(),
                centre.valeur()).stream().findFirst();
    }

    private static Seance seance(ResultSet rs) throws SQLException {
        SeanceId id = new SeanceId(rs.getObject("id", UUID.class));
        StatutDeSeance statut;
        try {
            statut = StatutDeSeance.valueOf(rs.getString("statut"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SeanceIllisible(id, "statut inconnu");
        }
        DerogationHorsPlanning derogation = null;
        if (rs.getBoolean("hors_planning")) {
            try {
                derogation = new DerogationHorsPlanning(MotifHorsPlanning.valueOf(rs.getString("motif_hors_planning")),
                        rs.getString("precision_hors_planning"), false);
            } catch (RuntimeException e) {
                throw new SeanceIllisible(id, "motif de hors planning illisible");
            }
        }
        return Seance.restituer(id, new CentreId(rs.getObject("center_id", UUID.class)),
                new PatientId(rs.getObject("patient_id", UUID.class)), rs.getObject("date_seance", LocalDate.class), statut,
                instant(rs, "created_at"), derogation, new Seance.Jalons(instant(rs, "validated_at"), instant(rs, "signed_infirmier_at"),
                        instant(rs, "signed_medecin_at"), instant(rs, "regularisation_deverrouillee_at")),
                new Seance.Traces(rs.getString("signed_infirmier_by"), rs.getString("signed_medecin_by"),
                        rs.getString("regularisation_deverrouillee_by"), place(rs)), forfaitImpose(rs));
    }

    /** Le forfait imposé : présent dès que {@code forfait_override_id} l'est (code, instant et auteur peuvent manquer : Facturation). */
    private static ForfaitImpose forfaitImpose(ResultSet rs) throws SQLException {
        UUID forfait = rs.getObject("forfait_override_id", UUID.class);
        if (forfait == null) return null;
        return new ForfaitImpose(new ForfaitId(forfait), rs.getString("forfait_override_code"), rs.getString("forfait_override_nom"),
                rs.getBigDecimal("forfait_override_prix"), instant(rs, "forfait_override_updated_at"),
                rs.getString("forfait_override_updated_by"));
    }

    /** La place mémorisée : salle ET créneau renseignés, sinon aucune (le générateur est facultatif). */
    private static PlaceDeSeance place(ResultSet rs) throws SQLException {
        UUID salle = rs.getObject("salle_id", UUID.class);
        UUID creneau = rs.getObject("creneau_id", UUID.class);
        return salle == null || creneau == null ? null : new PlaceDeSeance(salle, creneau, rs.getObject("generateur_id", UUID.class));
    }

    private boolean estPostgreSql() {
        Boolean lu = postgreSql;
        if (lu == null) {
            lu = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
            postgreSql = lu;
        }
        return lu;
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }
}
