package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListeDesForfaitsEnCache.ChoixDeForfait;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la liste de choix des forfaits (RF2) : MÊMES URLs et même JSON qu'avant, servies jusque-là par
 * l'Organisation ({@code /api/v1/referentials/forfaits} avec {@code centerId} obligatoire, et
 * {@code /api/v1/organisation/referentiels/forfaits} avec {@code centreId} facultatif). Lecture : tout utilisateur authentifié,
 * bornée au centre de la session (403 sinon). Un {@code centerId} absent répond 400 {@code BAD_REQUEST} (KB-RF2-01 corrigé, RF2c, ADR 0125 ; c'était un 500).
 */
@RestController
public class ForfaitsControleur {

    private final ConsulterLesForfaits consulter;
    private final ListeDesForfaitsEnCache liste;

    public ForfaitsControleur(ConsulterLesForfaits consulter, ListeDesForfaitsEnCache liste) {
        this.consulter = consulter;
        this.liste = liste;
    }

    @GetMapping("/api/v1/referentials/forfaits")
    public ResponseEntity<List<ChoixDeForfait>> forfaitsLegacy(@RequestParam(required = false) UUID centerId) {
        // KB-RF2-01 corrigé (ADR 0125) : un centerId absent répond 400 BAD_REQUEST (c'était un 500)
        return ResponseEntity.ok(liste.forfaits(consulter.centreDeLaLecture(ParametreObligatoireAbsent.centreObligatoire(centerId))));
    }

    @GetMapping("/api/v1/organisation/referentiels/forfaits")
    public List<ChoixDeForfait> forfaits(@RequestParam(required = false) UUID centreId) {
        return liste.forfaits(consulter.centreDeLaLecture(centreId));
    }
}
