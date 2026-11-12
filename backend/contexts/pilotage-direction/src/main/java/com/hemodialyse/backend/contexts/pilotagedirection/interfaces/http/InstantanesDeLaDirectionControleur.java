package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Info;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Instantane;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des instantanés mensuels de la direction (PL10a, ADR 0150) : mêmes URL, mêmes rôles (DIRECTION) et mêmes codes que le
 * legacy. La société vient de la session (jamais d'un paramètre), revérifiée à chaque appel. Un {@code Accept} qui n'admet pas le JSON est
 * refusé en 406 et un chemin voisin en 404 (KB-PL10a-1 corrigé) ; la liste et la lecture sont des consultations journalisées
 * (KB-PL10a-3 corrigé, action {@code DIRECTION_INSTANTANE_CONSULTATION}).
 */
@RestController
@RequestMapping(value = "/api/v1/direction/snapshots", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('DIRECTION')")
public class InstantanesDeLaDirectionControleur {

    private final SessionDeLaDirection session;
    private final InstantanesMensuelsDeLaDirection instantanes;

    public InstantanesDeLaDirectionControleur(SessionDeLaDirection session, InstantanesMensuelsDeLaDirection instantanes) {
        this.session = session;
        this.instantanes = instantanes;
    }

    /** Les mois figés de la société, du plus récent au plus ancien. */
    @GetMapping
    public ResponseEntity<List<Info>> lister() {
        UUID societe = session.societe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(instantanes.lister(societe));
    }

    /** L'instantané figé d'un mois. */
    @GetMapping("/{mois}")
    public ResponseEntity<Instantane> lire(@PathVariable String mois) {
        UUID societe = session.societe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(instantanes.lire(societe, mois));
    }

    /** Fige le mois s'il ne l'est pas encore (mois écoulé uniquement) ; idempotent. */
    @PostMapping("/{mois}")
    public ResponseEntity<Instantane> figer(@PathVariable String mois) {
        UUID societe = session.societe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(instantanes.figer(societe, mois));
    }
}
