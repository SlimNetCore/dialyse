package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.application.planning.SalleGenerateursService;
import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.*;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.*;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller pour la gestion des équipements GMAO.
 * Isolation multi-centre systématique (AGENTS.md §2) : toute lecture/écriture est bornée au
 * centre de l'utilisateur authentifié. Liste obligatoirement paginée (AGENTS.md §9).
 * <p>
 * Depuis la v2, {@code Equipement} (type {@code GENERATEUR_DIALYSE}) est la source de vérité
 * unique pour les générateurs de dialyse (l'ancien référentiel plat {@code generateur} est retiré,
 * cf. {@code GenerateurMigrationRunner}) : chaque endpoint d'écriture évince le cache référentiel
 * {@code ref.generateurs} pour que le wizard patient / l'affichage séance ne restent jamais sur un
 * statut périmé (sécurité patient — voir {@code EquipementFicheRestController}).
 */
@RestController
@RequestMapping("/api/v1/gmao/equipements")
@PreAuthorize("hasRole('ADMIN')")
public class EquipementRestController {

    private final EquipementRepositoryPort equipementRepository;
    private final EquipementStatutHistoriqueRepositoryPort historiqueRepository;

    private final SalleGenerateursService salleGenerateurs;

    public EquipementRestController(
            EquipementRepositoryPort equipementRepository,
            EquipementStatutHistoriqueRepositoryPort historiqueRepository,
            SalleGenerateursService salleGenerateurs) {
        this.equipementRepository = equipementRepository;
        this.historiqueRepository = historiqueRepository;
        this.salleGenerateurs = salleGenerateurs;
    }

    /**
     * Crée un nouvel équipement
     */
    @PostMapping
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public ResponseEntity<EquipementResponse> creerEquipement(
            @Valid @RequestBody CreateEquipementRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        if (equipementRepository.findByCentreIdAndCode(centreId, request.code()).isPresent()) {
            throw new IllegalArgumentException("Un équipement avec ce code existe déjà dans ce centre");
        }

        // Capacité de la salle : un générateur de plus ne doit pas la dépasser
        if (TypeEquipement.valueOf(request.type()) == TypeEquipement.GENERATEUR_DIALYSE) {
            salleGenerateurs.verifierAffectation(centreId, request.salleId(), null);
        }

        Equipement equipement = Equipement.creer(
                request.code(),
                request.designation(),
                TypeEquipement.valueOf(request.type()),
                request.fabricant(),
                request.modele(),
                request.numeroSerie(),
                request.dateInstallation(),
                centreId,
                request.localisation(),
                UUID.fromString(principal.getId()),
                request.salleId(),
                request.prixAcquisition()
        );

        equipementRepository.save(equipement);
        enregistrerHistorique(equipement, null);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EquipementResponse(equipement));
    }

    /**
     * Récupère un équipement par ID (restreint au centre courant)
     */
    @GetMapping("/{id}")
    public ResponseEntity<EquipementResponse> obtenirEquipement(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        return equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(centreId))
                .map(EquipementResponse::new)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Liste paginée des équipements du centre courant, filtrable par statut
     * (AGENTS.md §9 : toute liste doit être paginée — jamais de findAll sans limite).
     */
    @GetMapping
    public ResponseEntity<PagedResult<EquipementResponse>> listerEquipements(
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        PagedResult<Equipement> paged = equipementRepository.findPaged(
                UUID.fromString(principal.getCenterId()), statut, page, size);

        return ResponseEntity.ok(new PagedResult<>(
                paged.items().stream().map(EquipementResponse::new).toList(),
                paged.total(),
                paged.page(),
                paged.size()
        ));
    }

    /**
     * Modifie les caractéristiques d'un équipement existant (code/type/date d'installation immuables)
     */
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public ResponseEntity<EquipementResponse> modifierEquipement(
            @PathVariable String id,
            @Valid @RequestBody UpdateEquipementRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID centreId = UUID.fromString(principal.getCenterId());

        Equipement equipement = equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(centreId))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));

        if (equipement.getType() == TypeEquipement.GENERATEUR_DIALYSE) {
            salleGenerateurs.verifierAffectation(centreId, request.salleId(), equipement.getId());
        }

        equipement.modifier(
                request.designation(),
                request.fabricant(),
                request.modele(),
                request.numeroSerie(),
                request.localisation(),
                UUID.fromString(principal.getId()),
                request.salleId(),
                request.prixAcquisition()
        );
        equipementRepository.save(equipement);

        return ResponseEntity.ok(new EquipementResponse(equipement));
    }

    /**
     * Marque un équipement comme hors service
     */
    @PostMapping("/{id}/hors-service")
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public ResponseEntity<EquipementResponse> marquerHorsService(
            @PathVariable String id,
            @Valid @RequestBody MarquerHorsServiceRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Equipement equipement = equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(UUID.fromString(principal.getCenterId())))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));

        StatutEquipement statutPrecedent = equipement.getStatut();
        equipement.marquerHorsService(request.raison(), UUID.fromString(principal.getId()));
        equipementRepository.save(equipement);
        enregistrerHistorique(equipement, statutPrecedent);

        return ResponseEntity.ok(new EquipementResponse(equipement));
    }

    /**
     * Réactive un équipement
     */
    @PostMapping("/{id}/reactiver")
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public ResponseEntity<EquipementResponse> reactiverEquipement(
            @PathVariable String id,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Equipement equipement = equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(UUID.fromString(principal.getCenterId())))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));

        StatutEquipement statutPrecedent = equipement.getStatut();
        equipement.reactiver(UUID.fromString(principal.getId()));
        equipementRepository.save(equipement);
        enregistrerHistorique(equipement, statutPrecedent);

        return ResponseEntity.ok(new EquipementResponse(equipement));
    }

    /**
     * Réforme définitivement un équipement (fin de vie — aucun retour en arrière possible)
     */
    @PostMapping("/{id}/reformer")
    @PreAuthorize("hasRole('GMAO_REFORME')")
    @CacheEvict(cacheNames = "ref.generateurs", allEntries = true)
    public ResponseEntity<EquipementResponse> reformerEquipement(
            @PathVariable String id,
            @Valid @RequestBody ReformerEquipementRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Equipement equipement = equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(UUID.fromString(principal.getCenterId())))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));

        StatutEquipement statutPrecedent = equipement.getStatut();
        equipement.reformer(request.motif(), UUID.fromString(principal.getId()));
        equipementRepository.save(equipement);
        enregistrerHistorique(equipement, statutPrecedent);

        return ResponseEntity.ok(new EquipementResponse(equipement));
    }

    /**
     * Ajoute une observation
     */
    @PostMapping("/{id}/observations")
    public ResponseEntity<EquipementResponse> ajouterObservation(
            @PathVariable String id,
            @Valid @RequestBody AjouterObservationRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        Equipement equipement = equipementRepository.findById(UUID.fromString(id))
                .filter(e -> e.getCentreId().equals(UUID.fromString(principal.getCenterId())))
                .orElseThrow(() -> new IllegalArgumentException("Équipement non trouvé"));

        equipement.ajouterObservation(request.observation(), UUID.fromString(principal.getId()));
        equipementRepository.save(equipement);

        return ResponseEntity.ok(new EquipementResponse(equipement));
    }

    private void enregistrerHistorique(Equipement equipement, StatutEquipement statutPrecedent) {
        historiqueRepository.save(EquipementStatutHistorique.enregistrer(
                equipement.getId(), equipement.getCentreId(), statutPrecedent, equipement.getStatut(),
                equipement.getObservations(), equipement.getModifiePar() != null ? equipement.getModifiePar() : equipement.getCreePar()
        ));
    }
}
