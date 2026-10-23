package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReceptionsJson.BonReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReceptionsJson.BonRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReceptionsJson.CentreRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReceptionsJson.DepuisBonDeCommandeRequete;
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
 * Adapter entrant HTTP des bons de réception : {@code /api/v1/stock/bons-reception}, mêmes routes, rôles (écriture
 * {@code ADMIN}/{@code PHARMACIEN}, lecture avec {@code INFIRMIER}) et JSON que le legacy ({@code BonReceptionRestController},
 * slice S5). Centre de la session, un autre centre demandé est refusé (403, KB-STK-34) ; l'auteur est l'utilisateur de la session.
 */
@RestController
@RequestMapping("/api/v1/stock/bons-reception")
public class ReceptionsControleur {

    private final SessionCourante session;
    private final GestionDesReceptions receptions;

    public ReceptionsControleur(SessionCourante session, GestionDesReceptions receptions) {
        this.session = session;
        this.receptions = receptions;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping
    public BonReponse creer(@RequestBody BonRequete requete) {
        return BonReponse.depuis(receptions.creer(session.centre(requete.centerId()), requete.bonCommandeId(),
                requete.fournisseurId(), requete.dateReception(), session.utilisateur(), requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/from-bon-commande")
    public BonReponse depuisUnBonDeCommande(@RequestBody DepuisBonDeCommandeRequete requete) {
        return BonReponse.depuis(receptions.depuisUnBonDeCommande(session.centre(requete.centerId()), requete.bonCommandeId(),
                session.utilisateur()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/{id}")
    public BonReponse modifier(@PathVariable UUID id, @RequestBody BonRequete requete) {
        return BonReponse.depuis(receptions.modifier(session.centre(requete.centerId()), id, requete.fournisseurId(),
                requete.dateReception(), requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/{id}/valider")
    public BonReponse valider(@PathVariable UUID id, @RequestBody(required = false) CentreRequete requete) {
        return BonReponse.depuis(receptions.valider(session.centre(requete == null ? null : requete.centerId()), id,
                session.utilisateur()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/{id}")
    public BonReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return BonReponse.depuis(receptions.consulter(session.centre(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping
    public List<BonReponse> lister(@RequestParam(required = false) UUID centerId) {
        return receptions.lister(session.centre(centerId)).stream().map(BonReponse::depuis).toList();
    }
}

