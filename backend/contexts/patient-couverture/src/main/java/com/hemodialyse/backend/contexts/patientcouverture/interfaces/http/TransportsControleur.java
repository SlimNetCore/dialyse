package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesTransports;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListesDeTransportEnCache.ChoixDeTransport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des listes de choix des transports (RF1) : MÊMES URLs et même JSON qu'avant, servies jusque-là par
 * l'Organisation ({@code /api/v1/referentials/{transporteurs,categories-transport}} avec {@code centerId} obligatoire, et
 * {@code /api/v1/organisation/referentiels/...} avec {@code centreId} facultatif). Lecture : tout utilisateur authentifié, bornée au
 * centre de la session (403 sinon). Les erreurs de paramètre restent celles de la plateforme.
 */
@RestController
public class TransportsControleur {

    private final ConsulterLesTransports consulter;
    private final ListesDeTransportEnCache listes;

    public TransportsControleur(ConsulterLesTransports consulter, ListesDeTransportEnCache listes) {
        this.consulter = consulter;
        this.listes = listes;
    }

    @GetMapping("/api/v1/referentials/transporteurs")
    public ResponseEntity<List<ChoixDeTransport>> transporteursLegacy(@RequestParam(required = false) UUID centerId) {
        // KB-RF1-01 corrigé (ADR 0123) : un centerId absent répond 400 BAD_REQUEST (c'était un 500)
        return ResponseEntity.ok(listes.transporteurs(consulter.centreDeLaLecture(ParametreObligatoireAbsent.centreObligatoire(centerId))));
    }

    @GetMapping("/api/v1/referentials/categories-transport")
    public ResponseEntity<List<ChoixDeTransport>> categoriesLegacy(@RequestParam(required = false) UUID centerId) {
        // RF1-R4 clos (RF3d, ADR 0129) : un centerId absent répond 400 BAD_REQUEST (c'était un 500), comme les transporteurs
        return ResponseEntity.ok(listes.categories(consulter.centreDeLaLecture(ParametreObligatoireAbsent.centreObligatoire(centerId))));
    }

    @GetMapping("/api/v1/organisation/referentiels/transporteurs")
    public List<ChoixDeTransport> transporteurs(@RequestParam(required = false) UUID centreId) {
        return listes.transporteurs(consulter.centreDeLaLecture(centreId));
    }

    @GetMapping("/api/v1/organisation/referentiels/categories-transport")
    public List<ChoixDeTransport> categories(@RequestParam(required = false) UUID centreId) {
        return listes.categories(consulter.centreDeLaLecture(centreId));
    }
}
