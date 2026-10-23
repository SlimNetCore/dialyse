package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lectures de l'infirmier sur les sorties pour séances (slice S6b, ADR 0045) : articles favoris du périmètre, consommation d'une séance
 * avec ses alertes ; et paramétrage du centre (bascule de la sortie pour séances, réservée à l'administrateur). L'écriture des
 * consommations passe par le port publié appelé par la Séance, pas par HTTP.
 */
@RestController
@RequestMapping("/api/v1/stock")
public class ConsommationsControleur {

    private final SessionCourante session;
    private final GestionDesConsommations consommations;

    public ConsommationsControleur(SessionCourante session, GestionDesConsommations consommations) {
        this.session = session;
        this.consommations = consommations;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/sorties-pour-seances/favoris")
    public List<FavoriReponse> favoris(@RequestParam LocalDate jour, @RequestParam(required = false) String salle,
                                       @RequestParam(required = false) DemiJournee demiJournee,
                                       @RequestParam(required = false) UUID centerId) {
        return consommations.favoris(session.centre(centerId), jour, new PerimetreDeSortie(salle, demiJournee)).stream()
                .map(f -> new FavoriReponse(f.articleId(), f.quantiteSortie(), f.reste())).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/consommations-de-seance/{seanceId}/favoris")
    public List<FavoriReponse> favorisDeLaSeance(@PathVariable UUID seanceId, @RequestParam LocalDate jour,
                                                 @RequestParam(required = false) UUID centerId) {
        return consommations.favorisDeLaSeance(session.centre(centerId), seanceId, jour).stream()
                .map(f -> new FavoriReponse(f.articleId(), f.quantiteSortie(), f.reste())).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/consommations-de-seance/{seanceId}")
    public ConsommationReponse consommation(@PathVariable UUID seanceId, @RequestParam(required = false) UUID centerId) {
        return ConsommationReponse.depuis(consommations.consulter(session.centre(centerId), seanceId));
    }

    record FavoriReponse(UUID articleId, BigDecimal quantiteSortie, BigDecimal reste) {
    }

    record ImputationReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, String alerte) {
    }

    record ConsommationReponse(UUID seanceId, UUID patientId, UUID sortieId, List<ImputationReponse> imputations) {
        static ConsommationReponse depuis(ConsommationDeSeance c) {
            return new ConsommationReponse(c.seanceId(), c.patientId(), c.sortieId(), c.imputations().stream()
                    .map(i -> new ImputationReponse(i.id(), i.articleId(), i.lotId(), i.quantite(), i.alerte())).toList());
        }
    }


    record ParametresRequete(UUID centerId, boolean sortiePourSeancesActivee) {
    }
}

