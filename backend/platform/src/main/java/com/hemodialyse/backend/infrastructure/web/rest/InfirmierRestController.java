package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.AffectationInfirmierService;
import com.hemodialyse.backend.application.infirmier.CompteInfirmierService;
import com.hemodialyse.backend.application.infirmier.CompteInfirmierService.CompteCree;
import com.hemodialyse.backend.application.infirmier.InfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Référentiel des infirmiers et de leur roulement (salle, créneau, jours). Lecture pour le personnel, écriture réservée
 * à l'administrateur. Liste paginée (AGENTS.md §9) et bornée au centre (AGENTS.md §2).
 */
@RestController
@RequestMapping("/api/v1/infirmiers")
public class InfirmierRestController {

    private static final String LECTURE = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final String ECRITURE = "hasRole('ADMIN')";

    private final InfirmierService infirmiers;
    private final AffectationInfirmierService affectations;
    private final CompteInfirmierService comptes;
    private final PresenceInfirmierQueryService presence;
    private final CenterAccessGuard centerAccessGuard;

    public InfirmierRestController(InfirmierService infirmiers, AffectationInfirmierService affectations,
                                   CompteInfirmierService comptes, PresenceInfirmierQueryService presence,
                                   CenterAccessGuard centerAccessGuard) {
        this.infirmiers = infirmiers;
        this.affectations = affectations;
        this.comptes = comptes;
        this.presence = presence;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    @PreAuthorize(LECTURE)
    public ResponseEntity<PagedResult<InfirmierResponse>> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<InfirmierDetail> paged = infirmiers.lister(centre, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(InfirmierResponse::de).toList(), paged.total(), paged.page(), paged.size()));
    }

    @PostMapping
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> creer(@RequestParam(required = false) UUID centerId,
                                                   @Valid @RequestBody InfirmierRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        InfirmierDetail cree = infirmiers.creer(centre, r.matricule(), r.nom(), r.prenom(), r.telephone(),
                QualificationInfirmier.valueOf(r.qualification()), r.habiliteIsolement());
        return ResponseEntity.status(HttpStatus.CREATED).body(InfirmierResponse.de(cree));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> modifier(@RequestParam(required = false) UUID centerId,
                                                      @PathVariable UUID id, @Valid @RequestBody InfirmierRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(InfirmierResponse.de(infirmiers.modifier(centre, id, r.matricule(), r.nom(),
                r.prenom(), r.telephone(), QualificationInfirmier.valueOf(r.qualification()), r.habiliteIsolement())));
    }

    @PostMapping("/{id}/desactiver")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> desactiver(@RequestParam(required = false) UUID centerId,
                                                        @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(InfirmierResponse.de(infirmiers.desactiver(centre, id)));
    }

    @PostMapping("/{id}/reactiver")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> reactiver(@RequestParam(required = false) UUID centerId,
                                                       @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(InfirmierResponse.de(infirmiers.reactiver(centre, id)));
    }

    /**
     * Comptes INFIRMIER du centre qu'aucune fiche n'utilise encore (liste déroulante de la liaison).
     */
    @GetMapping("/comptes-liables")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<PagedResult<CompteResponse>> comptesLiables(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        PagedResult<CompteRef> paged = comptes.comptesLiables(centre, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(
                paged.items().stream().map(CompteResponse::de).toList(), paged.total(), paged.page(), paged.size()));
    }

    @PostMapping("/{id}/compte/lier")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> lierCompte(@RequestParam(required = false) UUID centerId,
                                                        @PathVariable UUID id, @Valid @RequestBody LierCompteRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(InfirmierResponse.de(comptes.lier(centre, id, r.userId())));
    }

    @PostMapping("/{id}/compte/creer")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<CompteCreeResponse> creerCompte(@RequestParam(required = false) UUID centerId,
                                                          @PathVariable UUID id, @Valid @RequestBody CreerCompteRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        CompteCree cree = comptes.creerEtLier(centre, id, r.identifiant(), r.email());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new CompteCreeResponse(InfirmierResponse.de(cree.infirmier()), cree.motDePasseTemporaire()));
    }

    @DeleteMapping("/{id}/compte")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<InfirmierResponse> delierCompte(@RequestParam(required = false) UUID centerId,
                                                          @PathVariable UUID id) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok(InfirmierResponse.de(comptes.delier(centre, id)));
    }

    @PostMapping("/{id}/affectations")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AffectationResponse> ajouterAffectation(
            @RequestParam(required = false) UUID centerId, @PathVariable UUID id,
            @Valid @RequestBody AffectationRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AffectationInfirmier a = affectations.ajouter(centre, id, r.salleId(), r.creneauId(), EnumSet.copyOf(r.jours()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AffectationResponse.de(a, sureffectif(centre, a)));
    }

    /**
     * Jours où l'affectation vient d'enregistrer crée un sur-effectif ; un échec du calcul ne doit jamais faire échouer
     * l'affectation.
     */
    private List<JourSemaine> sureffectif(UUID centre, AffectationInfirmier a) {
        try {
            return presence.joursEnSureffectif(centre, a.salleId(), a.creneauId(), a.jours(),
                    java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @PutMapping("/{id}/affectations/{affectationId}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AffectationResponse> modifierAffectation(
            @RequestParam(required = false) UUID centerId, @PathVariable UUID id, @PathVariable UUID affectationId,
            @Valid @RequestBody AffectationRequest r) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        AffectationInfirmier a = affectations.modifier(centre, id, affectationId, r.salleId(), r.creneauId(),
                EnumSet.copyOf(r.jours()));
        return ResponseEntity.ok(AffectationResponse.de(a, sureffectif(centre, a)));
    }

    @DeleteMapping("/{id}/affectations/{affectationId}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<Void> supprimerAffectation(
            @RequestParam(required = false) UUID centerId, @PathVariable UUID id, @PathVariable UUID affectationId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        affectations.supprimer(centre, id, affectationId);
        return ResponseEntity.noContent().build();
    }

    public record InfirmierRequest(
            @NotBlank String matricule,
            @NotBlank String nom,
            String prenom,
            String telephone,
            @NotBlank String qualification,
            boolean habiliteIsolement) {
    }

    public record AffectationRequest(
            @NotNull UUID salleId,
            @NotNull UUID creneauId,
            @NotEmpty List<JourSemaine> jours) {
    }

    /**
     * @param joursEnSureffectif jours de la semaine en cours où cette affectation porte la case au-dessus de l'effectif
     *                           requis (personnel payé sans activité utile) ; avertissement, jamais un refus
     */
    public record AffectationResponse(UUID id, UUID salleId, UUID creneauId, List<JourSemaine> jours,
                                      List<JourSemaine> joursEnSureffectif) {
        static AffectationResponse de(AffectationInfirmier a) {
            return de(a, List.of());
        }

        static AffectationResponse de(AffectationInfirmier a, List<JourSemaine> joursEnSureffectif) {
            return new AffectationResponse(a.id(), a.salleId(), a.creneauId(), a.jours().stream().sorted().toList(),
                    joursEnSureffectif);
        }
    }

    public record CompteResponse(UUID id, String username, String nomComplet, boolean actif) {
        static CompteResponse de(CompteRef c) {
            return c == null ? null : new CompteResponse(c.id(), c.username(), c.nomComplet(), c.actif());
        }
    }

    /**
     * @param compte compte utilisateur relié à la fiche, absent si la fiche n'a pas d'accès à l'application
     */
    public record InfirmierResponse(UUID id, String matricule, String nom, String prenom, String telephone,
                                    QualificationInfirmier qualification, boolean habiliteIsolement, boolean actif,
                                    List<AffectationResponse> affectations, CompteResponse compte) {
        static InfirmierResponse de(InfirmierDetail d) {
            var i = d.infirmier();
            return new InfirmierResponse(i.id(), i.matricule(), i.nom(), i.prenom(), i.telephone(), i.qualification(),
                    i.habiliteIsolement(), i.actif(), d.affectations().stream().map(AffectationResponse::de).toList(),
                    CompteResponse.de(d.compte()));
        }
    }

    public record LierCompteRequest(@NotNull UUID userId) {
    }

    public record CreerCompteRequest(@NotBlank String identifiant, String email) {
    }

    /**
     * @param motDePasseTemporaire communiqué une seule fois : il n'est jamais relisible ensuite
     */
    public record CompteCreeResponse(InfirmierResponse infirmier, String motDePasseTemporaire) {
    }
}
