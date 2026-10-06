package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationApplicationService;
import com.hemodialyse.backend.application.planning.optimisation.OptimisationPlanningService;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Optimisation du planning (moteur Timefold) : lancement asynchrone, suivi, arrêt anticipé, historique et application de
 * la proposition. Lecture et lancement pour l'administration et le secrétariat ; l'application d'une proposition, qui
 * modifie les placements et le roulement, est réservée à l'administrateur. Bornée au centre ({@link CenterAccessGuard}).
 */
@RestController
@RequestMapping("/api/v1/planning/optimisations")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class PlanningOptimisationRestController {

    private final OptimisationPlanningService planification;
    private final OptimisationApplicationService application;
    private final CenterAccessGuard centerAccessGuard;

    public PlanningOptimisationRestController(OptimisationPlanningService planification,
                                              OptimisationApplicationService application,
                                              CenterAccessGuard centerAccessGuard) {
        this.planification = planification;
        this.application = application;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Lance un calcul : réponse immédiate {@code 202} avec l'exécution {@code EN_COURS}, à suivre par {@link #consulter}.
     */
    @PostMapping
    public ResponseEntity<RunOptimisation> lancer(@RequestParam(required = false) UUID centerId,
                                                  @Valid @RequestBody LancerRequest request) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        String utilisateur = centerAccessGuard.currentScope().userId();
        RunOptimisation run = planification.lancer(centre, request.versParametres(), utilisateur);
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(run);
    }

    /**
     * Historique paginé, des plus récentes aux plus anciennes (sans le détail des propositions).
     */
    @GetMapping
    public ResponseEntity<PagedResult<RunOptimisation>> historique(@RequestParam(required = false) UUID centerId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planification.historique(centre, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunOptimisation> consulter(@RequestParam(required = false) UUID centerId,
                                                     @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planification.consulter(centre, id));
    }

    @PostMapping("/{id}/arret")
    public ResponseEntity<RunOptimisation> arreter(@RequestParam(required = false) UUID centerId,
                                                   @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(planification.arreter(centre, id));
    }

    @PostMapping("/{id}/application")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RunOptimisation> appliquer(@RequestParam(required = false) UUID centerId,
                                                     @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(application.appliquer(centre, id));
    }

    /**
     * @param debutSemaine           un jour de la première semaine (défaut : semaine en cours)
     * @param nbSemaines             horizon en semaines (couverture seulement, défaut 1)
     * @param dureeMaxSecondes       durée maximale de calcul par phase (défaut 20)
     * @param stabilite              0 à 10 (défaut 5)
     * @param objectif               {@code EQUITE} (défaut) ou {@code ECONOMIE}
     * @param maxVacationsParJour    1 à 3 (défaut 2)
     * @param maxVacationsParSemaine 1 à 14 (défaut 6)
     */
    public record LancerRequest(
            @NotNull PerimetreOptimisation perimetre,
            LocalDate debutSemaine,
            Integer nbSemaines,
            Integer dureeMaxSecondes,
            Integer stabilite,
            ObjectifInfirmiers objectif,
            Integer maxVacationsParJour,
            Integer maxVacationsParSemaine) {

        ParametresOptimisation versParametres() {
            ParametresOptimisation defauts = ParametresOptimisation.parDefaut(perimetre,
                    debutSemaine != null ? debutSemaine : LocalDate.now(ZoneOffset.UTC));
            return new ParametresOptimisation(perimetre, defauts.debutSemaine(),
                    nbSemaines != null ? nbSemaines : defauts.nbSemaines(),
                    dureeMaxSecondes != null ? dureeMaxSecondes : defauts.dureeMaxSecondes(),
                    stabilite != null ? stabilite : defauts.stabilite(),
                    objectif != null ? objectif : defauts.objectif(),
                    maxVacationsParJour != null ? maxVacationsParJour : defauts.maxVacationsParJour(),
                    maxVacationsParSemaine != null ? maxVacationsParSemaine : defauts.maxVacationsParSemaine());
        }
    }
}
