package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesConstantesDuPatient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP de DM1 : MÊME URL que le legacy ({@code GET /api/v1/patients/{patientId}/constantes}, nom de la variable
 * de chemin compris : le journal d'audit en dépend), même JSON champ par champ. Lecture seule. Droits du LEGACY inchangés :
 * MEDECIN et ADMIN (le MEDECIN lit mais n'écrit pas : KB-MED-08, à décider avec les métiers).
 * <p>
 * Écarts décidés (06/11/2026) : {@code centerId} différent du centre du jeton : 403 (avant : seul le centre de la session était
 * lu, sans 403 pour le {@code centerId} d'un autre centre, voir KB-MED-01/02) ; paramètres illisibles 400 (KB-MED-06/07, avant
 * 500) ; taille de page de 1 à 100 (KB-MED-06). Reproduit : patient inconnu ou d'un autre centre = page vide en 200 (KB-MED-03).
 * Aucun audit de la lecture (KB-MED-04 : question RGPD/DPO).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ConstantesControleur {

    private final ConsulterLesConstantesDuPatient consulter;

    public ConstantesControleur(ConsulterLesConstantesDuPatient consulter) {
        this.consulter = consulter;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/constantes")
    public ResponseEntity<PageDeConstantesReponse> lister(@PathVariable UUID patientId,
                                                          @RequestParam(required = false) UUID centerId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageDeConstantesReponse.de(consulter.executer(centerId, patientId, page, size)));
    }
}
