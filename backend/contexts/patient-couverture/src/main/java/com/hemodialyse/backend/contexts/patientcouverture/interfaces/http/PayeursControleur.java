package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListesDePayeursEnCache.ChoixDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListesDePayeursEnCache.DetailDePayeur;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des listes de choix des payeurs (RF3c) : MÊMES URLs, même JSON, mêmes refus qu'avant, servies jusque-là par
 * l'Organisation ({@code /api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}} avec {@code centerId}
 * obligatoire, et {@code /api/v1/organisation/referentiels/...} avec {@code centreId} facultatif). Lecture : tout utilisateur
 * authentifié, bornée au centre de la session (403 sinon). Un {@code centerId} absent reste une erreur 500 comme avant (le paramètre est
 * obligatoire, non corrigé : même comportement qu'avant RF3c).
 */
@RestController
public class PayeursControleur {

    private final ConsulterLesPayeurs consulter;
    private final ListesDePayeursEnCache listes;

    public PayeursControleur(ConsulterLesPayeurs consulter, ListesDePayeursEnCache listes) {
        this.consulter = consulter;
        this.listes = listes;
    }

    @GetMapping("/api/v1/referentials/centres-payeurs")
    public ResponseEntity<List<ChoixDePayeur>> centresPayeursLegacy(@RequestParam UUID centerId) {
        return ResponseEntity.ok(listes.centresPayeurs(consulter.centreDeLaLecture(centerId)));
    }

    @GetMapping("/api/v1/referentials/centres-payeurs-details")
    public ResponseEntity<List<DetailDePayeur>> detailsLegacy(@RequestParam UUID centerId) {
        return ResponseEntity.ok(listes.details(consulter.centreDeLaLecture(centerId)));
    }

    @GetMapping("/api/v1/referentials/caisses")
    public ResponseEntity<List<ChoixDePayeur>> caissesLegacy(@RequestParam UUID centerId) {
        return ResponseEntity.ok(listes.caisses(consulter.centreDeLaLecture(centerId)));
    }

    @GetMapping("/api/v1/referentials/agences")
    public ResponseEntity<List<ChoixDePayeur>> agencesLegacy(@RequestParam UUID centerId) {
        return ResponseEntity.ok(listes.agences(consulter.centreDeLaLecture(centerId)));
    }

    @GetMapping("/api/v1/organisation/referentiels/centres-payeurs")
    public List<ChoixDePayeur> centresPayeurs(@RequestParam(required = false) UUID centreId) {
        return listes.centresPayeurs(consulter.centreDeLaLecture(centreId));
    }

    @GetMapping("/api/v1/organisation/referentiels/centres-payeurs-details")
    public List<DetailDePayeur> details(@RequestParam(required = false) UUID centreId) {
        return listes.details(consulter.centreDeLaLecture(centreId));
    }

    @GetMapping("/api/v1/organisation/referentiels/agences")
    public List<ChoixDePayeur> agences(@RequestParam(required = false) UUID centreId) {
        return listes.agences(consulter.centreDeLaLecture(centreId));
    }

    @GetMapping("/api/v1/organisation/referentiels/caisses")
    public List<ChoixDePayeur> caisses(@RequestParam(required = false) UUID centreId) {
        return listes.caisses(consulter.centreDeLaLecture(centreId));
    }
}
