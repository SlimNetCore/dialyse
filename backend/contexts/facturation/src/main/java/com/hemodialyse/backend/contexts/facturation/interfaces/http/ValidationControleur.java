package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.validation.ResultatDeLaValidation;
import com.hemodialyse.backend.contexts.facturation.application.validation.ValiderLaFacturation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Adapter entrant HTTP de la validation de facturation : même URL et même JSON que le legacy (slice F3), réponse
 * {@code {createdInvoices, billedSeances}}. Réservé à {@code ADMIN} (KB-FAC-34 : le filtre des profils refusait déjà
 * {@code MEDECIN}). Le centre demandé est confronté à celui de la session (403 sinon, KB-FAC-33 corrigé). Refus propres à
 * l'émission : 409 numéro déjà pris, 422 séances sans forfait.
 */
@RestController
@RequestMapping("/api/v1/facturation")
public class ValidationControleur {

    private final SessionCourante session;
    private final ValiderLaFacturation valider;

    public ValidationControleur(SessionCourante session, ValiderLaFacturation valider) {
        this.session = session;
        this.valider = valider;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Integer>> valider(@RequestBody @Valid ValidationRequete requete) {
        ResultatDeLaValidation resultat = valider.valider(session.centre(requete.centerId()), requete.demande(),
                session.utilisateur());
        return ResponseEntity.ok(Map.of("createdInvoices", resultat.facturesEmises(), "billedSeances", resultat.seancesFacturees()));
    }
}
