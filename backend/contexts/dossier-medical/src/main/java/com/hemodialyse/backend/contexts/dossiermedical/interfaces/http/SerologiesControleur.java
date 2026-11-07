package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesSerologiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.CorrectionDeSerologieDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageDeSerologiesReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.SerologieDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.SerologieReponse;
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
 * Adapter entrant HTTP des sérologies du patient : MÊMES URL, MÊMES noms de champs JSON et MÊMES droits que le legacy (lecture
 * MEDECIN et ADMIN, écriture MEDECIN seul : KB-MED-55, à confirmer avec les métiers et le DPO). Écarts décidés (ADR 0083) : corps, date,
 * identifiant, paramètre, marqueur ou résultat illisible ou absent 400 stable sans nom de classe interne et 403 avant 400 pour un
 * profil sans droit (KB-MED-47 et 48, avant : 500) ; bornes de colonne 400 (KB-MED-52) ; écriture concurrente 409
 * {@code SEROLOGIE_ECRITURE_CONCURRENTE} ; une suppression qui n'a rien supprimé répond toujours 204 (reproduit) mais n'est plus inscrite
 * au journal d'audit (KB-MED-49). Reproduit : réponses 200 (pas 201) ; sérologie introuvable à la correction = 400 ; lectures sans
 * garde de patient (page vide) ; aucune lecture auditée.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class SerologiesControleur {

    /** Attribut de requête lu par le filtre d'audit de la plateforme : la requête n'a rien écrit, aucune ligne d'audit. */
    public static final String ATTRIBUT_SANS_TRACE = "hemodialyse.audit.sansTrace";

    private final ConsulterLesSerologiesDuPatient consulter;
    private final EnregistrerUneSerologie enregistrer;
    private final CorrigerUneSerologie corriger;
    private final SupprimerUneSerologie supprimer;

    public SerologiesControleur(ConsulterLesSerologiesDuPatient consulter, EnregistrerUneSerologie enregistrer, CorrigerUneSerologie corriger,
                                SupprimerUneSerologie supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.corriger = corriger;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/serologies")
    public ResponseEntity<PageDeSerologiesReponse> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageDeSerologiesReponse.de(consulter.liste(centerId, patientId, page, size)));
    }

    /** Bandeau « statut sérologique » : le dernier résultat connu pour chaque marqueur. */
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/serologies/synthese")
    public ResponseEntity<List<SerologieReponse>> synthese(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(consulter.synthese(centerId, patientId).stream().map(SerologieReponse::de).toList());
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/serologies")
    public ResponseEntity<SerologieReponse> enregistrer(@PathVariable UUID patientId, @RequestBody SerologieDemande demande) {
        return ResponseEntity.ok(SerologieReponse.de(enregistrer.executer(demande.centerId(), patientId, demande.marqueur(), demande.resultat(),
                demande.titre(), demande.unite(), demande.datePrelevement(), demande.laboratoire(), demande.dateProchainControle(),
                demande.conduiteATenir())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/serologies/{serologieId}")
    public ResponseEntity<SerologieReponse> corriger(@PathVariable UUID patientId, @PathVariable UUID serologieId,
                                                     @RequestBody CorrectionDeSerologieDemande demande) {
        return ResponseEntity.ok(SerologieReponse.de(corriger.executer(demande.centerId(), patientId, serologieId, demande.resultat(),
                demande.conduiteATenir())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/serologies/{serologieId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID serologieId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, serologieId)) requete.setAttribute(ATTRIBUT_SANS_TRACE, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
