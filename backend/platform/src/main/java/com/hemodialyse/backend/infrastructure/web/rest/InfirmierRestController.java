package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.AffectationInfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Roulement des infirmiers (salle, créneau, jours) : écriture réservée à l'administrateur, bornée au centre (AGENTS.md §2).
 * Le référentiel des infirmiers (fiches, comptes) est servi par le contexte Planification (slice P2,
 * {@code contexts/planification}, même chemin {@code /api/v1/infirmiers}) ; ce contrôleur ne garde que les trois routes
 * d'affectations, retirées avec la slice P3.
 */
@RestController
@RequestMapping("/api/v1/infirmiers")
public class InfirmierRestController {

    private static final String ECRITURE = "hasRole('ADMIN')";

    private final AffectationInfirmierService affectations;
    private final PresenceInfirmierQueryService presence;
    private final CenterAccessGuard centerAccessGuard;

    public InfirmierRestController(AffectationInfirmierService affectations, PresenceInfirmierQueryService presence,
                                   CenterAccessGuard centerAccessGuard) {
        this.affectations = affectations;
        this.presence = presence;
        this.centerAccessGuard = centerAccessGuard;
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
     * Fiche d'un infirmier avec son roulement et son compte, telle que « Mon planning » (P3) l'affiche.
     *
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
}
