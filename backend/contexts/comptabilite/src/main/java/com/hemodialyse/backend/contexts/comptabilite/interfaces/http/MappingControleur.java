package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ConsulterLeMapping;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ModifierLeMapping;
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
 * Adapter entrant HTTP du mapping comptable : {@code /api/v1/comptabilite/mapping}, administrateur seul, derrière la chaîne
 * de filtres commune. Le centre de chaque requête est confronté à celui de la session (403 sinon). Contrat verrouillé par la
 * suite de caractérisation.
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class MappingControleur {

    private final SessionCourante session;
    private final ConsulterLeMapping consulter;
    private final ModifierLeMapping modifier;

    public MappingControleur(SessionCourante session, ConsulterLeMapping consulter, ModifierLeMapping modifier) {
        this.session = session;
        this.consulter = consulter;
        this.modifier = modifier;
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/mapping")
    public ResponseEntity<MappingReponse> mapping(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(MappingReponse.depuis(consulter.executer(session.centre(centerId))));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/mapping")
    public ResponseEntity<MappingReponse> modifierLeMapping(@RequestBody @Valid MappingRequete requete) {
        return ResponseEntity.ok(MappingReponse.depuis(modifier.executer(session.centre(requete.centerId()), requete.commande())));
    }
}
