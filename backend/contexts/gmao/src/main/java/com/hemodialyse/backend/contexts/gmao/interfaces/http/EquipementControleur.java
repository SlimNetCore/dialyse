package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsignerObservation;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsulterEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.CorrigerIdentification;
import com.hemodialyse.backend.contexts.gmao.application.equipement.CorrigerIdentificationCommande;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ListerParc;
import com.hemodialyse.backend.contexts.gmao.application.equipement.MettreHorsService;
import com.hemodialyse.backend.contexts.gmao.application.equipement.Reformer;
import com.hemodialyse.backend.contexts.gmao.application.equipement.RemettreEnService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipementInconnu;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
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
 * Adapter entrant HTTP du parc d'équipements : {@code /api/v1/gmao/equipements}, derrière la chaîne de filtres commune
 * (sécurité, licence, rôle, audit). Contrat (routes, statuts, corps, erreurs) verrouillé par la suite de caractérisation,
 * sauf l'écart décidé KB-GMAO-32 : la réforme exige le rôle {@code ADMIN} <b>et</b> l'habilitation {@code GMAO_REFORME}
 * (le legacy se contentait de l'habilitation). Les identifiants en chemin restent des {@code String} analysés par
 * {@link UUID#fromString} : message d'erreur 400 inchangé (KB-GMAO-35).
 */
@RestController
@RequestMapping(EquipementControleur.BASE)
@PreAuthorize("hasRole('ADMIN')")
public class EquipementControleur {

    public static final String BASE = "/api/v1/gmao/equipements";

    /**
     * Cache du référentiel des générateurs (lu par la Planification). Les écritures du parc l'évincent ici, de façon
     * synchrone : {@code @CacheEvict} s'exécute au retour de la méthode, donc après le commit du cas d'usage et avant la
     * réponse HTTP. Les observations ne changent ni statut ni identité : pas d'éviction.
     */
    private static final String GENERATEURS = "ref.generateurs";

    private final SessionCourante session;
    private final EnregistrerEquipement enregistrer;
    private final CorrigerIdentification corriger;
    private final MettreHorsService mettreHorsService;
    private final RemettreEnService remettreEnService;
    private final Reformer reformer;
    private final ConsignerObservation consignerObservation;
    private final ConsulterEquipement consulter;
    private final ListerParc lister;

    public EquipementControleur(SessionCourante session, EnregistrerEquipement enregistrer, CorrigerIdentification corriger,
                                MettreHorsService mettreHorsService, RemettreEnService remettreEnService,
                                Reformer reformer, ConsignerObservation consignerObservation, ConsulterEquipement consulter,
                                ListerParc lister) {
        this.session = session;
        this.enregistrer = enregistrer;
        this.corriger = corriger;
        this.mettreHorsService = mettreHorsService;
        this.remettreEnService = remettreEnService;
        this.reformer = reformer;
        this.consignerObservation = consignerObservation;
        this.consulter = consulter;
        this.lister = lister;
    }

    @PostMapping
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<EquipementReponse> enregistrer(@Valid @RequestBody EnregistrerEquipementRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EquipementReponse.depuis(enregistrer.executer(
                new EnregistrerEquipementCommande(session.centre(), requete.code(), requete.designation(), requete.type(),
                        requete.fabricant(), requete.modele(), requete.numeroSerie(), requete.dateInstallation().toInstant(),
                        requete.localisation(), requete.salleId(), requete.prixAcquisition(), session.utilisateur()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipementReponse> consulter(@PathVariable String id) {
        return consulter.executer(equipementId(id), session.centre())
                .map(EquipementReponse::depuis)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<EquipementReponse.Page> lister(@RequestParam(required = false) String statut,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(EquipementReponse.Page.depuis(lister.executer(session.centre(), statut, page, size)));
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<EquipementReponse> corriger(@PathVariable String id,
                                                      @Valid @RequestBody CorrigerIdentificationRequete requete) {
        return ResponseEntity.ok(EquipementReponse.depuis(corriger.executer(new CorrigerIdentificationCommande(
                equipementId(id), session.centre(), requete.designation(), requete.fabricant(), requete.modele(),
                requete.numeroSerie(), requete.localisation(), requete.salleId(), requete.prixAcquisition(),
                session.utilisateur()))));
    }

    @PostMapping("/{id}/hors-service")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<EquipementReponse> mettreHorsService(@PathVariable String id,
                                                               @Valid @RequestBody MettreHorsServiceRequete requete) {
        return ResponseEntity.ok(EquipementReponse.depuis(mettreHorsService.executer(
                equipementId(id), session.centre(), requete.raison(), session.utilisateur())));
    }

    @PostMapping("/{id}/reactiver")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<EquipementReponse> remettreEnService(@PathVariable String id) {
        return ResponseEntity.ok(EquipementReponse.depuis(remettreEnService.executer(
                equipementId(id), session.centre(), session.utilisateur())));
    }

    /** Écart décidé KB-GMAO-32 (2026-10-11) : ADMIN et GMAO_REFORME ; l'annotation de méthode remplace celle de la classe. */
    @PostMapping("/{id}/reformer")
    @PreAuthorize("hasRole('ADMIN') and hasRole('GMAO_REFORME')")
    @CacheEvict(cacheNames = GENERATEURS, allEntries = true)
    public ResponseEntity<EquipementReponse> reformer(@PathVariable String id, @Valid @RequestBody ReformerRequete requete) {
        return ResponseEntity.ok(EquipementReponse.depuis(reformer.executer(
                equipementId(id), session.centre(), requete.motif(), session.utilisateur())));
    }

    @PostMapping("/{id}/observations")
    public ResponseEntity<EquipementReponse> consignerObservation(@PathVariable String id,
                                                                  @Valid @RequestBody ConsignerObservationRequete requete) {
        return ResponseEntity.ok(EquipementReponse.depuis(consignerObservation.executer(
                equipementId(id), session.centre(), requete.observation(), session.utilisateur())));
    }

    /**
     * Type inconnu : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-35 : nom de
     * l'énumération legacy {@code TypeEquipement}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(TypeEquipementInconnu.class)
    ProblemDetail typeInconnu(TypeEquipementInconnu erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("No enum constant com.hemodialyse.backend.domain.gmao.model.TypeEquipement." + erreur.code());
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }

    private static EquipementId equipementId(String id) {
        return new EquipementId(UUID.fromString(id));
    }
}
