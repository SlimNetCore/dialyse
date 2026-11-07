package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesDonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DonneurVivantDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DonneurVivantReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.MiseAJourDeDonneurDemande;
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

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des donneurs vivants candidats : MÊMES URL, MÊMES champs JSON et MÊMES droits que le legacy (lecture MEDECIN et
 * ADMIN, écriture MEDECIN seul : KB-MED-95, à confirmer). Écarts décidés (ADR 0086) : patient inconnu ou d'un autre centre 404 à
 * l'enregistrement (KB-MED-86) ; donneur introuvable à la mise à jour 404 {@code DONNEUR_VIVANT_INTROUVABLE} (KB-MED-88, avant : 400) ;
 * entrées illisibles ou crossmatch absent 400 stable, 403 avant 400, bornes de colonne 400 (KB-MED-87 / 89) ; écriture concurrente 409
 * {@code DONNEUR_VIVANT_ECRITURE_CONCURRENTE} ; une suppression sans effet reste 204 mais n'est plus journalisée (KB-MED-88). Reproduit :
 * aucune règle croisée (KB-MED-93).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class DonneursVivantsControleur {

    private final ConsulterLesDonneursVivants consulter;
    private final EnregistrerUnDonneurVivant enregistrer;
    private final MettreAJourUnDonneurVivant mettreAJour;
    private final SupprimerUnDonneurVivant supprimer;

    public DonneursVivantsControleur(ConsulterLesDonneursVivants consulter, EnregistrerUnDonneurVivant enregistrer,
                                     MettreAJourUnDonneurVivant mettreAJour, SupprimerUnDonneurVivant supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.mettreAJour = mettreAJour;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/donneurs")
    public ResponseEntity<List<DonneurVivantReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(consulter.executer(centerId, patientId).stream().map(DonneurVivantReponse::de).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/greffe/donneurs")
    public ResponseEntity<DonneurVivantReponse> enregistrer(@PathVariable UUID patientId, @RequestBody DonneurVivantDemande d) {
        return ResponseEntity.ok(DonneurVivantReponse.de(enregistrer.executer(d.centerId(), patientId, d.nom(), d.prenom(), d.dateNaissance(),
                d.lienLu(), d.telephone(), d.groupeSanguin(), d.typageHla())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/donneurs/{donneurId}")
    public ResponseEntity<DonneurVivantReponse> mettreAJour(@PathVariable UUID patientId, @PathVariable UUID donneurId,
                                                            @RequestBody MiseAJourDeDonneurDemande d) {
        var lien = d.lienLu();
        var statut = d.statutLu();
        var crossmatch = d.crossmatchLu();
        return ResponseEntity.ok(DonneurVivantReponse.de(mettreAJour.executer(d.centerId(), patientId, donneurId, d.nom(), d.prenom(),
                d.dateNaissance(), lien, d.telephone(), d.groupeSanguin(), d.typageHla(), statut, crossmatch, d.dateCrossmatch(), d.bilanRealise(),
                d.contreIndications(), d.decisionFinale(), d.dateDecision())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/greffe/donneurs/{donneurId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID donneurId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, donneurId)) requete.setAttribute(AuditSansTrace.ATTRIBUT, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
