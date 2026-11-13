package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.DonneesDeLaReprise;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Adapter de {@link DonneesDeLaReprise} sur les tables actuelles (RP1, ADR 0169) : requêtes de l'annulation d'un lot reprises de
 * l'outil de migration, sans changement. Les lectures des prises en charge et des attestations ne sont pas bornées au centre (comme
 * avant) ; toutes les suppressions le sont.
 */
public class DonneesDeLaRepriseJdbc implements DonneesDeLaReprise {

    private final JdbcTemplate jdbc;

    public DonneesDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> prisesEnChargeDuPatient(UUID patient) {
        return jdbc.query("SELECT id FROM prise_en_charge WHERE patient_id = ?", (rs, n) -> rs.getObject("id", UUID.class), patient);
    }

    @Override
    public List<UUID> attestationsDuPatient(UUID patient) {
        return jdbc.query("SELECT id FROM attestation_droit WHERE patient_id = ?", (rs, n) -> rs.getObject("id", UUID.class), patient);
    }

    @Override
    public List<AffectationDeLAssure> affectationsDeLAssure(UUID centre, String numeroAssurance) {
        return jdbc.query("SELECT id, patient_id FROM assure_patient WHERE center_id = ? AND numero_assurance = ?",
                (rs, n) -> new AffectationDeLAssure(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class)),
                centre, numeroAssurance);
    }

    @Override
    public List<UUID> patientsDeLAssure(UUID centre, String numeroAssurance) {
        return jdbc.query("SELECT id FROM patients WHERE center_id = ? AND assure_numero_assurance = ?",
                (rs, n) -> rs.getObject("id", UUID.class), centre, numeroAssurance);
    }

    @Override
    public void supprimerLAffectation(UUID centre, UUID affectation) {
        jdbc.update("DELETE FROM assure_patient WHERE id = ? AND center_id = ?", affectation, centre);
    }

    @Override
    public void supprimerLesAffectationsDuPatient(UUID centre, UUID patient) {
        jdbc.update("DELETE FROM assure_patient WHERE patient_id = ? AND center_id = ?", patient, centre);
    }

    @Override
    public void supprimerLePatient(UUID centre, UUID patient) {
        jdbc.update("DELETE FROM patients WHERE id = ? AND center_id = ?", patient, centre);
    }

    @Override
    public void supprimerLAssure(UUID centre, String numeroAssurance) {
        jdbc.update("DELETE FROM assure WHERE numero_assurance = ? AND center_id = ?", numeroAssurance, centre);
    }
}
