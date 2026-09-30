package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Annulation d'un lot de reprise : seules les données créées par le lot sont supprimées, et uniquement si elles
 * n'ont pas été utilisées depuis (séance, prise en charge, dossier médical, facture…).
 */
@Component
public class MigrationRollbackJdbcAdapter implements MigrationRollbackPort {

    private static final Logger log = LoggerFactory.getLogger(MigrationRollbackJdbcAdapter.class);

    /**
     * Tables rattachées à un patient qui empêchent sa suppression (assure_patient est supprimée avec lui).
     */
    private static final List<String> PATIENT_DATA = List.of("prise_en_charge", "attestation_droit", "seances", "factures",
            "dossier_medical_patient", "prescriptions_medicales", "antecedents_medicaux", "allergies_patient",
            "serologies_patient", "abords_vasculaires", "resultats_analyses", "observations_biologiques", "ordonnances",
            "demandes_examen", "administrations_anemie", "bons_sortie", "bilans_pre_greffe");

    private final JdbcTemplate jdbc;

    public MigrationRollbackJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Set<String> targets(List<IdMapping> mappings, MigrationEntity entity) {
        return mappings.stream().filter(m -> m.entity() == entity).map(IdMapping::targetId).collect(Collectors.toSet());
    }

    @Override
    public List<String> blockers(CenterId centerId, List<IdMapping> created) {
        Set<String> createdPatients = targets(created, MigrationEntity.PATIENTS);
        Set<String> createdAffectations = targets(created, MigrationEntity.AFFECTATIONS);
        List<String> blockers = new ArrayList<>();

        for (IdMapping m : created) {
            if (m.entity() == MigrationEntity.PATIENTS) {
                UUID patientId = UUID.fromString(m.targetId());
                for (String table : PATIENT_DATA) {
                    if (count("SELECT COUNT(*) FROM " + table + " WHERE patient_id = ?", patientId) > 0) {
                        blockers.add("patient « " + m.legacyId() + " » (" + table + ")");
                        break;
                    }
                }
            } else if (m.entity() == MigrationEntity.ASSURES) {
                long links = jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) AS id, CAST(patient_id AS VARCHAR(36)) AS patient_id "
                                + "FROM assure_patient WHERE center_id = ? AND numero_assurance = ?", centerId.value(), m.targetId())
                        .stream()
                        .filter(row -> !createdAffectations.contains(String.valueOf(row.get("id")))
                                && !createdPatients.contains(String.valueOf(row.get("patient_id"))))
                        .count();
                long patients = jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) FROM patients WHERE center_id = ? "
                                + "AND assure_numero_assurance = ?", String.class, centerId.value(), m.targetId())
                        .stream().filter(id -> !createdPatients.contains(id)).count();
                if (links + patients > 0)
                    blockers.add("assuré « " + m.targetId() + " » (rattaché à un patient existant)");
            }
        }
        return blockers;
    }

    @Override
    public void delete(CenterId centerId, List<IdMapping> created) {
        UUID center = centerId.value();
        for (IdMapping m : created) {
            switch (m.entity()) {
                case AFFECTATIONS -> jdbc.update("DELETE FROM assure_patient WHERE id = ? AND center_id = ?",
                        UUID.fromString(m.targetId()), center);
                case PATIENTS -> {
                    UUID patientId = UUID.fromString(m.targetId());
                    jdbc.update("DELETE FROM assure_patient WHERE patient_id = ? AND center_id = ?", patientId, center);
                    jdbc.update("DELETE FROM patients WHERE id = ? AND center_id = ?", patientId, center);
                }
                case ASSURES ->
                        jdbc.update("DELETE FROM assure WHERE numero_assurance = ? AND center_id = ?", m.targetId(), center);
            }
        }
    }

    private long count(String sql, Object... args) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class, args);
            return n == null ? 0 : n;
        } catch (DataAccessException e) {
            // Table absente d'un schéma allégé (ex. H2 de développement sans le module concerné).
            log.debug("Contrôle ignoré : {}", e.getMessage());
            return 0;
        }
    }
}

