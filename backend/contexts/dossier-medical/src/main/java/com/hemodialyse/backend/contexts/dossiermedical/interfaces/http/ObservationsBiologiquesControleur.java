package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesObservationsBiologiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.CorrectionDObservationDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ObservationDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ObservationReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageReponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des observations biologiques du patient : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy
 * (lecture MEDECIN et ADMIN, écriture MEDECIN seul : KB-MED-78, à confirmer avec les métiers). Écarts décidés (ADR 0085) : corps, date,
 * identifiant, paramètre, énumération illisible ou absente 400 stable sans nom de classe interne et 403 avant 400 pour un profil sans droit
 * (KB-MED-72, avant : 500) ; patient inconnu ou d'un autre centre 404 à l'enregistrement (KB-MED-73) ; lecture par demande d'examen
 * limitée à une demande du centre et du patient de l'URL (KB-MED-77) ; bornes de colonne 400 ; écriture concurrente 409
 * {@code OBSERVATION_ECRITURE_CONCURRENTE} ; une suppression qui n'a rien supprimé répond toujours 204 (reproduit) mais n'est plus inscrite
 * au journal d'audit (KB-MED-75). Reproduit : réponses 200 (pas 201) ; observation introuvable à la correction = 400 (KB-MED-76) ; lectures
 * d'un patient inconnu = page vide ; aucune lecture auditée.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ObservationsBiologiquesControleur {

    private final ConsulterLesObservationsBiologiquesDuPatient consulter;
    private final EnregistrerUneObservationBiologique enregistrer;
    private final CorrigerUneObservationBiologique corriger;
    private final SupprimerUneObservationBiologique supprimer;

    public ObservationsBiologiquesControleur(ConsulterLesObservationsBiologiquesDuPatient consulter,
                                             EnregistrerUneObservationBiologique enregistrer, CorrigerUneObservationBiologique corriger,
                                             SupprimerUneObservationBiologique supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.corriger = corriger;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/observations")
    public ResponseEntity<PageReponse<ObservationReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                                  @RequestParam(required = false) String code,
                                                                  @RequestParam(required = false) LocalDate from,
                                                                  @RequestParam(required = false) LocalDate to,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.de(consulter.liste(centerId, patientId, code, from, to, page, size), ObservationReponse::de));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/demandes-examen/{demandeId}/observations")
    public ResponseEntity<List<ObservationReponse>> deLaDemande(@PathVariable UUID patientId, @PathVariable UUID demandeId,
                                                                @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(consulter.deLaDemande(centerId, patientId, demandeId).stream().map(ObservationReponse::de).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/observations")
    public ResponseEntity<ObservationReponse> enregistrer(@PathVariable UUID patientId, @RequestBody ObservationDemande d) {
        return ResponseEntity.ok(ObservationReponse.de(enregistrer.executer(d.centerId(), patientId, d.demandeExamenId(), d.codeSystem(), d.code(),
                d.codeDisplay(), d.valeurNum(), d.unite(), d.valeurTexte(), d.datePrelevement(), d.statut())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/observations/{observationId}")
    public ResponseEntity<ObservationReponse> corriger(@PathVariable UUID patientId, @PathVariable UUID observationId,
                                                       @RequestBody CorrectionDObservationDemande d) {
        return ResponseEntity.ok(ObservationReponse.de(corriger.executer(d.centerId(), patientId, observationId, d.valeurNum(), d.unite(),
                d.valeurTexte())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/observations/{observationId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID observationId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, observationId)) requete.setAttribute(AuditSansTrace.ATTRIBUT, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
