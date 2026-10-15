package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.AffecterUnCompteAuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.ListerLesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP du compte client de chaque payeur : {@code /api/v1/comptabilite/payeurs}, ADMIN seul, derrière la
 * chaîne de filtres commune. Le centre de chaque requête est confronté à celui de la session (403 sinon).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptesPayeursControleur {

    private final SessionCourante session;
    private final ListerLesPayeurs lister;
    private final AffecterUnCompteAuPayeur affecter;

    public ComptesPayeursControleur(SessionCourante session, ListerLesPayeurs lister, AffecterUnCompteAuPayeur affecter) {
        this.session = session;
        this.lister = lister;
        this.affecter = affecter;
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/payeurs")
    public ResponseEntity<PageReponse<PayeurReponse>> payeurs(@RequestParam(required = false) UUID centerId,
                                                              @RequestParam(required = false) String recherche,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(PageReponse.depuis(lister.executer(centre, recherche, page, size), PayeurReponse::depuis));
    }

    /**
     * Affecte un compte client au payeur ; un compte vide lui rend le compte client par défaut du centre.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/payeurs/{payeurId}/compte")
    public ResponseEntity<PayeurReponse> definirComptePayeur(@PathVariable UUID payeurId,
                                                             @RequestParam(required = false) UUID centerId,
                                                             @RequestBody @Valid ComptePayeurRequete requete) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(PayeurReponse.depuis(affecter.executer(centre, new PayeurId(payeurId), requete.compte())));
    }
}
