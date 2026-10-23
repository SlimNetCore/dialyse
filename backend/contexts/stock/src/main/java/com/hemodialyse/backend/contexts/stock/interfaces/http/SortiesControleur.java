package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesJson.BonReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesJson.BonRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesJson.LotDisponibleReponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des bons de sortie manuels : {@code /api/v1/stock/bons-sortie}, mêmes routes, rôles ({@code ADMIN},
 * {@code PHARMACIEN}, {@code INFIRMIER}) et JSON que le legacy ({@code BonSortieRestController}, slice S6). Centre de la session,
 * un autre centre demandé est refusé (403) ; l'auteur est l'utilisateur de la session.
 */
@RestController
@RequestMapping("/api/v1/stock/bons-sortie")
public class SortiesControleur {

    private final SessionCourante session;
    private final GestionDesSorties sorties;

    public SortiesControleur(SessionCourante session, GestionDesSorties sorties) {
        this.session = session;
        this.sorties = sorties;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PostMapping
    public BonReponse sortir(@RequestBody BonRequete requete) {
        return BonReponse.depuis(sorties.sortir(session.centre(requete.centerId()), requete.seanceId(), requete.patientId(),
                requete.poste(), requete.dateSortie(), session.utilisateur(), requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PutMapping("/{id}")
    public BonReponse corriger(@PathVariable UUID id, @RequestBody BonRequete requete) {
        return BonReponse.depuis(sorties.corriger(session.centre(requete.centerId()), id, requete.seanceId(), requete.patientId(),
                requete.poste(), requete.dateSortie(), session.utilisateur(), requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/lots-disponibles")
    public List<LotDisponibleReponse> lotsDisponibles(@RequestParam(required = false) UUID centerId, @RequestParam UUID articleId) {
        return sorties.lotsDisponibles(session.centre(centerId), articleId).stream().map(LotDisponibleReponse::depuis).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/{id}")
    public BonReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return BonReponse.depuis(sorties.consulter(session.centre(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping
    public List<BonReponse> lister(@RequestParam(required = false) UUID centerId) {
        return sorties.lister(session.centre(centerId)).stream().map(BonReponse::depuis).toList();
    }
}

