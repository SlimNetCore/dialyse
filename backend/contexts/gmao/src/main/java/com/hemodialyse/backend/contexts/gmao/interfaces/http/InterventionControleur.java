package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AcheverTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterLigneDeCout;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterLigneDeCoutCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTacheCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.Annuler;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ListerInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.Rectifier;
import com.hemodialyse.backend.contexts.gmao.application.intervention.RetirerLigneDeCout;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adapter entrant HTTP des interventions : {@code /api/v1/gmao/interventions}, derrière la chaîne de filtres commune
 * (sécurité, licence, rôle, audit). Contrat (routes, statuts, corps, erreurs) verrouillé par la suite de caractérisation,
 * sauf l'écart décidé KB-GMAO-52 : la rectification exige le rôle {@code ADMIN} <b>et</b> l'habilitation
 * {@code GMAO_RECTIFICATION} (le legacy se contentait de l'habilitation). Ajout nouveau (KB-GMAO-55, demandé le 2026-10-12) :
 * les tâches, {@code POST /{id}/taches}, {@code POST /{id}/taches/{tacheId}/demarrer} et {@code .../achever}, réservées au rôle
 * {@code ADMIN} comme les autres commandes (à valider par le métier) ; mêmes conventions d'erreur (400 intervention ou tâche
 * inconnue ou description vide, 422 transition, statut ou limite refusés) ; la réponse est l'intervention entière. Les identifiants en chemin restent des
 * {@code String} analysés par {@link UUID#fromString} : message d'erreur 400 inchangé (KB-GMAO-53).
 */
@RestController
@RequestMapping(InterventionControleur.BASE)
@PreAuthorize("hasRole('ADMIN')")
public class InterventionControleur {

    public static final String BASE = "/api/v1/gmao/interventions";

    /**
     * Cache du référentiel des générateurs (lu par la Planification). Démarrer et terminer changent l'état de l'équipement :
     * ils l'évincent ici, de façon synchrone, comme {@code EquipementControleur} ({@code @CacheEvict} s'exécute au retour de la
     * méthode, donc après le commit du cas d'usage et avant la réponse HTTP). Aucune éviction si la commande est refusée.
     */
    private static final String GENERATEURS = "ref.generateurs";

    private final SessionCourante session;
    private final PlanifierIntervention planifier;
    private final DemarrerIntervention demarrer;
    private final TerminerIntervention terminer;
    private final AjouterLigneDeCout ajouterLigne;
    private final RetirerLigneDeCout retirerLigne;
    private final AjouterTache ajouterTache;
    private final DemarrerTache demarrerTache;
    private final AcheverTache acheverTache;
    private final Rectifier rectifier;
    private final Annuler annuler;
    private final ConsulterIntervention consulter;
    private final ListerInterventions lister;

    public InterventionControleur(SessionCourante session, PlanifierIntervention planifier, DemarrerIntervention demarrer,
                                  TerminerIntervention terminer, AjouterLigneDeCout ajouterLigne,
                                  RetirerLigneDeCout retirerLigne, AjouterTache ajouterTache, DemarrerTache demarrerTache,
                                  AcheverTache acheverTache, Rectifier rectifier, Annuler annuler,
                                  ConsulterIntervention consulter, ListerInterventions lister) {
        this.session = session;
        this.planifier = planifier;
        this.demarrer = demarrer;
        this.terminer = terminer;
        this.ajouterLigne = ajouterLigne;
        this.retirerLigne = retirerLigne;
        this.ajouterTache = ajouterTache;
        this.demarrerTache = demarrerTache;
        this.acheverTache = acheverTache;
        this.rectifier = rectifier;
        this.annuler = annuler;
        this.consulter = consulter;
        this.lister = lister;
    }

    @PostMapping
    public ResponseEntity<InterventionReponse> planifier(@Valid @RequestBody PlanifierInterventionRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(InterventionReponse.depuis(planifier.executer(
                new PlanifierInterventionCommande(new EquipementId(requete.equipementId()), session.centre(), requete.type(),
                        requete.dateDebut().toInstant(), requete.description(),
                        requete.intervenantId() == null ? null : new IntervenantId(requete.intervenantId()),
                        requete.etatEquipementAvant(), requete.symptome(), requete.priorite(),
                        instant(requete.echeance()), session.utilisateur()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterventionReponse> consulter(@PathVariable String id) {
        return consulter.executer(interventionId(id), session.centre())
                .map(InterventionReponse::depuis)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<InterventionReponse.Page> lister(@RequestParam(required = false) String statut,
                                                           @RequestParam(required = false) UUID equipementId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(InterventionReponse.Page.depuis(lister.executer(session.centre(), statut,
                equipementId == null ? null : new EquipementId(equipementId), page, size)));
    }

    @PostMapping("/{id}/demarrer")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<InterventionReponse> demarrer(@PathVariable String id) {
        return ResponseEntity.ok(InterventionReponse.depuis(
                demarrer.executer(interventionId(id), session.centre(), session.utilisateur())));
    }

    @PostMapping("/{id}/terminer")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<InterventionReponse> terminer(@PathVariable String id,
                                                        @Valid @RequestBody TerminerInterventionRequete requete) {
        return ResponseEntity.ok(InterventionReponse.depuis(terminer.executer(new TerminerInterventionCommande(
                interventionId(id), session.centre(), requete.actions(), requete.etatEquipementApres(),
                requete.dateFin().toInstant(), requete.cause(), session.utilisateur()))));
    }

    @PostMapping("/{id}/lignes-cout")
    public ResponseEntity<InterventionReponse> ajouterLigneDeCout(@PathVariable String id,
                                                                  @Valid @RequestBody AjouterLigneDeCoutRequete requete) {
        return ResponseEntity.ok(InterventionReponse.depuis(ajouterLigne.executer(new AjouterLigneDeCoutCommande(
                interventionId(id), session.centre(), requete.type(), requete.libelle(), requete.quantite(),
                requete.prixUnitaire(), requete.articleStockId(), session.utilisateur()))));
    }

    @DeleteMapping("/{id}/lignes-cout/{ligneId}")
    public ResponseEntity<InterventionReponse> retirerLigneDeCout(@PathVariable String id, @PathVariable UUID ligneId) {
        return ResponseEntity.ok(InterventionReponse.depuis(retirerLigne.executer(interventionId(id), session.centre(),
                new LigneDeCoutId(ligneId), session.utilisateur())));
    }

    @PostMapping("/{id}/taches")
    public ResponseEntity<InterventionReponse> ajouterTache(@PathVariable String id,
                                                            @Valid @RequestBody AjouterTacheRequete requete) {
        return ResponseEntity.ok(InterventionReponse.depuis(ajouterTache.executer(new AjouterTacheCommande(interventionId(id),
                session.centre(), requete.description(), session.utilisateur()))));
    }

    @PostMapping("/{id}/taches/{tacheId}/demarrer")
    public ResponseEntity<InterventionReponse> demarrerTache(@PathVariable String id, @PathVariable String tacheId) {
        return ResponseEntity.ok(InterventionReponse.depuis(demarrerTache.executer(interventionId(id), session.centre(),
                new TacheId(UUID.fromString(tacheId)), session.utilisateur())));
    }

    @PostMapping("/{id}/taches/{tacheId}/achever")
    public ResponseEntity<InterventionReponse> acheverTache(@PathVariable String id, @PathVariable String tacheId) {
        return ResponseEntity.ok(InterventionReponse.depuis(acheverTache.executer(interventionId(id), session.centre(),
                new TacheId(UUID.fromString(tacheId)), session.utilisateur())));
    }

    /** Écart décidé KB-GMAO-52 (2026-10-12) : ADMIN et GMAO_RECTIFICATION ; l'annotation de méthode remplace celle de la classe. */
    @PostMapping("/{id}/rectifier")
    @PreAuthorize("hasRole('ADMIN') and hasRole('GMAO_RECTIFICATION')")
    public ResponseEntity<InterventionReponse> rectifier(@PathVariable String id,
                                                         @Valid @RequestBody RectifierInterventionRequete requete) {
        return ResponseEntity.ok(InterventionReponse.depuis(rectifier.executer(interventionId(id), session.centre(),
                requete.motif(), instant(requete.dateDebut()), session.utilisateur())));
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<InterventionReponse> annuler(@PathVariable String id,
                                                       @Valid @RequestBody AnnulerInterventionRequete requete) {
        return ResponseEntity.ok(InterventionReponse.depuis(
                annuler.executer(interventionId(id), session.centre(), requete.raison(), session.utilisateur())));
    }

    private static InterventionId interventionId(String id) {
        return new InterventionId(UUID.fromString(id));
    }

    private static Instant instant(OffsetDateTime date) {
        return date == null ? null : date.toInstant();
    }
}
