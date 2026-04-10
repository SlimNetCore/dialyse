package com.hemodialyse.backend.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application read-model service used by controllers.
 * Keeps SQL/JdbcTemplate out of web layer.
 */
@Service
public class PecReadQueryService {

    private final JdbcTemplate jdbc;

    public PecReadQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listAttestationsByCenter(UUID centerId) {
        return jdbc.queryForList(
            "SELECT a.id, a.patient_id, a.date_debut, a.date_fin, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
            "FROM attestation_droit a " +
            "JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id " +
            "WHERE a.center_id = ? ORDER BY a.date_fin DESC",
            centerId
        );
    }

    public List<Map<String, Object>> listPecByCenterDetailed(UUID centerId) {
        return jdbc.queryForList(
            "SELECT pc.id, pc.patient_id, pc.date_debut_demande, pc.date_fin_demande, pc.statut, " +
            "pc.date_debut_effectif, pc.date_fin_effectif, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
            "FROM prise_en_charge pc " +
            "JOIN patients p ON p.id = pc.patient_id AND p.center_id = pc.center_id " +
            "WHERE pc.center_id = ? ORDER BY pc.date_fin_demande DESC",
            centerId
        );
    }
}

