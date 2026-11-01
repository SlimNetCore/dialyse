package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement;
import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement.Resultat;
import com.hemodialyse.backend.contexts.planification.application.ModifierLAffectation;
import com.hemodialyse.backend.contexts.planification.application.RetirerLAffectation;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.AffectationDemande;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.AffectationReponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Adapter entrant HTTP du roulement des infirmiers (slice P3) : mêmes URLs, mêmes droits (administrateur) et même JSON que le legacy,
 * bornées au centre de la session. Écarts décidés (26/10/2026) : corps illisible, jour inconnu, identifiant illisible 400 au lieu de
 * 500 ou {@code VALIDATION_ERROR}, 415 hors JSON, droits vérifiés avant le corps ; verrou de la fiche et écriture concurrente 409 ;
 * infirmier désactivé 422 ; « aujourd'hui » du sur-effectif au fuseau du centre (KB-PLN-25, 30, 32, 35).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/{id}/affectations")
public class RoulementControleur {

    private static final String ECRITURE = "hasRole('ADMIN')";

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final AffecterAuRoulement affecter;
    private final ModifierLAffectation modifier;
    private final RetirerLAffectation retirer;

    public RoulementControleur(SessionCourante session, ObjectMapper lecteur, AffecterAuRoulement affecter,
                               ModifierLAffectation modifier, RetirerLAffectation retirer) {
        this.session = session;
        this.lecteur = lecteur;
        this.affecter = affecter;
        this.modifier = modifier;
        this.retirer = retirer;
    }

    @PostMapping
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AffectationReponse> affecter(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                       @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        AffectationDemande demande = CorpsJson.lire(lecteur, requete, corps, AffectationDemande.class);
        Resultat resultat = affecter.executer(centre, new InfirmierId(id), demande.salle(), demande.creneau(), demande.joursLus());
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, resultat.affectation().id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(AffectationReponse.depuis(resultat));
    }

    @PutMapping("/{affectationId}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AffectationReponse> modifier(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                       @PathVariable UUID affectationId, @RequestBody(required = false) byte[] corps,
                                                       HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, affectationId.toString());
        AffectationDemande demande = CorpsJson.lire(lecteur, requete, corps, AffectationDemande.class);
        return ResponseEntity.ok(AffectationReponse.depuis(modifier.executer(centre, new InfirmierId(id),
                new AffectationId(affectationId), demande.salle(), demande.creneau(), demande.joursLus())));
    }

    @DeleteMapping("/{affectationId}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<Void> retirer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                        @PathVariable UUID affectationId, HttpServletRequest requete) {
        CentreId centre = session.centre(centerId);
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, affectationId.toString());
        retirer.executer(centre, new InfirmierId(id), new AffectationId(affectationId));
        return ResponseEntity.noContent().build();
    }
}
