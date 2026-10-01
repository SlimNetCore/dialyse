package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.application.gmao.InterventionEquipementStatutService;
import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.*;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller pour la gestion des interventions GMAO.
 * Isolation multi-centre systématique (AGENTS.md §2). Liste obligatoirement paginée (AGENTS.md §9).
 * <p>
 * L'utilisateur saisit l'état de l'équipement à la création (appliqué au démarrage) et après l'intervention
 * (appliqué à la clôture) ; « À réformer » n'est qu'une proposition — la réforme elle-même relève d'une
 * personne habilitée ({@code EquipementRestController#reformerEquipement}).
 */
@RestController
@RequestMapping("/api/v1/gmao/interventions")
@PreAuthorize("hasRole('ADMIN')")
public class InterventionRestController {

    private final InterventionRepositoryPort interventionRepository;
    private final IntervenantRepositoryPort intervenantRepository;
    private final InterventionEquipementStatutService statutService;

    public InterventionRestController(InterventionRepositoryPort interventionRepository,
                                      IntervenantRepositoryPort intervenantRepository,
                                      InterventionEquipementStatutService statutService) {
        this.interventionRepository = interventionRepository;
        this.intervenantRepository = intervenantRepository;
        this.statutService = statutService;
    }

    /**
     * Crée une nouvelle intervention
     */
    @PostMapping
    public ResponseEntity<InterventionResponse> creerIntervention(
            @Valid @RequestBody CreateInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());
        statutService.requireEquipement(request.equipementId(), centreId);

        Intervention intervention = Intervention.creer(
                request.equipementId(),
                centreId,
                TypeIntervention.valueOf(request.type()),
                request.dateDebut(),
                request.description(),
                request.intervenantId(),
                StatutEquipement.valueOf(request.etatEquipementAvant()),
                request.symptome(),
                request.priorite() == null || request.priorite().isBlank()
                        ? PrioriteIntervention.NORMALE : PrioriteIntervention.valueOf(request.priorite()),
                request.echeance(),
                UUID.fromString(principal.getId())
        );

        interventionRepository.save(intervention);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InterventionResponse(intervention));
    }

    /**
     * Récupère une intervention par ID (restreinte au centre courant)
     */
    @GetMapping("/{id}")
    public ResponseEntity<InterventionResponse> obtenirIntervention(
            @PathVariable String id, Authentication authentication) {
        UUID centreId = centreId(authentication);
        return interventionRepository.findById(UUID.fromString(id))
                .filter(i -> i.getCentreId().equals(centreId))
                .map(InterventionResponse::new)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Liste paginée des interventions du centre courant, filtrable par statut et/ou équipement
     * (AGENTS.md §9 : toute liste doit être paginée — jamais de findAll sans limite).
     */
    @GetMapping
    public ResponseEntity<PagedResult<InterventionResponse>> listerInterventions(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) UUID equipementId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID centreId = centreId(authentication);

        PagedResult<Intervention> paged = equipementId != null
                ? interventionRepository.findPagedByEquipementId(
                statutService.requireEquipement(equipementId, centreId).getId(), page, size)
                : interventionRepository.findPaged(centreId, statut, page, size);

        return ResponseEntity.ok(new PagedResult<>(
                paged.items().stream().map(InterventionResponse::new).toList(),
                paged.total(),
                paged.page(),
                paged.size()
        ));
    }

    /**
     * Démarre une intervention : l'équipement prend l'état saisi à la création
     */
    @PostMapping("/{id}/demarrer")
    public ResponseEntity<InterventionResponse> demarrerIntervention(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getId());
        Intervention intervention = requireIntervention(id, centreId(authentication));

        intervention.demarrer(userId);
        statutService.appliquerEtat(intervention.getEquipementId(), intervention.getCentreId(),
                intervention.getEtatEquipementAvant(), "Intervention démarrée", userId);
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    /**
     * Termine une intervention : l'équipement prend l'état saisi après intervention
     */
    @PostMapping("/{id}/terminer")
    public ResponseEntity<InterventionResponse> terminerIntervention(
            @PathVariable String id,
            @Valid @RequestBody TerminerInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal.getId());
        UUID centreId = centreId(authentication);
        Intervention intervention = requireIntervention(id, centreId);

        intervention.terminer(request.actions(), StatutEquipement.valueOf(request.etatEquipementApres()),
                request.dateFin(), request.cause(), userId);
        // Valorisation automatique du temps de l'intervenant (tarif horaire × durée), si renseigné
        if (intervention.getIntervenantId() != null) {
            intervenantRepository.findById(intervention.getIntervenantId())
                    .filter(i -> i.centreId().equals(centreId))
                    .ifPresent(i -> intervention.appliquerTarifIntervenant(i.tarifHoraireDefaut(), userId));
        }
        statutService.appliquerEtat(intervention.getEquipementId(), centreId,
                intervention.getEtatEquipementApres(), "Intervention terminée", userId);
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    /**
     * Ajoute une ligne de coût (pièce, main d'œuvre, intervenant...) — aide à la décision sur le coût
     * réel de maintenance.
     */
    @PostMapping("/{id}/lignes-cout")
    public ResponseEntity<InterventionResponse> ajouterLigneCout(
            @PathVariable String id,
            @Valid @RequestBody AjouterLigneCoutRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Intervention intervention = requireIntervention(id, centreId(authentication));

        LigneCoutIntervention ligne = LigneCoutIntervention.creer(
                TypeLigneCout.valueOf(request.type()), request.libelle(), request.quantite(),
                request.prixUnitaire(), request.articleStockId());
        intervention.ajouterLigneCout(ligne, UUID.fromString(principal.getId()));
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    /**
     * Annule une intervention
     */
    @PostMapping("/{id}/annuler")
    public ResponseEntity<InterventionResponse> annulerIntervention(
            @PathVariable String id,
            @Valid @RequestBody AnnulerInterventionRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Intervention intervention = requireIntervention(id, centreId(authentication));

        intervention.annuler(request.raison(), UUID.fromString(principal.getId()));
        interventionRepository.save(intervention);

        return ResponseEntity.ok(new InterventionResponse(intervention));
    }

    private Intervention requireIntervention(String id, UUID centreId) {
        return interventionRepository.findById(UUID.fromString(id))
                .filter(i -> i.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Intervention non trouvée"));
    }

    private UUID centreId(Authentication authentication) {
        return UUID.fromString(((UserPrincipal) authentication.getPrincipal()).getCenterId());
    }
}
