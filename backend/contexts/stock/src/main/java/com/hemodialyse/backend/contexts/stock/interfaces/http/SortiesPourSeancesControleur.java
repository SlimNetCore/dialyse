package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.sortieseances.GestionDesSortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesPourSeancesJson.CentreRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesPourSeancesJson.MotifRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesPourSeancesJson.PieceReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.SortiesPourSeancesJson.PieceRequete;
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
 * Adapter entrant HTTP des sorties pour séances : {@code /api/v1/stock/sorties-pour-seances} (slice S6b, ADR 0045). Enregistrement
 * réservé à {@code ADMIN} et {@code PHARMACIEN} ; justification d'un écart ouverte aussi à {@code INFIRMIER} ; préparation et
 * validation du rapprochement pour {@code ADMIN}, {@code PHARMACIEN} et {@code DIRECTEUR_ADMINISTRATIF} (T42), qui lit aussi le
 * stock mais ne fait aucune autre écriture. Centre de la session, un autre centre demandé est refusé (403).
 */
@RestController
@RequestMapping("/api/v1/stock/sorties-pour-seances")
public class SortiesPourSeancesControleur {

    private final SessionCourante session;
    private final GestionDesSortiesPourSeances sorties;

    public SortiesPourSeancesControleur(SessionCourante session, GestionDesSortiesPourSeances sorties) {
        this.session = session;
        this.sorties = sorties;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping
    public PieceReponse enregistrer(@RequestBody PieceRequete requete) {
        return PieceReponse.depuis(sorties.enregistrer(session.centre(requete.centerId()), requete.jour(), requete.perimetre(),
                session.utilisateur(), requete.demandes()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping
    public List<PieceReponse> lister(@RequestParam(required = false) UUID centerId) {
        return sorties.lister(session.centre(centerId)).stream().map(PieceReponse::depuis).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/{id}")
    public PieceReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return PieceReponse.depuis(sorties.consulter(session.centre(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','DIRECTEUR_ADMINISTRATIF')")
    @PostMapping("/{id}/rapprochement")
    public PieceReponse preparerLeRapprochement(@PathVariable UUID id, @RequestBody(required = false) CentreRequete requete) {
        return PieceReponse.depuis(sorties.preparerLeRapprochement(session.centre(requete == null ? null : requete.centerId()), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @PutMapping("/{id}/lignes/{ligneId}/motif")
    public PieceReponse justifierUnEcart(@PathVariable UUID id, @PathVariable UUID ligneId, @RequestBody MotifRequete requete) {
        return PieceReponse.depuis(sorties.justifierUnEcart(session.centre(requete.centerId()), id, ligneId, requete.motif()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','DIRECTEUR_ADMINISTRATIF')")
    @PostMapping("/{id}/valider")
    public PieceReponse valider(@PathVariable UUID id, @RequestBody(required = false) CentreRequete requete) {
        return PieceReponse.depuis(sorties.valider(session.centre(requete == null ? null : requete.centerId()), id,
                session.utilisateur()));
    }
}

