package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.EnregistrerUneRegleDeTva;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.ListerLesReglesDeTva;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP des règles de TVA comptables : {@code /api/v1/comptabilite/regles-tva}, administrateur seul. La table
 * {@code tva_types} est partagée avec la Facturation, dont la liste des types de TVA est mise en cache six heures : un ajout
 * réussi évince ce cache ({@link #CACHE_TYPES_DE_TVA}, même nom que celui de la Facturation) de façon synchrone, au retour de
 * la méthode, donc après le commit du cas d'usage et avant la réponse (KB-CPT-20 corrigé).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ReglesTvaControleur {

    /**
     * Cache de la liste des types de TVA de la Facturation ({@code TypeTvaApplicationService.listerTypesTva}).
     */
    static final String CACHE_TYPES_DE_TVA = "tva-types-list";

    private final SessionCourante session;
    private final ListerLesReglesDeTva lister;
    private final EnregistrerUneRegleDeTva enregistrer;

    public ReglesTvaControleur(SessionCourante session, ListerLesReglesDeTva lister, EnregistrerUneRegleDeTva enregistrer) {
        this.session = session;
        this.lister = lister;
        this.enregistrer = enregistrer;
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/regles-tva")
    public ResponseEntity<List<RegleTvaReponse>> regles(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(lister.executer(session.centre(centerId)).stream().map(RegleTvaReponse::depuis).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/regles-tva")
    @CacheEvict(cacheNames = CACHE_TYPES_DE_TVA, allEntries = true)
    public ResponseEntity<Map<String, Boolean>> enregistrerUneRegle(@RequestParam(required = false) UUID centerId,
                                                                    @RequestBody @Valid RegleTvaRequete requete) {
        enregistrer.executer(session.centre(centerId), requete.commande());
        return ResponseEntity.ok(Map.of("saved", true));
    }
}
