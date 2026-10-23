package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande;
import com.hemodialyse.backend.contexts.stock.interfaces.http.BonsDeCommandeJson.BonReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.BonsDeCommandeJson.CentreRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.BonsDeCommandeJson.CreationRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.BonsDeCommandeJson.RemplacementRequete;
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
 * Adapter entrant HTTP des bons de commande : {@code /api/v1/stock/bons-commande}, mêmes routes, rôles (écriture
 * {@code ADMIN}/{@code PHARMACIEN}, lecture avec {@code INFIRMIER}) et JSON que le legacy ({@code BonCommandeRestController},
 * slice S3). Centre de la session, un autre centre demandé est refusé (403, KB-STK-17) ; l'auteur est l'utilisateur de la
 * session.
 */
@RestController
@RequestMapping("/api/v1/stock/bons-commande")
public class BonsDeCommandeControleur {

    private final SessionCourante session;
    private final GestionDesBonsDeCommande bons;

    public BonsDeCommandeControleur(SessionCourante session, GestionDesBonsDeCommande bons) {
        this.session = session;
        this.bons = bons;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping
    public BonReponse creer(@RequestBody CreationRequete requete) {
        return BonReponse.depuis(bons.creer(session.centre(requete.centerId()), requete.fournisseurId(), session.utilisateur(),
                requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/{id}")
    public BonReponse remplacerLesLignes(@PathVariable UUID id, @RequestBody RemplacementRequete requete) {
        return BonReponse.depuis(bons.remplacerLesLignes(session.centre(requete.centerId()), id, requete.saisies()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/{id}/valider")
    public BonReponse valider(@PathVariable UUID id, @RequestBody(required = false) CentreRequete requete) {
        return BonReponse.depuis(bons.valider(session.centre(requete == null ? null : requete.centerId()), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/{id}")
    public BonReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return BonReponse.depuis(bons.consulter(session.centre(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping
    public List<BonReponse> lister(@RequestParam(required = false) UUID centerId) {
        return bons.lister(session.centre(centerId)).stream().map(BonReponse::depuis).toList();
    }
}

