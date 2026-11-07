package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DetailDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAlerte;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
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

/** Alertes sur la table réelle {@code alertes_observance} (aucune colonne ajoutée). Ordre de lecture : création décroissante, puis identifiant. */
public class AlertesJdbc implements Alertes {

    private static final String COLONNES = "id, patient_id, center_id, type_traitement, type_alerte, periode_debut, periode_fin, doses_attendues, "
            + "doses_administrees, message, unite_dose, dose_prescrite, frequence_valeur, frequence_unite, created_at, resolved_at";

    private final JdbcTemplate jdbc;

    public AlertesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(AlerteDObservance a) {
        jdbc.update("INSERT INTO alertes_observance (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", a.id(), a.patient().valeur(),
                a.centre().valeur(), a.traitement().name(), a.nature().name(), a.debut(), a.fin(), a.attendues(), a.administrees(), a.message(),
                a.detail().unite(), a.detail().dosePrescrite(), a.detail().frequenceValeur(), a.detail().frequenceUnite(), horodatage(a.creeeLe()),
                horodatage(a.resolueLe()));
    }

    @Override
    public void enregistrerLaResolution(AlerteDObservance a) {
        jdbc.update("UPDATE alertes_observance SET resolved_at = ? WHERE id = ? AND patient_id = ? AND center_id = ?", horodatage(a.resolueLe()),
                a.id(), a.patient().valeur(), a.centre().valeur());
    }

    @Override
    public List<AlerteDObservance> duPatient(CentreId centre, PatientId patient) {
        return jdbc.query("SELECT " + COLONNES + " FROM alertes_observance WHERE center_id = ? AND patient_id = ? ORDER BY created_at DESC, id DESC",
                (rs, i) -> alerte(rs), centre.valeur(), patient.valeur());
    }

    @Override
    public Optional<AlerteDObservance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        try {
            return jdbc.query("SELECT " + COLONNES + " FROM alertes_observance WHERE id = ? AND patient_id = ? AND center_id = ? FOR UPDATE",
                    (rs, i) -> alerte(rs), id, patient.valeur(), centre.valeur()).stream().findFirst();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }

    @Override
    public Optional<AlerteDObservance> ouverte(CentreId centre, PatientId patient, TypeDeTraitement traitement, TypeDAlerte nature) {
        return jdbc.query("SELECT " + COLONNES + " FROM alertes_observance WHERE center_id = ? AND patient_id = ? AND type_traitement = ? "
                + "AND type_alerte = ? AND resolved_at IS NULL ORDER BY created_at DESC, id DESC LIMIT 1", (rs, i) -> alerte(rs), centre.valeur(),
                patient.valeur(), traitement.name(), nature.name()).stream().findFirst();
    }

    private static AlerteDObservance alerte(ResultSet rs) throws SQLException {
        var detail = new DetailDObservance(rs.getString("unite_dose"), rs.getObject("dose_prescrite", Integer.class),
                rs.getObject("frequence_valeur", Integer.class), rs.getString("frequence_unite"));
        return AlerteDObservance.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), TypeDeTraitement.valueOf(rs.getString("type_traitement")),
                TypeDAlerte.valueOf(rs.getString("type_alerte")), rs.getObject("periode_debut", LocalDate.class),
                rs.getObject("periode_fin", LocalDate.class), rs.getInt("doses_attendues"), rs.getInt("doses_administrees"),
                rs.getString("message"), detail, instant(rs, "created_at"), instant(rs, "resolved_at"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
