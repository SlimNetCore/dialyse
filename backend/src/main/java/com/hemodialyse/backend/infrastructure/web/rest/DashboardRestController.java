package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.web.dto.request.DashboardSearchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardRestController {

    private final JdbcTemplate jdbc;

    public DashboardRestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestParam UUID centerId,
                                   @RequestParam(defaultValue = "30") int expirationDays,
                                   @RequestParam(required = false) String month) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate threshold = LocalDate.now().plusDays(expirationDays);

        long pecCree = countPec(centerId, "CREE", yearMonth);
        long pecValidee = countPec(centerId, "VALIDEE", yearMonth);
        long pecExpiring = countPecExpiring(centerId, threshold);

        long attestationTotal = countAttestations(centerId, yearMonth);
        long attestationExpiring = countAttestationsExpiring(centerId, threshold);

        long patientCount = countPatients(centerId, yearMonth);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pecCree", pecCree);
        response.put("pecValidee", pecValidee);
        response.put("pecExpiring", pecExpiring);
        response.put("attestationTotal", attestationTotal);
        response.put("attestationExpiring", attestationExpiring);
        response.put("patientCount", patientCount);
        response.put("expirationDays", expirationDays);
        if (yearMonth != null) {
            response.put("month", yearMonth.toString());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stats/search")
    public ResponseEntity<?> searchStats(@RequestBody @Valid DashboardSearchRequest criteria) {
        int expirationDays = criteria.expirationDays() != null ? criteria.expirationDays() : 30;
        YearMonth yearMonth = parseMonth(criteria.month());
        LocalDate threshold = LocalDate.now().plusDays(expirationDays);

        long pecCree = countPec(criteria.centerId(), "CREE", yearMonth);
        long pecValidee = countPec(criteria.centerId(), "VALIDEE", yearMonth);
        long pecExpiring = countPecExpiring(criteria.centerId(), threshold);

        long attestationTotal = countAttestations(criteria.centerId(), yearMonth);
        long attestationExpiring = countAttestationsExpiring(criteria.centerId(), threshold);

        long patientCount = countPatients(criteria.centerId(), yearMonth);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pecCree", pecCree);
        response.put("pecValidee", pecValidee);
        response.put("pecExpiring", pecExpiring);
        response.put("attestationTotal", attestationTotal);
        response.put("attestationExpiring", attestationExpiring);
        response.put("patientCount", patientCount);
        response.put("expirationDays", expirationDays);
        if (yearMonth != null) {
            response.put("month", yearMonth.toString());
        }
        return ResponseEntity.ok(response);
    }

    // ---- Helpers ----

    /**
     * Parse "YYYY-MM" → YearMonth, returns null if absent or invalid (graceful degradation).
     */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private long countPec(UUID centerId, String status, YearMonth month) {
        if (month == null) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = ?",
                    Long.class, centerId, status);
            return count != null ? count : 0;
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centerId, status, start, end);
        return count != null ? count : 0;
    }

    private long countPecExpiring(UUID centerId, LocalDate threshold) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = 'VALIDEE' AND date_fin_demande <= ?",
                Long.class, centerId, threshold);
        return count != null ? count : 0;
    }

    private long countAttestations(UUID centerId, YearMonth month) {
        if (month == null) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM attestation_droit WHERE center_id = ?",
                    Long.class, centerId);
            return count != null ? count : 0;
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM attestation_droit WHERE center_id = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centerId, start, end);
        return count != null ? count : 0;
    }

    private long countAttestationsExpiring(UUID centerId, LocalDate threshold) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM attestation_droit WHERE center_id = ? AND date_fin <= ?",
                Long.class, centerId, threshold);
        return count != null ? count : 0;
    }

    private long countPatients(UUID centerId, YearMonth month) {
        if (month == null) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM patients WHERE center_id = ?",
                    Long.class, centerId);
            return count != null ? count : 0;
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM patients WHERE center_id = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centerId, start, end);
        return count != null ? count : 0;
    }
}
