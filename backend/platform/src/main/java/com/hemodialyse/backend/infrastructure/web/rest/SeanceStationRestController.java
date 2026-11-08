package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.SeanceRecap;
import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appui du poste infirmier : rappel des dernières séances du patient (SE4). Les raccourcis de consommables du centre sont servis par
 * contexts/seance depuis SE3 (ADR 0090).
 * Traçabilité : SeanceStationComponent → SeanceStore → /api/v1/seances/** → SeanceUseCase.
 */
@RestController
@RequestMapping("/api/v1/seances")
public class SeanceStationRestController {

    private final SeanceUseCase seances;
    private final CenterAccessGuard centerAccessGuard;

    public SeanceStationRestController(SeanceUseCase seances, CenterAccessGuard centerAccessGuard) {
        this.seances = seances;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Les dernières séances du patient (avant la date donnée, aujourd'hui par défaut), constantes comprises.
     */
    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN')")
    @GetMapping("/patient/{patientId}/recentes")
    public List<Map<String, Object>> recentes(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(defaultValue = "3") int limit) {
        CenterId centre = centerAccessGuard.requireCenter(centerId);
        return seances.recentByPatient(centre, patientId, before, limit).stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(SeanceRecap recap) {
        VoletParamedical v = recap.voletParamedical();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seanceId", recap.seance().getId());
        row.put("dateSeance", recap.seance().getDateSeance());
        row.put("status", recap.seance().getStatus());
        row.put("poidsAvantKg", v != null ? v.getPoidsAvantKg() : null);
        row.put("poidsApresKg", v != null ? v.getPoidsApresKg() : null);
        row.put("taAvant", v != null ? v.getTaAvant() : null);
        row.put("taApres", v != null ? v.getTaApres() : null);
        row.put("dureeMinutes", v != null ? v.getDureeMinutes() : null);
        row.put("ultrafiltrationMl", v != null ? v.getUltrafiltrationMl() : null);
        return row;
    }
}
