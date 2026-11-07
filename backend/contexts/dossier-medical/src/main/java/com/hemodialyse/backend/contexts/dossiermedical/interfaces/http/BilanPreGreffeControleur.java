package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneDecisionDeRcp;
import com.hemodialyse.backend.contexts.dossiermedical.application.ChangerLeStatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLeBilanImmunologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLesNotesDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.BilanImmunologiqueDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.BilanPreGreffeReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DecisionRcpDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.NotesDuBilanDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.StatutDuBilanDemande;
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
 * Adapter entrant HTTP du bilan pré-greffe du receveur et de ses décisions de RCP : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits
 * que le legacy (lecture MEDECIN et ADMIN, écriture MEDECIN seul : KB-MED-95, à confirmer avec les métiers). Écarts décidés (ADR 0086) :
 * patient inconnu ou d'un autre centre 404 {@code PATIENT_INTROUVABLE} et plus aucun bilan créé pour lui (KB-MED-85 / 86) ; corps,
 * statut, avis, date ou identifiant absent ou illisible 400 stable et 403 avant 400 pour un profil sans droit (KB-MED-87 / 89) ; bornes de
 * colonne 400 ; un seul bilan par patient (KB-MED-90) ; écriture concurrente 409 {@code BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE}
 * (KB-MED-91). Reproduit : la première lecture d'un patient valide ouvre le bilan, réponses 200 (pas 201), machine à états, audit pauvre
 * {@code PATIENTS_*} et lectures non tracées (KB-MED-96, à revoir avec le DPO).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class BilanPreGreffeControleur {

    private final ConsulterLeBilanPreGreffe consulter;
    private final ChangerLeStatutDuBilanPreGreffe changerLeStatut;
    private final MettreAJourLeBilanImmunologique mettreAJourLImmunologie;
    private final MettreAJourLesNotesDuBilan mettreAJourLesNotes;
    private final AjouterUneDecisionDeRcp ajouterUneDecision;

    public BilanPreGreffeControleur(ConsulterLeBilanPreGreffe consulter, ChangerLeStatutDuBilanPreGreffe changerLeStatut,
                                    MettreAJourLeBilanImmunologique mettreAJourLImmunologie, MettreAJourLesNotesDuBilan mettreAJourLesNotes,
                                    AjouterUneDecisionDeRcp ajouterUneDecision) {
        this.consulter = consulter;
        this.changerLeStatut = changerLeStatut;
        this.mettreAJourLImmunologie = mettreAJourLImmunologie;
        this.mettreAJourLesNotes = mettreAJourLesNotes;
        this.ajouterUneDecision = ajouterUneDecision;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/greffe/bilan")
    public ResponseEntity<BilanPreGreffeReponse> consulter(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(BilanPreGreffeReponse.de(consulter.executer(centerId, patientId)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/statut")
    public ResponseEntity<BilanPreGreffeReponse> changerLeStatut(@PathVariable UUID patientId, @RequestBody StatutDuBilanDemande d) {
        return ResponseEntity.ok(BilanPreGreffeReponse.de(changerLeStatut.executer(d.centerId(), patientId, d.statutLu())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/immunologique")
    public ResponseEntity<BilanPreGreffeReponse> mettreAJourLImmunologie(@PathVariable UUID patientId, @RequestBody BilanImmunologiqueDemande d) {
        return ResponseEntity.ok(BilanPreGreffeReponse.de(mettreAJourLImmunologie.executer(d.centerId(), patientId, d.groupeSanguinConfirme(),
                d.typageHla(), d.praClasseI(), d.praClasseII())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/greffe/bilan/notes")
    public ResponseEntity<BilanPreGreffeReponse> mettreAJourLesNotes(@PathVariable UUID patientId, @RequestBody NotesDuBilanDemande d) {
        return ResponseEntity.ok(BilanPreGreffeReponse.de(mettreAJourLesNotes.executer(d.centerId(), patientId, d.contreIndications(),
                d.conclusionNephrologue())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/greffe/bilan/decisions-rcp")
    public ResponseEntity<BilanPreGreffeReponse> ajouterUneDecision(@PathVariable UUID patientId, @RequestBody DecisionRcpDemande d) {
        return ResponseEntity.ok(BilanPreGreffeReponse.de(ajouterUneDecision.executer(d.centerId(), patientId, d.dateReunion(), d.avisLu(),
                d.compteRendu(), d.prochaineDateRevue())));
    }
}
