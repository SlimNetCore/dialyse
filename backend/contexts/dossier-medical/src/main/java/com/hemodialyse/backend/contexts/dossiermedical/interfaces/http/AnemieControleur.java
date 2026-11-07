package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLObservance;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeSuiviDeLAnemie;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAdministrationsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.GererLesAlertesDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.application.SaisirUneAdministration;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AdministrationDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AdministrationReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AlerteReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ObservanceReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.SuiviDeLAnemieReponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du traitement de l'anémie : suivi, observance, alertes d'observance (liste et résolution) et administrations.
 * MÊMES URL, champs JSON et rôles que le legacy (KB-MED-23, à confirmer avec les métiers). Écarts décidés (ADR 0081) : les 10
 * administrations récentes du suivi sont les plus récentes (KB-MED-26) ; patient inconnu = 404 à la saisie (KB-MED-19) ; entrée illisible 400
 * (KB-MED-22). Reproduit : alerte inconnue 422 (KB-MED-29).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AnemieControleur {

    private final ConsulterLeSuiviDeLAnemie suivi;
    private final ConsulterLObservance observance;
    private final GererLesAlertesDObservance alertes;
    private final ConsulterLesAdministrationsDuPatient administrations;
    private final SaisirUneAdministration saisir;

    public AnemieControleur(ConsulterLeSuiviDeLAnemie suivi, ConsulterLObservance observance, GererLesAlertesDObservance alertes,
                            ConsulterLesAdministrationsDuPatient administrations, SaisirUneAdministration saisir) {
        this.suivi = suivi;
        this.observance = observance;
        this.alertes = alertes;
        this.administrations = administrations;
        this.saisir = saisir;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/suivi-anemie")
    public ResponseEntity<SuiviDeLAnemieReponse> suivi(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(SuiviDeLAnemieReponse.de(suivi.executer(centerId, patientId)));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/observance-anemie")
    public ResponseEntity<ObservanceReponse> observance(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(ObservanceReponse.de(observance.executer(centerId, patientId)));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/alertes-observance")
    public ResponseEntity<List<AlerteReponse>> alertes(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(alertes.lister(centerId, patientId).stream().map(AlerteReponse::de).toList());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PutMapping("/{patientId}/alertes-observance/{alerteId}/resoudre")
    public ResponseEntity<AlerteReponse> resoudre(@PathVariable UUID patientId, @PathVariable UUID alerteId,
                                                  @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(AlerteReponse.de(alertes.resoudre(centerId, patientId, alerteId)));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<AdministrationReponse.Page> administrations(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AdministrationReponse.Page.de(administrations.executer(centerId, patientId, page, size)));
    }

    @PreAuthorize("hasAnyRole('INFIRMIER','MEDECIN')")
    @PostMapping("/{patientId}/administrations-anemie")
    public ResponseEntity<AdministrationReponse> saisir(@PathVariable UUID patientId, @RequestBody AdministrationDemande demande) {
        return ResponseEntity.ok(AdministrationReponse.de(saisir.executer(demande.centerId(), patientId, demande.saisie())));
    }
}
