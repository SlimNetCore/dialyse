package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsCommunsUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsLectureUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.RefItemReponse;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Façade de cutover legacy (slice O5) : mêmes URLs ({@code /api/v1/referentials/*}) et mêmes
 * DTOs que l'ancien {@code ReferentialRestController}, ré-implémentées sur le contexte
 * Organisation. Le frontend continue de fonctionner sans changement pendant la bascule ;
 * les nouveaux écrans utilisent les endpoints canoniques ({@code /api/v1/organisation/*}).
 * <p>
 * Écart décidé (2026-11-02) : garde de centre. Le legacy croyait le {@code centerId} du client (tout utilisateur authentifié
 * lisait n'importe quel centre) ; le centre est désormais celui du jeton, 403 sinon. Tri par {@code code} des salles et des
 * positions : écart accepté.
 * <p>
 * À supprimer une fois le frontend migré (dette documentée dans la slice O5).
 */
@RestController
@RequestMapping("/api/v1/referentials")
public class ReferentielsLegacyControleur {

    private final ReferentielsOrganisationUseCases referentiels;
    private final ReferentielsLectureUseCases lectures;
    private final ReferentielsCommunsUseCases communs;
    private final OrganisationUseCases organisation;
    private final SessionCourante session;

    public ReferentielsLegacyControleur(ReferentielsOrganisationUseCases referentiels,
                                        ReferentielsLectureUseCases lectures,
                                        ReferentielsCommunsUseCases communs,
                                        OrganisationUseCases organisation,
                                        SessionCourante session) {
        this.session = session;
        this.referentiels = referentiels;
        this.lectures = lectures;
        this.communs = communs;
        this.organisation = organisation;
    }

    @GetMapping("/medecins")
    public ResponseEntity<?> medecins(@RequestParam UUID centerId) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(referentiels.listerMedecins(centre, 0, Integer.MAX_VALUE).items().stream()
                .map(m -> new RefItemReponse(m.id().valeur().toString(), null, m.nom(), null, m.prenom(),
                        m.specialite(), null))
                .toList());
    }

    @GetMapping("/salles")
    public ResponseEntity<?> salles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(organisation.listerSallesDuCentre(session.centre(centerId)).stream()
                .map(s -> new RefItemReponse(s.id().valeur().toString(), s.code(), s.nom(), null, null,
                        null, null))
                .toList());
    }

    @GetMapping("/positions")
    public ResponseEntity<?> positions(@RequestParam UUID centerId) {
        return ResponseEntity.ok(organisation.listerCreneaux(session.centre(centerId)).stream()
                .map(c -> new RefItemReponse(c.id().valeur().toString(), c.code(), null, null, null,
                        c.libelle(), null))
                .toList());
    }

    // /transporteurs et /categories-transport : servis par patient-couverture (RF1, ADR 0122), mêmes URLs et même JSON.
    // /forfaits : servi par patient-couverture (RF2, ADR 0124), même URL et même JSON.

    @GetMapping("/etats-patients")
    public ResponseEntity<?> etatsPatients() {
        return ResponseEntity.ok(communs.etatsPatients());
    }

    @GetMapping("/articles")
    public ResponseEntity<?> articles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(lectures.listerArticles(session.centre(centerId), 0, Integer.MAX_VALUE).items().stream()
                .map(a -> new RefItemReponse(a.id().toString(), a.code(), a.libelle(), null, null,
                        a.unite(), null))
                .toList());
    }

    @GetMapping("/generateurs")
    public ResponseEntity<?> generateurs(@RequestParam UUID centerId,
                                         @RequestParam(required = false) UUID salleId) {
        var generateurs = lectures.listerGenerateurs(session.centre(centerId));
        if (salleId != null) {
            generateurs = generateurs.stream().filter(g -> salleId.equals(g.salleId())).toList();
        }
        return ResponseEntity.ok(generateurs.stream()
                .map(g -> new RefItemReponse(g.id().toString(), g.code(),
                        g.nom() == null || g.nom().isBlank() ? "Générateur " + g.code() : g.nom(),
                        g.salleId() == null ? null : g.salleId().toString(), null,
                        g.statut(), null))
                .toList());
    }

    // /centres-payeurs, /centres-payeurs-details, /caisses et /agences : servis par patient-couverture (RF3c, ADR 0128), mêmes URLs et même JSON.
}
