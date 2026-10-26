package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.AffecterUnRemplacant;
import com.hemodialyse.backend.contexts.planification.application.AnnulerLeRemplacement;
import com.hemodialyse.backend.contexts.planification.application.RemplacementLu;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP des remplacements (slice P3) : mêmes URLs et mêmes droits (administrateur et secrétariat) que le legacy, bornées
 * au centre de la session. Écarts décidés (26/10/2026) : entrées illisibles 400, droits avant le corps, remplaçant concurrent 409 au
 * lieu de 500, jour fermé ou passé 422, remplacé contrôlé (KB-PLN-27 à 30).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/presence/remplacements")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class RemplacementsControleur {

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final AffecterUnRemplacant affecter;
    private final AnnulerLeRemplacement annuler;

    public RemplacementsControleur(SessionCourante session, ObjectMapper lecteur, AffecterUnRemplacant affecter,
                                   AnnulerLeRemplacement annuler) {
        this.session = session;
        this.lecteur = lecteur;
        this.affecter = affecter;
        this.annuler = annuler;
    }

    @PostMapping
    public ResponseEntity<RemplacementReponse> affecter(@RequestParam(required = false) UUID centerId,
                                                        @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        RemplacementDemande demande = CorpsJson.lire(lecteur, requete, corps, RemplacementDemande.class);
        if (demande.date() == null) {
            throw new SaisieInvalide("Champ obligatoire absent : date");
        }
        RemplacementLu cree = affecter.executer(centre, demande.date(), new SalleId(demande.salleId()),
                new CreneauId(demande.creneauId()), new InfirmierId(demande.infirmierId()),
                demande.remplaceId() == null ? null : new InfirmierId(demande.remplaceId()));
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, cree.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(RemplacementReponse.depuis(cree));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> annuler(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        annuler.executer(centre, new RemplacementId(id));
        return ResponseEntity.noContent().build();
    }

    public record RemplacementDemande(LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId, UUID remplaceId) {
    }

    public record RemplacementReponse(UUID id, LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId,
                                      UUID remplaceId) {
        static RemplacementReponse depuis(RemplacementLu r) {
            return new RemplacementReponse(r.id(), r.date(), r.salle(), r.creneau(), r.infirmier(), r.remplace());
        }
    }
}
