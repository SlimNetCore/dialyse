package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.SeanceRecap;
import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciUseCase;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appuis du poste infirmier : rappel des dernières séances du patient et raccourcis de consommables du centre.
 * Traçabilité : SeanceStationComponent → SeanceStore → /api/v1/seances/** → SeanceUseCase / SeanceRaccourciUseCase.
 */
@RestController
@RequestMapping("/api/v1/seances")
public class SeanceStationRestController {

    private final SeanceUseCase seances;
    private final SeanceRaccourciUseCase raccourcis;
    private final CenterAccessGuard centerAccessGuard;

    public SeanceStationRestController(SeanceUseCase seances, SeanceRaccourciUseCase raccourcis,
                                       CenterAccessGuard centerAccessGuard) {
        this.seances = seances;
        this.raccourcis = raccourcis;
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

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @GetMapping("/raccourcis-consommables")
    public List<UUID> raccourcis(@RequestParam(required = false) UUID centerId) {
        return raccourcis.get(centerAccessGuard.requireCenter(centerId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/raccourcis-consommables")
    public List<UUID> remplacerRaccourcis(@RequestParam(required = false) UUID centerId,
                                          @RequestBody @NotNull RaccourcisRequest request) {
        return raccourcis.replace(centerAccessGuard.requireCenter(centerId), request.articleIds());
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

    public record RaccourcisRequest(List<UUID> articleIds) {
    }
}
