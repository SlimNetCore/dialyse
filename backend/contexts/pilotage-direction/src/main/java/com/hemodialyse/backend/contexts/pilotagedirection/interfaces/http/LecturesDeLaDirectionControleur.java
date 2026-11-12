package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaCapaciteDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaCapaciteDesCentres.CapaciteOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaGmaoDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaGmaoDesCentres.GmaoOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLeStockDesGroupes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLeStockDesGroupes.StockGroupesOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesAbsencesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesAbsencesDesCentres.AbsencesOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP des lectures de la direction fournies par d'autres contextes (PL10b2 et PL10c, ADR 0152) : {@code /gmao},
 * {@code /stock-groupes}, {@code /capacite} et {@code /absences}. Mêmes URL, mêmes rôles (DIRECTION) et mêmes codes que le legacy. Mêmes
 * décisions que le tableau de bord (ADR 0151) : la société vient de la session, revérifiée à chaque appel AVANT la lecture des paramètres
 * (403 avant 400, KB-PL10b-1), {@code Accept} incompatible en 406 et chemin voisin en 404 (KB-PL10b-3), consultation journalisée
 * (KB-PL10b-2), dates ISO strictes (paramètre répété = 400, vide = période par défaut), fin par défaut au jour d'Alger (KB-PL10b-4).
 */
@RestController
@RequestMapping(value = "/api/v1/direction", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('DIRECTION')")
public class LecturesDeLaDirectionControleur {

    private final SessionDeLaDirection session;
    private final ConsulterLaGmaoDesCentres gmao;
    private final ConsulterLeStockDesGroupes stockGroupes;
    private final ConsulterLaCapaciteDesCentres capacite;
    private final ConsulterLesAbsencesDesCentres absences;

    public LecturesDeLaDirectionControleur(SessionDeLaDirection session, ConsulterLaGmaoDesCentres gmao,
                                           ConsulterLeStockDesGroupes stockGroupes, ConsulterLaCapaciteDesCentres capacite,
                                           ConsulterLesAbsencesDesCentres absences) {
        this.session = session;
        this.gmao = gmao;
        this.stockGroupes = stockGroupes;
        this.capacite = capacite;
        this.absences = absences;
    }

    /** Aide à la décision GMAO : coût de maintenance, état du parc d'équipements et temps d'indisponibilité, par centre et consolidés. */
    @GetMapping("/gmao")
    public ResponseEntity<GmaoOverview> gmao(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(gmao.execute(societe, from(requete), to(requete)));
    }

    /** Valorisation du stock des groupes d'articles (ex. « KIT CNAS ») : stock début/fin, entrées, sorties, par centre et consolidée. */
    @GetMapping("/stock-groupes")
    public ResponseEntity<StockGroupesOverview> stockGroupes(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(stockGroupes.execute(societe, from(requete), to(requete)));
    }

    /** Capacité théorique des centres (générateurs, postes actifs, séries) et taux d'occupation de la file active, par centre et consolidée. */
    @GetMapping("/capacite")
    public ResponseEntity<CapaciteOverview> capacite(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(capacite.execute(societe, from(requete), to(requete)));
    }

    /** Absences des patients : nombre, motifs, valorisation HT, taux d'absentéisme et part du CA HT, par centre et consolidées. */
    @GetMapping("/absences")
    public ResponseEntity<AbsencesOverview> absences(HttpServletRequest requete) {
        UUID societe = session.societe();
        return aucuneMiseEnCache(absences.execute(societe, from(requete), to(requete)));
    }

    private static java.time.LocalDate from(HttpServletRequest requete) {
        return TableauDeBordDeLaDirectionControleur.date(requete, "from");
    }

    private static java.time.LocalDate to(HttpServletRequest requete) {
        return TableauDeBordDeLaDirectionControleur.date(requete, "to");
    }

    private static <T> ResponseEntity<T> aucuneMiseEnCache(T corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
    }
}
