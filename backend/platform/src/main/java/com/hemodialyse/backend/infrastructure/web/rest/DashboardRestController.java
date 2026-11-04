package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.centre.JourDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.infrastructure.web.dto.request.DashboardSearchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Tableau de bord administratif du centre. Les compteurs de prises en charge, d'attestations et de patients viennent du langage
 * publié de Patient et couverture ({@link CompteursDeCouverture}, PC5 : plus de SQL sur leurs tables, comptes inchangés) ; l'échéance
 * se calcule au jour du centre (PC4, KB-PAT-47).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardRestController {

    private final CompteursDeCouverture compteurs;
    private final JourDuCentre jourDuCentre;

    public DashboardRestController(CompteursDeCouverture compteurs, JourDuCentre jourDuCentre) {
        this.compteurs = compteurs;
        this.jourDuCentre = jourDuCentre;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestParam UUID centerId,
                                   @RequestParam(defaultValue = "30") int expirationDays,
                                   @RequestParam(required = false) String month) {
        YearMonth yearMonth = parseMonth(month);
        // PC4 (KB-PAT-47) : l'échéance se calcule au jour du centre, plus à celui de la JVM
        LocalDate threshold = jourDuCentre.aujourdhui(centerId).plusDays(expirationDays);

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
        LocalDate threshold = jourDuCentre.aujourdhui(criteria.centerId()).plusDays(expirationDays);

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
        if (month == null) return compteurs.prisesEnCharge(centerId, status, null, null);
        return compteurs.prisesEnCharge(centerId, status, month.atDay(1), month.atEndOfMonth());
    }

    private long countPecExpiring(UUID centerId, LocalDate threshold) {
        return compteurs.prisesEnChargeValideesAEcheance(centerId, threshold);
    }

    private long countAttestations(UUID centerId, YearMonth month) {
        if (month == null) return compteurs.attestations(centerId, null, null);
        return compteurs.attestations(centerId, month.atDay(1), month.atEndOfMonth());
    }

    private long countAttestationsExpiring(UUID centerId, LocalDate threshold) {
        return compteurs.attestationsAEcheance(centerId, threshold);
    }

    private long countPatients(UUID centerId, YearMonth month) {
        if (month == null) {
            // effectif d'aujourd'hui (jour d'UTC, comme avant) : un patient sorti (transfert, décès, greffe, guérison, fin de séjour)
            // n'est plus compté
            return compteurs.patientsPresentsLe(centerId, LocalDate.now(java.time.ZoneOffset.UTC));
        }
        return compteurs.patientsCrees(centerId, month.atDay(1), month.atEndOfMonth());
    }
}
