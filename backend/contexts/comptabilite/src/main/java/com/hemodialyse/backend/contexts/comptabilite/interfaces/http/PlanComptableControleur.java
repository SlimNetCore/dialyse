package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.EnregistrerUnCompte;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.ListerLesComptes;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.SupprimerUnCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP du plan comptable : {@code /api/v1/comptabilite/comptes}, derrière la chaîne de filtres commune. Le
 * centre de chaque requête est confronté à celui de la session (403 sinon, RG-CPT-016). Contrat verrouillé par la suite de
 * caractérisation, sauf l'écart décidé KB-CPT-01 ({@code actif} manquant : 400).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class PlanComptableControleur {

    private final SessionCourante session;
    private final ListerLesComptes lister;
    private final EnregistrerUnCompte enregistrer;
    private final SupprimerUnCompte supprimer;

    public PlanComptableControleur(SessionCourante session, ListerLesComptes lister, EnregistrerUnCompte enregistrer,
                                   SupprimerUnCompte supprimer) {
        this.session = session;
        this.lister = lister;
        this.enregistrer = enregistrer;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/comptes")
    public ResponseEntity<PageReponse<CompteReponse>> comptes(@RequestParam(required = false) UUID centerId,
                                                              @RequestParam(required = false) String recherche,
                                                              @RequestParam(defaultValue = "false") boolean actifs,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(PageReponse.depuis(lister.executer(centre, recherche, actifs, page, size), CompteReponse::depuis));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/comptes/{numero}")
    public ResponseEntity<CompteReponse> enregistrerCompte(@PathVariable String numero,
                                                           @RequestParam(required = false) UUID centerId,
                                                           @RequestBody @Valid CompteRequete requete) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(CompteReponse.depuis(
                enregistrer.executer(centre, numero, requete.libelle(), requete.actifObligatoire())));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/comptes/{numero}")
    public ResponseEntity<Void> supprimerCompte(@PathVariable String numero, @RequestParam(required = false) UUID centerId) {
        supprimer.executer(session.centre(centerId), numero);
        return ResponseEntity.noContent().build();
    }
}
