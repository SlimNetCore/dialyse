package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DossierMedicalDeBaseDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DossierMedicalDeBaseReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EcritureDuDossierMedicalReponse;
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

import java.util.UUID;

/**
 * Adapter entrant HTTP du dossier médical de base : MÊMES URL, MÊMES champs JSON et MÊMES droits que le legacy (lecture MEDECIN, ADMIN
 * et INFIRMIER ; écriture MEDECIN seul). Écarts décidés (ADR 0087) : patient inconnu ou d'un autre centre 404
 * {@code PATIENT_INTROUVABLE} à l'écriture (KB-MED-98) ; écriture simultanée 409 ; colonne trop longue, date ou corps illisible 400, 403
 * avant 400 (KB-MED-99). Reproduit : audit inégal (KB-MED-100), statuts d'hépatite libres.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DossierMedicalDeBaseControleur {

    private final ConsulterLeDossierMedicalDeBase consulter;
    private final EnregistrerLeDossierMedicalDeBase enregistrer;

    public DossierMedicalDeBaseControleur(ConsulterLeDossierMedicalDeBase consulter, EnregistrerLeDossierMedicalDeBase enregistrer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
    }

    /** Lecture ouverte à l'infirmier : le statut sérologique hépatite B et C conditionne l'isolement en machine. */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/dossier-medical")
    public ResponseEntity<DossierMedicalDeBaseReponse> lire(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return consulter.executer(centerId, patientId).map(d -> ResponseEntity.ok(DossierMedicalDeBaseReponse.de(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/dossier-medical")
    public ResponseEntity<EcritureDuDossierMedicalReponse> creer(@PathVariable UUID patientId, @RequestBody DossierMedicalDeBaseDemande d) {
        return ResponseEntity.ok(enregistrer(patientId, d));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/dossier-medical")
    public ResponseEntity<EcritureDuDossierMedicalReponse> remplacer(@PathVariable UUID patientId, @RequestBody DossierMedicalDeBaseDemande d) {
        return ResponseEntity.ok(enregistrer(patientId, d));
    }

    private EcritureDuDossierMedicalReponse enregistrer(UUID patientId, DossierMedicalDeBaseDemande d) {
        return EcritureDuDossierMedicalReponse.de(enregistrer.executer(d.centerId(), patientId, d.nephropathieInitiale(), d.dateMiseEnDialyse(),
                d.hepatiteBStatut(), d.hepatiteCStatut(), d.observationGlobale(), d.conclusionMedicale()));
    }
}
