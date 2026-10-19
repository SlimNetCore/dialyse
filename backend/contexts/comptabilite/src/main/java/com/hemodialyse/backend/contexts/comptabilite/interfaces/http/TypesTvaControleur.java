package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.GererLesTypesDeTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

/**
 * Adapter entrant HTTP de l'écran « Types de TVA » : {@code /api/v1/tva-types}, mêmes URL et JSON que le legacy, devenu une
 * façade du contexte Comptabilité, propriétaire de {@code tva_types} (ADR 0030, slice F1). Consultation : {@code ADMIN},
 * {@code MEDECIN}, {@code SECRETAIRE} ; écriture : {@code ADMIN}. Le centre demandé est confronté à celui de la session
 * (403 sinon, KB-FAC-01 corrigé). Plus de cache de la liste : elle est relue à chaque appel, quel que soit l'écran qui a
 * écrit (KB-CPT-20 sans objet).
 */
@RestController
@RequestMapping("/api/v1/tva-types")
public class TypesTvaControleur {

    private final SessionCourante session;
    private final GererLesTypesDeTva types;

    public TypesTvaControleur(SessionCourante session, GererLesTypesDeTva types) {
        this.session = session;
        this.types = types;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<PageReponse<TypeTvaReponse>> lister(@RequestParam UUID centerId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.depuis(types.lister(session.centre(centerId), page, size), TypeTvaReponse::depuis));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TypeTvaReponse> creer(@RequestBody @Valid TypeTvaRequete requete) {
        var cree = types.creer(session.centre(requete.centerId()), requete.saisie(), requete.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(TypeTvaReponse.depuis(cree));
    }

    /**
     * 404 (rien d'écrit) si le type est inconnu dans le centre de la requête (KB-FAC-02 corrigé).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TypeTvaReponse> modifier(@PathVariable UUID id, @RequestBody @Valid TypeTvaRequete requete) {
        return types.modifier(session.centre(requete.centerId()), new RegleTvaId(id), requete.saisie())
                .map(TypeTvaReponse::depuis).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Désactivation logique, idempotente : 204 même pour un type inconnu du centre (KB-FAC-06, reproduit).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id, @RequestParam UUID centerId) {
        types.desactiver(session.centre(centerId), new RegleTvaId(id));
        return ResponseEntity.noContent().build();
    }
}

