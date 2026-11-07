package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAnalysesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AnalyseDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AnalyseReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EcritureDAnalyseReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PageReponse;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.RechercheDAnalysesDemande;
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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP des analyses du patient : MÊMES URL (dont {@code POST analyses/search}), MÊMES noms de champs JSON et MÊMES droits
 * que le legacy (lecture MEDECIN et ADMIN, écriture MEDECIN seul : KB-MED-78, à confirmer avec les métiers). Écarts décidés (ADR 0085) :
 * corps, date, identifiant, paramètre illisible, page ou taille de recherche absente 400 stable et 403 avant 400 pour un profil sans droit
 * (KB-MED-72, avant : 500) ; patient inconnu ou d'un autre centre 404 à l'enregistrement (KB-MED-73) ; le PUT n'est plus un « enregistrer » :
 * identifiant inconnu, analyse d'un autre patient ou d'un autre centre 404 {@code ANALYSE_INTROUVABLE}, jamais créée ni déplacée (KB-MED-74) ;
 * bornes de colonne 400 ; écriture concurrente 409 {@code ANALYSE_ECRITURE_CONCURRENTE} ; une suppression qui n'a rien supprimé répond
 * toujours 204 (reproduit) mais n'est plus inscrite au journal d'audit (KB-MED-75). Reproduit : réponses 200 (pas 201) ; réponse d'écriture
 * {@code id / patientId / updatedAt} ; aucune règle de valeur (KB-MED-79) ; lectures d'un patient inconnu = page vide ; la recherche, une
 * lecture, est journalisée comme une création (KB-MED-81, à revoir avec le DPO).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AnalysesControleur {

    private final ConsulterLesAnalysesDuPatient consulter;
    private final EnregistrerUneAnalyse enregistrer;
    private final ModifierUneAnalyse modifier;
    private final SupprimerUneAnalyse supprimer;

    public AnalysesControleur(ConsulterLesAnalysesDuPatient consulter, EnregistrerUneAnalyse enregistrer, ModifierUneAnalyse modifier,
                              SupprimerUneAnalyse supprimer) {
        this.consulter = consulter;
        this.enregistrer = enregistrer;
        this.modifier = modifier;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/analyses")
    public ResponseEntity<PageReponse<AnalyseReponse>> lister(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId,
                                                              @RequestParam(required = false) LocalDate from,
                                                              @RequestParam(required = false) LocalDate to,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.de(consulter.liste(centerId, patientId, from, to, page, size), AnalyseReponse::de));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PostMapping("/analyses/search")
    public ResponseEntity<PageReponse<AnalyseReponse>> rechercher(@RequestBody RechercheDAnalysesDemande d) {
        return ResponseEntity.ok(PageReponse.de(consulter.recherche(d.centerId(), d.patientId(), d.page(), d.size(), d.dateFrom(), d.dateTo()),
                AnalyseReponse::de));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/analyses")
    public ResponseEntity<EcritureDAnalyseReponse> enregistrer(@PathVariable UUID patientId, @RequestBody AnalyseDemande d) {
        return ResponseEntity.ok(EcritureDAnalyseReponse.de(enregistrer.executer(d.centerId(), patientId, d.datePrelevement(), d.mesures())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<EcritureDAnalyseReponse> modifier(@PathVariable UUID patientId, @PathVariable UUID analyseId,
                                                            @RequestBody AnalyseDemande d) {
        return ResponseEntity.ok(EcritureDAnalyseReponse.de(modifier.executer(d.centerId(), patientId, analyseId, d.datePrelevement(),
                d.mesures())));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID patientId, @PathVariable UUID analyseId,
                                          @RequestParam(required = false) UUID centerId, HttpServletRequest requete) {
        if (!supprimer.executer(centerId, patientId, analyseId)) requete.setAttribute(AuditSansTrace.ATTRIBUT, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }
}
