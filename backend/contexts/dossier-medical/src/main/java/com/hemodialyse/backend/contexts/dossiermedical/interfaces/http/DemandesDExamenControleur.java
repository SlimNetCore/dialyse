package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AnnulerUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesDemandesDExamenDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.DemanderUnExamen;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLePrelevement;
import com.hemodialyse.backend.contexts.dossiermedical.application.RendreLeResultatDisponible;
import com.hemodialyse.backend.contexts.dossiermedical.application.ValiderUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DemandeDExamenDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DemandeDExamenReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageDeDemandesDExamenReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.TransitionDeDemandeDExamen;
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
 * Adapter entrant HTTP des demandes d'examen : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (le MEDECIN écrit,
 * l'ADMIN consulte : KB-MED-40, à confirmer avec les métiers). Écarts décidés (ADR 0082) : patient inconnu ou d'un autre centre 404 à la
 * demande (KB-MED-38) ; corps, date, identifiant, paramètre ou champ trop long illisible 400, et 403 avant 400 pour un profil sans
 * droit (KB-MED-35, avant : 500). Reproduit : réponses 200 (pas 201) ; demande introuvable = 400 (KB-MED-39) ; aucune route de lecture
 * d'une demande seule, de modification du contenu ni de suppression (les routes inexistantes restent traitées par la plateforme). La
 * route {@code GET .../demandes-examen/{id}/observations} appartient à DM6.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DemandesDExamenControleur {

    private final ConsulterLesDemandesDExamenDuPatient consulter;
    private final DemanderUnExamen demander;
    private final EnregistrerLePrelevement prelever;
    private final RendreLeResultatDisponible rendreDisponible;
    private final ValiderUneDemandeDExamen valider;
    private final AnnulerUneDemandeDExamen annuler;

    public DemandesDExamenControleur(ConsulterLesDemandesDExamenDuPatient consulter, DemanderUnExamen demander,
                                     EnregistrerLePrelevement prelever, RendreLeResultatDisponible rendreDisponible,
                                     ValiderUneDemandeDExamen valider, AnnulerUneDemandeDExamen annuler) {
        this.consulter = consulter;
        this.demander = demander;
        this.prelever = prelever;
        this.rendreDisponible = rendreDisponible;
        this.valider = valider;
        this.annuler = annuler;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/demandes-examen")
    public ResponseEntity<PageDeDemandesDExamenReponse> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageDeDemandesDExamenReponse.de(consulter.executer(centerId, patientId, page, size)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/demandes-examen")
    public ResponseEntity<DemandeDExamenReponse> creer(@PathVariable UUID patientId, @RequestBody DemandeDExamenDemande demande) {
        return ResponseEntity.ok(DemandeDExamenReponse.de(demander.executer(demande.centerId(), patientId, demande.prescripteurId(),
                demande.dateDemande(), demande.categorie(), demande.urgent(), demande.motif(), demande.lignesDemandees())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/preleve")
    public ResponseEntity<DemandeDExamenReponse> preleve(@PathVariable UUID patientId, @PathVariable UUID demandeId,
                                                         @RequestBody TransitionDeDemandeDExamen demande) {
        return ResponseEntity.ok(DemandeDExamenReponse.de(prelever.executer(demande.centerId(), patientId, demandeId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/resultat-disponible")
    public ResponseEntity<DemandeDExamenReponse> marquerResultatDisponible(@PathVariable UUID patientId, @PathVariable UUID demandeId,
                                                                           @RequestBody TransitionDeDemandeDExamen demande) {
        return ResponseEntity.ok(DemandeDExamenReponse.de(rendreDisponible.executer(demande.centerId(), patientId, demandeId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/valider")
    public ResponseEntity<DemandeDExamenReponse> valider(@PathVariable UUID patientId, @PathVariable UUID demandeId,
                                                         @RequestBody TransitionDeDemandeDExamen demande) {
        return ResponseEntity.ok(DemandeDExamenReponse.de(valider.executer(demande.centerId(), patientId, demandeId, demande.conclusion())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/demandes-examen/{demandeId}/annuler")
    public ResponseEntity<DemandeDExamenReponse> annuler(@PathVariable UUID patientId, @PathVariable UUID demandeId,
                                                         @RequestBody TransitionDeDemandeDExamen demande) {
        return ResponseEntity.ok(DemandeDExamenReponse.de(annuler.executer(demande.centerId(), patientId, demandeId)));
    }
}
