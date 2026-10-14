package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ConsulterPlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EnregistrerExecution;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EquipementInconnu;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EtablirPlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EtablirPlanCommande;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansActifs;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansDEquipement;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansDuCentre;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansEnRetard;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ReprendrePlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.SuspendrePlan;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des plans de maintenance préventive : {@code /api/v1/gmao/plans-maintenance}. Il passe par la
 * chaîne de filtres commune (sécurité, licence, rôle, audit). Contrat (routes, statuts, corps, erreurs) verrouillé par la
 * suite de caractérisation. Les identifiants en chemin restent des {@code String} analysés par {@link UUID#fromString} :
 * message d'erreur 400 inchangé (KB-GMAO-16).
 */
@RestController
@RequestMapping(PlanMaintenanceControleur.BASE)
@PreAuthorize("hasRole('ADMIN')")
public class PlanMaintenanceControleur {

    public static final String BASE = "/api/v1/gmao/plans-maintenance";

    private final SessionCourante session;
    private final EtablirPlan etablirPlan;
    private final EnregistrerExecution enregistrerExecution;
    private final SuspendrePlan suspendrePlan;
    private final ReprendrePlan reprendrePlan;
    private final ListerPlansDuCentre listerPlansDuCentre;
    private final ListerPlansActifs listerPlansActifs;
    private final ListerPlansEnRetard listerPlansEnRetard;
    private final ListerPlansDEquipement listerPlansDEquipement;
    private final ConsulterPlan consulterPlan;

    public PlanMaintenanceControleur(SessionCourante session, EtablirPlan etablirPlan, EnregistrerExecution enregistrerExecution,
                                     SuspendrePlan suspendrePlan, ReprendrePlan reprendrePlan, ConsulterPlan consulterPlan,
                                     ListerPlansDuCentre listerPlansDuCentre, ListerPlansActifs listerPlansActifs,
                                     ListerPlansEnRetard listerPlansEnRetard, ListerPlansDEquipement listerPlansDEquipement) {
        this.session = session;
        this.etablirPlan = etablirPlan;
        this.enregistrerExecution = enregistrerExecution;
        this.suspendrePlan = suspendrePlan;
        this.reprendrePlan = reprendrePlan;
        this.consulterPlan = consulterPlan;
        this.listerPlansDuCentre = listerPlansDuCentre;
        this.listerPlansActifs = listerPlansActifs;
        this.listerPlansEnRetard = listerPlansEnRetard;
        this.listerPlansDEquipement = listerPlansDEquipement;
    }

    @PostMapping
    public ResponseEntity<PlanMaintenanceReponse> etablir(@Valid @RequestBody EtablirPlanRequete requete) {
        FrequenceMaintenance frequence = FrequenceMaintenance.valueOf(requete.frequence());
        PlanMaintenanceVue plan = etablirPlan.executer(new EtablirPlanCommande(
                new EquipementId(requete.equipementId()),
                session.centre(),
                requete.designation(),
                requete.description(),
                frequence,
                requete.prochaineDatePrevue().toInstant(),
                requete.tachesAEffectuer(),
                session.utilisateur()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanMaintenanceReponse.depuis(plan));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanMaintenanceReponse> consulter(@PathVariable String id) {
        return consulterPlan.executer(session.centre(), planId(id))
                .map(PlanMaintenanceReponse::depuis)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PlanMaintenanceReponse>> lister() {
        return ResponseEntity.ok(reponses(listerPlansDuCentre.executer(session.centre())));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<PlanMaintenanceReponse>> listerActifs() {
        return ResponseEntity.ok(reponses(listerPlansActifs.executer(session.centre())));
    }

    @GetMapping("/en-retard")
    public ResponseEntity<List<PlanMaintenanceReponse>> listerEnRetard() {
        return ResponseEntity.ok(reponses(listerPlansEnRetard.executer(session.centre())));
    }

    @GetMapping("/equipement/{equipementId}")
    public ResponseEntity<List<PlanMaintenanceReponse>> listerParEquipement(@PathVariable String equipementId) {
        return ResponseEntity.ok(reponses(listerPlansDEquipement.executer(session.centre(), new EquipementId(UUID.fromString(equipementId)))));
    }

    @PostMapping("/{id}/executer")
    public ResponseEntity<PlanMaintenanceReponse> executer(@PathVariable String id) {
        return ResponseEntity.ok(PlanMaintenanceReponse.depuis(
                enregistrerExecution.executer(session.centre(), planId(id), session.utilisateur())));
    }

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<PlanMaintenanceReponse> suspendre(@PathVariable String id) {
        return ResponseEntity.ok(PlanMaintenanceReponse.depuis(
                suspendrePlan.executer(session.centre(), planId(id), session.utilisateur())));
    }

    @PostMapping("/{id}/reactiver")
    public ResponseEntity<PlanMaintenanceReponse> reprendre(@PathVariable String id,
                                                            @Valid @RequestBody ReprendrePlanRequete requete) {
        return ResponseEntity.ok(PlanMaintenanceReponse.depuis(
                reprendrePlan.executer(session.centre(), planId(id), requete.nouvelleDatePrevue().toInstant(), session.utilisateur())));
    }

    /**
     * Équipement inconnu à l'établissement d'un plan : 400 avec le code stable {@code EQUIPEMENT_INCONNU} (écart décidé
     * KB-GMAO-14 : le legacy répondait 500 par violation de clé étrangère). 400 comme « Plan non trouvé »
     * sur les commandes (KB-GMAO-16) ; même forme de corps que les autres erreurs 400 du legacy.
     */
    @ExceptionHandler(EquipementInconnu.class)
    ProblemDetail equipementInconnu(EquipementInconnu erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", EquipementInconnu.CODE);
        return probleme;
    }

    private static PlanMaintenanceId planId(String id) {
        return new PlanMaintenanceId(UUID.fromString(id));
    }

    private static List<PlanMaintenanceReponse> reponses(List<PlanMaintenanceVue> vues) {
        return vues.stream().map(PlanMaintenanceReponse::depuis).toList();
    }
}
