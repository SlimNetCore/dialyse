package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AnnulerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesOrdonnancesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.MarquerUneOrdonnanceImprimee;
import com.hemodialyse.backend.contexts.dossiermedical.application.RedigerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.SignerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.OrdonnanceDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.OrdonnanceReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageDOrdonnancesReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.TransitionDemande;
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
 * Adapter entrant HTTP des ordonnances : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (le MEDECIN écrit,
 * l'ADMIN consulte : KB-MED-23, à confirmer avec les métiers). Écarts décidés (ADR 0081) : patient inconnu ou d'un autre centre
 * 404 à la création (KB-MED-19) ; corps, date, identifiant ou paramètre illisible 400 (avant : 500, KB-MED-22). Reproduit :
 * un refus de signature CONSOMME un numéro, trou de la séquence légale conservé (KB-MED-20, à confirmer avec les métiers) ; réponses
 * 200 (pas 201) ; ordonnance introuvable = 400.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class OrdonnancesControleur {

    private final ConsulterLesOrdonnancesDuPatient consulter;
    private final RedigerUneOrdonnance rediger;
    private final SignerUneOrdonnance signer;
    private final MarquerUneOrdonnanceImprimee marquerImprimee;
    private final AnnulerUneOrdonnance annuler;

    public OrdonnancesControleur(ConsulterLesOrdonnancesDuPatient consulter, RedigerUneOrdonnance rediger, SignerUneOrdonnance signer,
                                 MarquerUneOrdonnanceImprimee marquerImprimee, AnnulerUneOrdonnance annuler) {
        this.consulter = consulter;
        this.rediger = rediger;
        this.signer = signer;
        this.marquerImprimee = marquerImprimee;
        this.annuler = annuler;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/ordonnances")
    public ResponseEntity<PageDOrdonnancesReponse> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageDOrdonnancesReponse.de(consulter.executer(centerId, patientId, page, size)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/ordonnances")
    public ResponseEntity<OrdonnanceReponse> creer(@PathVariable UUID patientId, @RequestBody OrdonnanceDemande demande) {
        return ResponseEntity.ok(OrdonnanceReponse.de(rediger.executer(demande.centerId(), patientId, demande.medecinId(),
                demande.datePrescription(), demande.lignesDemandees())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/signer")
    public ResponseEntity<OrdonnanceReponse> signer(@PathVariable UUID patientId, @PathVariable UUID ordonnanceId,
                                                    @RequestBody TransitionDemande demande) {
        return ResponseEntity.ok(OrdonnanceReponse.de(signer.executer(demande.centerId(), patientId, ordonnanceId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/imprimer")
    public ResponseEntity<OrdonnanceReponse> marquerImprimee(@PathVariable UUID patientId, @PathVariable UUID ordonnanceId,
                                                             @RequestBody TransitionDemande demande) {
        return ResponseEntity.ok(OrdonnanceReponse.de(marquerImprimee.executer(demande.centerId(), patientId, ordonnanceId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/ordonnances/{ordonnanceId}/annuler")
    public ResponseEntity<OrdonnanceReponse> annuler(@PathVariable UUID patientId, @PathVariable UUID ordonnanceId,
                                                     @RequestBody TransitionDemande demande) {
        return ResponseEntity.ok(OrdonnanceReponse.de(annuler.executer(demande.centerId(), patientId, ordonnanceId)));
    }
}
