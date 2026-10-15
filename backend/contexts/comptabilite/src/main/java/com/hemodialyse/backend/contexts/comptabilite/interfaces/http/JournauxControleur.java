package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.EnregistrerUnJournal;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.ListerLesJournaux;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.SupprimerUnJournal;
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
 * Adapter entrant HTTP des journaux comptables : {@code /api/v1/comptabilite/journaux}, derrière la chaîne de filtres commune.
 * Le centre de chaque requête est confronté à celui de la session (403 sinon). Contrat verrouillé par la suite de
 * caractérisation, sauf l'écart décidé KB-CPT-01 ({@code actif} manquant : 400).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class JournauxControleur {

    private final SessionCourante session;
    private final ListerLesJournaux lister;
    private final EnregistrerUnJournal enregistrer;
    private final SupprimerUnJournal supprimer;

    public JournauxControleur(SessionCourante session, ListerLesJournaux lister, EnregistrerUnJournal enregistrer,
                              SupprimerUnJournal supprimer) {
        this.session = session;
        this.lister = lister;
        this.enregistrer = enregistrer;
        this.supprimer = supprimer;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/journaux")
    public ResponseEntity<PageReponse<JournalReponse>> journaux(@RequestParam(required = false) UUID centerId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(PageReponse.depuis(lister.executer(centre, page, size), JournalReponse::depuis));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/journaux/{code}")
    public ResponseEntity<JournalReponse> enregistrerJournal(@PathVariable String code,
                                                             @RequestParam(required = false) UUID centerId,
                                                             @RequestBody @Valid JournalRequete requete) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(JournalReponse.depuis(
                enregistrer.executer(centre, code, requete.libelle(), requete.actifObligatoire())));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/journaux/{code}")
    public ResponseEntity<Void> supprimerJournal(@PathVariable String code, @RequestParam(required = false) UUID centerId) {
        supprimer.executer(session.centre(centerId), code);
        return ResponseEntity.noContent().build();
    }
}
