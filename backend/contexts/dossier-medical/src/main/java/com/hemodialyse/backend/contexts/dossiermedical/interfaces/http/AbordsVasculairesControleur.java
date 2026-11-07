package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAbordsVasculairesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AbordVasculaireDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AbordVasculaireReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EcritureDAbordVasculaireReponse;
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

import java.util.UUID;

/**
 * Adapter entrant HTTP des abords vasculaires du patient : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (lecture
 * MEDECIN, ADMIN et INFIRMIER, qui ponctionne l'abord ; écriture MEDECIN seul : KB-MED-69, reproduit, à confirmer avec les métiers et le
 * DPO). Écarts décidés (ADR 0084) : corps, date, identifiant, paramètre illisible ou type absent 400 stable et 403 avant 400 pour un
 * profil sans droit (KB-MED-58, avant : 500) ; patient inconnu ou d'un autre centre 404 à l'enregistrement (KB-MED-61) ; le PUT n'est plus
 * un « enregistrer » : identifiant inconnu, abord d'un autre patient ou d'un autre centre 404 {@code ABORD_VASCULAIRE_INTROUVABLE},
 * jamais créé ni réaffecté (KB-MED-62) ; bornes de colonne 400 ; écriture concurrente 409 {@code ABORD_VASCULAIRE_ECRITURE_CONCURRENTE} ;
 * une suppression qui n'a rien supprimé répond toujours 204 (reproduit) mais n'est plus inscrite au journal d'audit. Reproduit : réponses
 * 200 (pas 201) ; type libre (KB-MED-63) ; réponse d'écriture {@code id / patientId / updatedAt} ; lectures sans garde de patient ; aucune
 * lecture auditée.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AbordsVasculairesControleur {
    /** Attribut de requête lu par le filtre d'audit de la plateforme : la requête n'a rien écrit, aucune ligne d'audit. */
    public static final String ATTRIBUT_SANS_TRACE = "hemodialyse.audit.sansTrace";

    private final ConsulterLesAbordsVasculairesDuPatient consulter;
    private final EnregistrerUnAbordVasculaire enregistrer;
    private final ModifierUnAbordVasculaire modifier;
    private final SupprimerUnAbordVasculaire supprimer;

    public AbordsVasculairesControleur(ConsulterLesAbordsVasculairesDuPatient consulter, EnregistrerUnAbordVasculaire enregistrer,
                                       ModifierUnAbordVasculaire modifier, SupprimerUnAbordVasculaire supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.modifier = modifier;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<PageReponse<AbordVasculaireReponse>> lister(@PathVariable UUID patientId,
                                                                      @RequestParam(required = false) UUID centerId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.de(consulter.liste(centerId, patientId, page, size), AbordVasculaireReponse::de));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/abords-vasculaires")
    public ResponseEntity<EcritureDAbordVasculaireReponse> enregistrer(@PathVariable UUID patientId, @RequestBody AbordVasculaireDemande d) {
        return ResponseEntity.ok(EcritureDAbordVasculaireReponse.de(enregistrer.executer(d.centerId(), patientId, d.typeAbord(), d.cote(),
                d.localisation(), d.dateCreation(), d.dateFin(), d.actif(), d.complications())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/abords-vasculaires/{abordId}")
    public ResponseEntity<EcritureDAbordVasculaireReponse> modifier(@PathVariable UUID patientId, @PathVariable UUID abordId,
                                                                    @RequestBody AbordVasculaireDemande d) {
        return ResponseEntity.ok(EcritureDAbordVasculaireReponse.de(modifier.executer(d.centerId(), patientId, abordId, d.typeAbord(), d.cote(),
                d.localisation(), d.dateCreation(), d.dateFin(), d.actif(), d.complications())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/abords-vasculaires/{abordId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID abordId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, abordId)) requete.setAttribute(ATTRIBUT_SANS_TRACE, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
