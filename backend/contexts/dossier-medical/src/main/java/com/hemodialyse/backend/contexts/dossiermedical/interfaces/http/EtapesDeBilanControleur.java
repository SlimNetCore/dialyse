package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesEtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenererLesEtapesStandard;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EtapeDeBilanDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EtapeDeBilanReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.MiseAJourDEtapeDemande;
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
 * Adapter entrant HTTP de la checklist du bilan pré-greffe : MÊMES URL, MÊMES champs JSON et MÊMES droits que le legacy (lecture MEDECIN,
 * ADMIN et INFIRMIER ; création, génération et mise à jour MEDECIN et INFIRMIER ; suppression MEDECIN seul : KB-MED-95, à confirmer).
 * Écarts décidés (ADR 0086) : patient inconnu ou d'un autre centre 404 à la création et à la génération (KB-MED-86) ; étape introuvable
 * à la mise à jour 404 {@code ETAPE_BILAN_INTROUVABLE} (KB-MED-88, avant : 400) ; entrées illisibles 400 stable, 403 avant 400,
 * libellé de plus de 255 caractères 400 (KB-MED-87 / 89) ; génération sans doublon (KB-MED-90) ; écriture concurrente 409
 * {@code ETAPE_BILAN_ECRITURE_CONCURRENTE} (KB-MED-91) ; une suppression sans effet reste 204 mais n'est plus journalisée (KB-MED-88).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class EtapesDeBilanControleur {

    private final ConsulterLesEtapesDeBilan consulter;
    private final AjouterUneEtapeDeBilan ajouter;
    private final GenererLesEtapesStandard generer;
    private final MettreAJourUneEtapeDeBilan mettreAJour;
    private final SupprimerUneEtapeDeBilan supprimer;

    public EtapesDeBilanControleur(ConsulterLesEtapesDeBilan consulter, AjouterUneEtapeDeBilan ajouter, GenererLesEtapesStandard generer,
                                   MettreAJourUneEtapeDeBilan mettreAJour, SupprimerUneEtapeDeBilan supprimer) {
        this.consulter = consulter;
        this.ajouter = ajouter;
        this.generer = generer;
        this.mettreAJour = mettreAJour;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/greffe/etapes")
    public ResponseEntity<List<EtapeDeBilanReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(consulter.executer(centerId, patientId).stream().map(EtapeDeBilanReponse::de).toList());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PostMapping("/{patientId}/greffe/etapes")
    public ResponseEntity<EtapeDeBilanReponse> ajouter(@PathVariable UUID patientId, @RequestBody EtapeDeBilanDemande d) {
        return ResponseEntity.ok(EtapeDeBilanReponse.de(ajouter.executer(d.centerId(), patientId, d.categorieLue(), d.libelle())));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PostMapping("/{patientId}/greffe/etapes/generer-standard")
    public ResponseEntity<List<EtapeDeBilanReponse>> genererLaListeStandard(@PathVariable UUID patientId,
                                                                            @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(generer.executer(centerId, patientId).stream().map(EtapeDeBilanReponse::de).toList());
    }

    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER')")
    @PutMapping("/{patientId}/greffe/etapes/{etapeId}")
    public ResponseEntity<EtapeDeBilanReponse> mettreAJour(@PathVariable UUID patientId, @PathVariable UUID etapeId,
                                                           @RequestBody MiseAJourDEtapeDemande d) {
        return ResponseEntity.ok(EtapeDeBilanReponse.de(mettreAJour.executer(d.centerId(), patientId, etapeId, d.statutLu(), d.dateRealisation(),
                d.resultat(), d.dateExpiration(), d.demandeExamenId(), d.serologieId())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/greffe/etapes/{etapeId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID etapeId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, etapeId)) requete.setAttribute(AuditSansTrace.ATTRIBUT, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
