package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.ConsulterIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.CorrigerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.CorrigerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.DesactiverIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.ListerIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureInconnue;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP du référentiel des intervenants : {@code /api/v1/gmao/intervenants}, derrière la chaîne de filtres
 * commune. Contrat verrouillé par la suite de caractérisation, sauf l'écart décidé KB-GMAO-22 : {@code GET /{id}} existe
 * (200, ou 404 si l'intervenant est inconnu ou d'un autre centre ; le legacy répondait 500). Les identifiants en chemin
 * restent des {@code String} analysés par {@link UUID#fromString} (KB-GMAO-23).
 */
@RestController
@RequestMapping(IntervenantControleur.BASE)
@PreAuthorize("hasRole('ADMIN')")
public class IntervenantControleur {

    public static final String BASE = "/api/v1/gmao/intervenants";

    private final SessionCourante session;
    private final EnregistrerIntervenant enregistrer;
    private final CorrigerIntervenant corriger;
    private final DesactiverIntervenant desactiver;
    private final ListerIntervenants lister;
    private final ConsulterIntervenant consulter;

    public IntervenantControleur(SessionCourante session, EnregistrerIntervenant enregistrer, CorrigerIntervenant corriger,
                                 DesactiverIntervenant desactiver, ListerIntervenants lister, ConsulterIntervenant consulter) {
        this.session = session;
        this.enregistrer = enregistrer;
        this.corriger = corriger;
        this.desactiver = desactiver;
        this.lister = lister;
        this.consulter = consulter;
    }

    @PostMapping
    public ResponseEntity<IntervenantReponse> enregistrer(@Valid @RequestBody IntervenantRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(IntervenantReponse.depuis(enregistrer.executer(
                new EnregistrerIntervenantCommande(session.centre(), requete.nom(), requete.type(), requete.telephone(),
                        requete.email(), requete.tarifHoraireDefaut(), session.utilisateur()))));
    }

    @GetMapping
    public ResponseEntity<IntervenantReponse.Page> lister(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(IntervenantReponse.Page.depuis(lister.executer(session.centre(), page, size)));
    }

    /** Écart accepté KB-GMAO-22 (décision utilisateur 2026-10-10) : 404 propre pour un intervenant inconnu ou d'un autre centre. */
    @GetMapping("/{id}")
    public ResponseEntity<IntervenantReponse> consulter(@PathVariable String id) {
        return consulter.executer(intervenantId(id), session.centre())
                .map(IntervenantReponse::depuis)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntervenantReponse> corriger(@PathVariable String id, @Valid @RequestBody IntervenantRequete requete) {
        return ResponseEntity.ok(IntervenantReponse.depuis(corriger.executer(new CorrigerIntervenantCommande(
                intervenantId(id), session.centre(), requete.nom(), requete.type(), requete.telephone(), requete.email(),
                requete.tarifHoraireDefaut(), session.utilisateur()))));
    }

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<IntervenantReponse> desactiver(@PathVariable String id) {
        return ResponseEntity.ok(IntervenantReponse.depuis(
                desactiver.executer(intervenantId(id), session.centre(), session.utilisateur())));
    }

    /**
     * Nature inconnue : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-29 : nom de
     * l'énumération legacy {@code TypeIntervenant}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(NatureInconnue.class)
    ProblemDetail natureInconnue(NatureInconnue erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("No enum constant com.hemodialyse.backend.domain.gmao.model.TypeIntervenant." + erreur.code());
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }

    private static IntervenantId intervenantId(String id) {
        return new IntervenantId(UUID.fromString(id));
    }
}
