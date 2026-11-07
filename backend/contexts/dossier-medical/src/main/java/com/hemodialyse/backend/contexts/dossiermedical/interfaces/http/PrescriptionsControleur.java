package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLaPrescriptionEnVigueur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesPrescriptionsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.PrescrireUnTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EcritureDePrescriptionReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageDePrescriptionsReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PrescriptionDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PrescriptionReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.RechercheDePrescriptionsDemande;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.UUID;

/**
 * Adapter entrant HTTP des prescriptions médicales : MÊMES URL, champs JSON et rôles que le legacy (MEDECIN écrit ; MEDECIN et
 * ADMIN lisent et recherchent ; INFIRMIER lit la prescription en vigueur seulement : KB-MED-23, à confirmer avec les métiers).
 * Écarts décidés (ADR 0081) : modifier une prescription inconnue ou d'un autre centre/patient = 404 sans création ni réaffectation
 * (KB-MED-17) ; patient inconnu ou d'un autre centre = 404 à la création (KB-MED-19) ; entrée illisible 400 (KB-MED-22).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PrescriptionsControleur {

    private final ConsulterLesPrescriptionsDuPatient consulter;
    private final ConsulterLaPrescriptionEnVigueur enVigueur;
    private final PrescrireUnTraitement prescrire;
    private final SupprimerUnePrescription supprimer;

    public PrescriptionsControleur(ConsulterLesPrescriptionsDuPatient consulter, ConsulterLaPrescriptionEnVigueur enVigueur,
                                   PrescrireUnTraitement prescrire, SupprimerUnePrescription supprimer) {
        this.consulter = consulter;
        this.enVigueur = enVigueur;
        this.prescrire = prescrire;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/prescriptions")
    public ResponseEntity<PageDePrescriptionsReponse> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageDePrescriptionsReponse.de(consulter.lister(centerId, patientId, from, to, page, size)));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/prescriptions/active")
    public ResponseEntity<PrescriptionReponse> enVigueur(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return enVigueur.executer(centerId, patientId, date).map(d -> ResponseEntity.ok(PrescriptionReponse.de(d)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PostMapping("/prescriptions/search")
    public ResponseEntity<PageDePrescriptionsReponse> rechercher(@RequestBody RechercheDePrescriptionsDemande demande) {
        return ResponseEntity.ok(PageDePrescriptionsReponse.de(consulter.rechercher(demande.centerId(), demande.patientId(), demande.page(),
                demande.size(), demande.dateFrom(), demande.dateTo())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/prescriptions")
    public ResponseEntity<EcritureDePrescriptionReponse> creer(@PathVariable UUID patientId, @RequestBody PrescriptionDemande demande) {
        return ResponseEntity.ok(EcritureDePrescriptionReponse.de(prescrire.creer(demande.centerId(), patientId, demande.contenu())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<EcritureDePrescriptionReponse> modifier(@PathVariable UUID patientId, @PathVariable UUID prescriptionId,
                                                                  @RequestBody PrescriptionDemande demande) {
        return ResponseEntity.ok(EcritureDePrescriptionReponse.de(prescrire.modifier(demande.centerId(), patientId, prescriptionId,
                demande.contenu())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID prescriptionId,
                                          @RequestParam(required = false) UUID centerId) {
        supprimer.executer(centerId, patientId, prescriptionId);
        return ResponseEntity.noContent().build();
    }
}
