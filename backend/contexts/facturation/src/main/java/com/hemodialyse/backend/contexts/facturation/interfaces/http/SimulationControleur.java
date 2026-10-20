package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ChangerLeForfaitDUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ExclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ReinclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.SimulerLaFacturation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP de la simulation de facturation : mêmes URL et JSON que le legacy (slice F2), plus
 * {@code .../include} (réinclusion, ADR 0031) et les champs {@code motif} et {@code commentaire} de l'exclusion. Réservé à
 * {@code ADMIN} (KB-FAC-15 : le filtre des profils refusait déjà {@code MEDECIN}). Le centre demandé est confronté à celui de la
 * session (403 sinon, KB-FAC-14 corrigé).
 */
@RestController
@RequestMapping("/api/v1/facturation")
public class SimulationControleur {

    private final SessionCourante session;
    private final SimulerLaFacturation simuler;
    private final ExclureUneSeance exclure;
    private final ReinclureUneSeance reinclure;
    private final ChangerLeForfaitDUneSeance changerLeForfait;

    public SimulationControleur(SessionCourante session, SimulerLaFacturation simuler, ExclureUneSeance exclure,
                                ReinclureUneSeance reinclure, ChangerLeForfaitDUneSeance changerLeForfait) {
        this.session = session;
        this.simuler = simuler;
        this.exclure = exclure;
        this.reinclure = reinclure;
        this.changerLeForfait = changerLeForfait;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/preview")
    public ResponseEntity<SimulationReponse> simuler(@RequestBody @Valid SimulationRequete requete) {
        return ResponseEntity.ok(SimulationReponse.depuis(simuler.simuler(session.centre(requete.centerId()), requete.demande())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/preview/seances/{seanceId}/exclude")
    public ResponseEntity<SimulationReponse> exclure(@PathVariable UUID seanceId, @RequestBody @Valid ExclusionRequete requete) {
        return ResponseEntity.ok(SimulationReponse.depuis(exclure.executer(session.centre(requete.centerId()), seanceId,
                requete.commande(session.utilisateur()), requete.demande())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/preview/seances/{seanceId}/include")
    public ResponseEntity<SimulationReponse> reinclure(@PathVariable UUID seanceId, @RequestBody @Valid ReinclusionRequete requete) {
        return ResponseEntity.ok(SimulationReponse.depuis(
                reinclure.executer(session.centre(requete.centerId()), seanceId, requete.demande())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/preview/seances/{seanceId}/forfait")
    public ResponseEntity<SimulationReponse> changerLeForfait(@PathVariable UUID seanceId,
                                                              @RequestBody @Valid ChangementDeForfaitRequete requete) {
        return ResponseEntity.ok(SimulationReponse.depuis(changerLeForfait.executer(session.centre(requete.centerId()), seanceId,
                requete.forfaitId(), requete.userId(), requete.demande())));
    }
}
