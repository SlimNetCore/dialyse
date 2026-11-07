package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAntecedentsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.ResoudreUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AntecedentDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AntecedentReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ResolutionDAntecedentDemande;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.UUID;

/**
 * Adapter entrant HTTP des antécédents du patient : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (lecture MEDECIN
 * et ADMIN, écriture MEDECIN seul : KB-MED-69, à confirmer avec les métiers et le DPO). Écarts décidés (ADR 0084) : corps, date,
 * identifiant, paramètre, type, système ou statut illisible ou absent 400 stable sans nom de classe interne et 403 avant 400 pour un
 * profil sans droit (KB-MED-58 et 59, avant : 500) ; patient inconnu ou d'un autre centre 404 à l'écriture (KB-MED-61) ; bornes de
 * colonne 400 ; écriture concurrente 409 {@code ANTECEDENT_ECRITURE_CONCURRENTE} ; une suppression qui n'a rien supprimé répond toujours
 * 204 (reproduit) mais n'est plus inscrite au journal d'audit. Reproduit : réponses 200 (pas 201) ; antécédent introuvable à la
 * correction ou à la résolution = 400 ; lectures sans garde de patient (page vide) ; aucune lecture auditée.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AntecedentsControleur {
    /** Attribut de requête lu par le filtre d'audit de la plateforme : la requête n'a rien écrit, aucune ligne d'audit. */
    public static final String ATTRIBUT_SANS_TRACE = "hemodialyse.audit.sansTrace";

    private final ConsulterLesAntecedentsDuPatient consulter;
    private final EnregistrerUnAntecedent enregistrer;
    private final CorrigerUnAntecedent corriger;
    private final ResoudreUnAntecedent resoudre;
    private final SupprimerUnAntecedent supprimer;

    public AntecedentsControleur(ConsulterLesAntecedentsDuPatient consulter, EnregistrerUnAntecedent enregistrer,
                                 CorrigerUnAntecedent corriger, ResoudreUnAntecedent resoudre, SupprimerUnAntecedent supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.corriger = corriger;
        this.resoudre = resoudre;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/antecedents")
    public ResponseEntity<PageReponse<AntecedentReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.de(consulter.liste(centerId, patientId, page, size), AntecedentReponse::de));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/antecedents")
    public ResponseEntity<AntecedentReponse> enregistrer(@PathVariable UUID patientId, @RequestBody AntecedentDemande d) {
        return ResponseEntity.ok(AntecedentReponse.de(enregistrer.executer(d.centerId(), patientId, d.type(), d.codeSystem(), d.code(),
                d.codeDisplay(), d.libelleLibre(), d.dateDebut(), d.dateFin(), d.severite(), d.note())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/antecedents/{antecedentId}")
    public ResponseEntity<AntecedentReponse> corriger(@PathVariable UUID patientId, @PathVariable UUID antecedentId,
                                                      @RequestBody AntecedentDemande d) {
        return ResponseEntity.ok(AntecedentReponse.de(corriger.executer(d.centerId(), patientId, antecedentId, d.codeSystem(), d.code(),
                d.codeDisplay(), d.libelleLibre(), d.dateDebut(), d.dateFin(), d.statutClinique(), d.severite(), d.note())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/antecedents/{antecedentId}/resoudre")
    public ResponseEntity<AntecedentReponse> resoudre(@PathVariable UUID patientId, @PathVariable UUID antecedentId,
                                                      @RequestBody ResolutionDAntecedentDemande d) {
        return ResponseEntity.ok(AntecedentReponse.de(resoudre.executer(d.centerId(), patientId, antecedentId, d.dateResolution())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/antecedents/{antecedentId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID antecedentId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, antecedentId)) requete.setAttribute(ATTRIBUT_SANS_TRACE, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
