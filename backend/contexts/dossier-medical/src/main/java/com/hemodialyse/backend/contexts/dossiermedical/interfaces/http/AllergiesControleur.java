package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAllergiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.DeclarerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AllergieDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AllergieReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.CorrectionDAllergieDemande;
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

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des allergies du patient : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (lecture MEDECIN et
 * ADMIN, y compris {@code critiques} ; écriture MEDECIN seul ; l'infirmier est refusé partout : KB-MED-69, à confirmer avec les métiers
 * et le DPO). Écarts décidés (ADR 0084) : corps, date, identifiant, paramètre, énumération illisible ou absente 400 stable sans nom de
 * classe interne et 403 avant 400 pour un profil sans droit (KB-MED-58 et 59, avant : 500) ; patient inconnu ou d'un autre centre 404 à la
 * déclaration (KB-MED-61) ; bornes de colonne 400 ; écriture concurrente 409 {@code ALLERGIE_ECRITURE_CONCURRENTE} ; une suppression qui
 * n'a rien supprimé répond toujours 204 (reproduit) mais n'est plus inscrite au journal d'audit. Reproduit : réponses 200 (pas 201) ;
 * allergie introuvable à la correction = 400 ; lectures sans garde de patient ; allergie réfutée toujours « critique » ; aucune lecture
 * auditée ; aucun événement publié.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AllergiesControleur {
    /** Attribut de requête lu par le filtre d'audit de la plateforme : la requête n'a rien écrit, aucune ligne d'audit. */
    public static final String ATTRIBUT_SANS_TRACE = "hemodialyse.audit.sansTrace";

    private final ConsulterLesAllergiesDuPatient consulter;
    private final DeclarerUneAllergie declarer;
    private final CorrigerUneAllergie corriger;
    private final SupprimerUneAllergie supprimer;

    public AllergiesControleur(ConsulterLesAllergiesDuPatient consulter, DeclarerUneAllergie declarer, CorrigerUneAllergie corriger,
                               SupprimerUneAllergie supprimer) {
        this.consulter = consulter;
        this.declarer = declarer;
        this.corriger = corriger;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/allergies")
    public ResponseEntity<PageReponse<AllergieReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.de(consulter.liste(centerId, patientId, page, size), AllergieReponse::de));
    }

    /** Bandeau permanent du dossier : les allergies de criticité haute. */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/allergies/critiques")
    public ResponseEntity<List<AllergieReponse>> critiques(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(consulter.critiques(centerId, patientId).stream().map(AllergieReponse::de).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/allergies")
    public ResponseEntity<AllergieReponse> declarer(@PathVariable UUID patientId, @RequestBody AllergieDemande d) {
        return ResponseEntity.ok(AllergieReponse.de(declarer.executer(d.centerId(), patientId, d.codeSystem(), d.code(), d.codeDisplay(),
                d.categorie(), d.criticite(), d.typeReaction(), d.manifestations(), d.dateConstatation(), d.statutVerification())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/allergies/{allergieId}")
    public ResponseEntity<AllergieReponse> corriger(@PathVariable UUID patientId, @PathVariable UUID allergieId,
                                                    @RequestBody CorrectionDAllergieDemande d) {
        return ResponseEntity.ok(AllergieReponse.de(corriger.executer(d.centerId(), patientId, allergieId, d.criticite(), d.manifestations(),
                d.statutVerification())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/allergies/{allergieId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID allergieId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, allergieId)) requete.setAttribute(ATTRIBUT_SANS_TRACE, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
