package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.optimisation.PreferencesPlanningService;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Données de l'optimisation du planning saisies par le centre : préférences des patients, profils des infirmiers et
 * réglages. Lecture et saisie des préférences et profils : `ADMIN`, `SECRETAIRE` ; réglages : écriture `ADMIN`.
 * Bornée au centre ({@link CenterAccessGuard}), non mise en cache.
 */
@RestController
@RequestMapping("/api/v1/planning/preferences")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class PlanningPreferencesRestController {

    private final PreferencesPlanningService service;
    private final CenterAccessGuard centerAccessGuard;

    public PlanningPreferencesRestController(PreferencesPlanningService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping("/patients")
    public ResponseEntity<PagedResult<PreferencePatientPort.Ligne>> patients(
            @RequestParam(required = false) UUID centerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preferencesPatients(centre, page, size));
    }

    @PutMapping("/patients/{patientId}")
    public ResponseEntity<PreferencePatient> enregistrerPreference(@RequestParam(required = false) UUID centerId,
                                                                   @PathVariable UUID patientId,
                                                                   @RequestBody PreferenceRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(service.enregistrerPreference(centre,
                new PreferencePatient(patientId, r.creneauPrefereId(), r.seancesParSemaine(), r.joursAChoisir())));
    }

    @GetMapping("/infirmiers")
    public ResponseEntity<PagedResult<ProfilInfirmierPort.Ligne>> infirmiers(
            @RequestParam(required = false) UUID centerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.profilsInfirmiers(centre, page, size));
    }

    @PutMapping("/infirmiers/{infirmierId}")
    public ResponseEntity<ProfilInfirmier> enregistrerProfil(@RequestParam(required = false) UUID centerId,
                                                             @PathVariable UUID infirmierId,
                                                             @Valid @RequestBody ProfilRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(service.enregistrerProfil(centre,
                new ProfilInfirmier(infirmierId, r.tauxActivite(), r.competences() == null ? Set.of() : Set.copyOf(r.competences()))));
    }

    @GetMapping("/reglages")
    public ResponseEntity<ReglagesOptimisation> reglages(@RequestParam(required = false) UUID centerId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reglages(centre));
    }

    @PutMapping("/reglages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReglagesOptimisation> enregistrerReglages(@RequestParam(required = false) UUID centerId,
                                                                    @RequestBody ReglagesOptimisation r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(service.enregistrerReglages(centre, r));
    }

    /**
     * @param joursAChoisir les jours de dialyse sont choisis par l'optimisation ({@code seancesParSemaine} requis)
     */
    public record PreferenceRequest(UUID creneauPrefereId, Integer seancesParSemaine, boolean joursAChoisir) {
    }

    public record ProfilRequest(@NotNull Integer tauxActivite, List<CompetenceInfirmier> competences) {
    }
}
