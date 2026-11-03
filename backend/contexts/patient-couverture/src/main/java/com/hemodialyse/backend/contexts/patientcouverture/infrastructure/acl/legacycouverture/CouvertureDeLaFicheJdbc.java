package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacycouverture;

import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * ACL du port {@link CouvertureDeLaFiche} vers la table actuelle {@code attestation_droit} : reproduit à l'identique ce que la
 * saisie de la fiche enregistre comme attestation, jusqu'à la reprise de la slice PC4. L'assuré principal (PC2) et la demande de
 * PEC (PC3) sont repris. Retirée avec PC4.
 */
public class CouvertureDeLaFicheJdbc implements CouvertureDeLaFiche {

    private final JdbcTemplate jdbc;

    public CouvertureDeLaFicheJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean attestationEnregistree(CentreId centre, PatientId patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ?",
                Integer.class, centre.valeur(), patient.valeur());
        return n != null && n > 0;
    }

    @Override
    public void enregistrer(CentreId centre, PatientId patient, Couverture couverture, AttestationSaisie attestation) {
        if (attestation != null && attestation.complete()) enregistrerLAttestation(centre, patient, attestation);
    }

    private void enregistrerLAttestation(CentreId centre, PatientId patient, AttestationSaisie a) {
        Integer deja = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ? "
                + "AND date_debut = ? AND date_fin = ?", Integer.class, centre.valeur(), patient.valeur(),
                Date.valueOf(a.debut()), Date.valueOf(a.fin()));
        if (deja != null && deja > 0) return;
        if (a.fin().isBefore(a.debut())) throw new SaisieInvalide("Date fin avant date debut");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) "
                + "VALUES (?,?,?,?,?,?)", a.id() != null ? a.id() : UUID.randomUUID(), patient.valeur(), centre.valeur(),
                Date.valueOf(a.debut()), Date.valueOf(a.fin()), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
