package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.parametres.ConfigurerLaFacturation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP des paramètres de facturation : {@code /api/v1/facturation/settings}, mêmes URL et JSON que le legacy
 * (slice F1). Les autres routes de {@code /api/v1/facturation} restent au legacy jusqu'aux slices F2, F3 et F5. Lecture :
 * {@code ADMIN}, {@code MEDECIN}, {@code SECRETAIRE} (le filtre des profils refuse encore {@code MEDECIN}, KB-FAC-11
 * reproduit) ; modification : {@code ADMIN}. Le centre demandé est confronté à celui de la session (403 sinon, KB-FAC-01
 * corrigé).
 */
@RestController
@RequestMapping("/api/v1/facturation")
public class ParametresFacturationControleur {

    private final SessionCourante session;
    private final ConfigurerLaFacturation configurer;

    public ParametresFacturationControleur(SessionCourante session, ConfigurerLaFacturation configurer) {
        this.session = session;
        this.configurer = configurer;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping("/settings")
    public ResponseEntity<ParametresFacturationReponse> consulter(@RequestParam UUID centerId) {
        return ResponseEntity.ok(ParametresFacturationReponse.depuis(configurer.consulter(session.centre(centerId))));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public ResponseEntity<ParametresFacturationReponse> modifier(@RequestBody @Valid ParametresFacturationRequete requete) {
        return ResponseEntity.ok(ParametresFacturationReponse.depuis(
                configurer.modifier(session.centre(requete.centerId()), requete.commande(), requete.userId())));
    }
}
