package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Annulation d'un lot de reprise : seules les données créées par le lot sont supprimées, et uniquement si elles
 * n'ont pas été utilisées depuis (séance facturée ou complétée, facture de reprise encaissée, données saisies
 * dans la plateforme pour un patient repris…). Les données créées par le même lot ne bloquent jamais.
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

    /**
     * Lignes créées par le lot, par table (elles ne bloquent pas la suppression de leur patient).
     */
    private static Map<String, Set<String>> createdByTable(List<IdMapping> created) {
        Map<String, Set<String>> out = new HashMap<>();
        for (IdMapping m : created) {
            String table = m.entity() == MigrationEntity.SOLDES_OUVERTURE ? "factures"
                    : HistoricalRecordJdbcAdapter.TABLES.containsKey(m.entity())
                    ? HistoricalRecordJdbcAdapter.TABLES.get(m.entity()).name() : null;
            if (table != null) out.computeIfAbsent(table, t -> new HashSet<>()).add(m.targetId().toLowerCase());
        }
        return out;
    }

    private static Set<String> targets(List<IdMapping> mappings, MigrationEntity entity) {
        return mappings.stream().filter(m -> m.entity() == entity).map(IdMapping::targetId).collect(Collectors.toSet());
    }

    @Override
    public List<String> blockers(CenterId centerId, List<IdMapping> created) {
        Map<String, Set<String>> createdByTable = createdByTable(created);
        Set<String> createdPatients = targets(created, MigrationEntity.PATIENTS);
        Set<String> createdAffectations = targets(created, MigrationEntity.AFFECTATIONS);
        List<String> blockers = new ArrayList<>();

        for (IdMapping m : created) {
            switch (m.entity()) {
                case PATIENTS -> {
                    UUID patientId = UUID.fromString(m.targetId());
                    for (String table : PATIENT_DATA) {
                        Set<String> ownRows = createdByTable.getOrDefault(table, Set.of());
                        boolean usedElsewhere = ids("SELECT CAST(id AS VARCHAR(36)) FROM " + table + " WHERE patient_id = ?", patientId)
                                .stream().anyMatch(id -> !ownRows.contains(id));
                        if (usedElsewhere) {
                            blockers.add("patient « " + m.legacyId() + " » (" + table + ")");
                            break;
                        }
                    }
                }
                case ASSURES -> {
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
                case SEANCES -> {
                    UUID seanceId = UUID.fromString(m.targetId());
                    boolean billed = !ids("SELECT CAST(facture_id AS VARCHAR(36)) FROM seances WHERE id = ? AND facture_id IS NOT NULL",
                            seanceId).isEmpty();
                    boolean completed = !ids("SELECT CAST(id AS VARCHAR(36)) FROM volet_medical WHERE seance_id = ?", seanceId).isEmpty()
                            || !ids("SELECT CAST(id AS VARCHAR(36)) FROM volet_paramedical WHERE seance_id = ?", seanceId).isEmpty();
                    if (billed || completed) {
                        blockers.add("séance « " + m.legacyId() + " » (" + (billed ? "facturée" : "complétée") + " dans la plateforme)");
                    }
                }
                case SOLDES_OUVERTURE -> {
                    Long payments = jdbc.queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ? "
                                    + "AND (saisi_par IS NULL OR saisi_par <> ?)", Long.class,
                            UUID.fromString(m.targetId()), OpeningBalancePort.MIGRATION_AUTHOR);
                    if (payments != null && payments > 0) {
                        blockers.add("facture « " + m.legacyId() + " » (encaissée dans la plateforme)");
                    }
                }
                default -> {
                    // Attestations, PEC, dossier médical… : supprimables sans condition.
                }
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
                case SOLDES_OUVERTURE -> {
                    UUID factureId = UUID.fromString(m.targetId());
                    jdbc.update("DELETE FROM facture_reglements WHERE facture_id = ? AND center_id = ?", factureId, center);
                    jdbc.update("DELETE FROM facture_lignes WHERE facture_id = ? AND center_id = ?", factureId, center);
                    jdbc.update("DELETE FROM factures WHERE id = ? AND center_id = ?", factureId, center);
                }
                default -> jdbc.update("DELETE FROM " + HistoricalRecordJdbcAdapter.table(m.entity()).name()
                        + " WHERE id = ? AND center_id = ?", UUID.fromString(m.targetId()), center);
            }
        }
    }

    private List<String> ids(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, String.class, args).stream().map(String::toLowerCase).toList();
        } catch (DataAccessException e) {
            // Table absente d'un schéma allégé (ex. H2 de développement sans le module concerné).
            log.debug("Contrôle ignoré : {}", e.getMessage());
            return List.of();
        }
    }
}

