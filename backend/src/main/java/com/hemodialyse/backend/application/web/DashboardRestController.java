package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardRestController {

    private final JdbcTemplate jdbc;

    public DashboardRestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestParam UUID centerId,
                                   @RequestParam(defaultValue = "30") int expirationDays) {
        LocalDate threshold = LocalDate.now().plusDays(expirationDays);

        // PEC counts
        long pecCree = countPec(centerId, "CREE");
        long pecValidee = countPec(centerId, "VALIDEE");
        long pecExpiring = countPecExpiring(centerId, threshold);

        // Attestation counts
        long attestationTotal = countAttestations(centerId);
        long attestationExpiring = countAttestationsExpiring(centerId, threshold);

        // Patient count
        long patientCount = countPatients(centerId);

        return ResponseEntity.ok(Map.of(
                "pecCree", pecCree,
                "pecValidee", pecValidee,
                "pecExpiring", pecExpiring,
                "attestationTotal", attestationTotal,
                "attestationExpiring", attestationExpiring,
                "patientCount", patientCount,
                "expirationDays", expirationDays
        ));
    }

    private long countPec(UUID centerId, String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pec WHERE center_id = ? AND statut = ?",
                Long.class, centerId, status
        );
        return count != null ? count : 0;
    }

    private long countPecExpiring(UUID centerId, LocalDate threshold) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM pec WHERE center_id = ? AND statut = 'VALIDEE' AND date_fin_demande <= ?",
                Long.class, centerId, threshold
        );
        return count != null ? count : 0;
    }

    private long countAttestations(UUID centerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM attestation WHERE center_id = ?",
                Long.class, centerId
        );
        return count != null ? count : 0;
    }

    private long countAttestationsExpiring(UUID centerId, LocalDate threshold) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM attestation WHERE center_id = ? AND date_fin <= ?",
                Long.class, centerId, threshold
        );
        return count != null ? count : 0;
    }

    private long countPatients(UUID centerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM patient WHERE center_id = ?",
                Long.class, centerId
        );
        return count != null ? count : 0;
    }
}

