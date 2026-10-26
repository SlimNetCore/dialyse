package com.hemodialyse.backend.contexts.stock.application.peremptions;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.AlerteDeStock;
import com.hemodialyse.backend.contexts.stock.domain.peremptions.RegleDesPeremptions;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions.LotSignale;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contrôle quotidien des péremptions (RG-STK-061) : pour chaque centre <b>actif</b>, les lots en stock dont la péremption tombe dans
 * les 30 jours (borne incluse) ou est passée (signalés le lendemain puis chaque semaine, {@link RegleDesPeremptions}), d'articles
 * <b>actifs</b>, sont publiés ({@link LotProcheExpiration}, Outbox) puis notifiés au pharmacien et à l'administrateur du centre en
 * une seule alerte par centre et par jour du centre. « Aujourd'hui » est le jour du fuseau de chaque centre. Un centre en échec
 * n'arrête pas les autres : l'incident est rendu dans le résultat.
 */
public class ControlerLesPeremptions {

    private final CentresActifs centres;
    private final HorlogeDesCentres horloge;
    private final LecturesDuStock lectures;
    private final Articles articles;
    private final UniteDeTravail uniteDeTravail;
    private final EvenementsDePeremption evenements;
    private final NotificationsDesPeremptions notifications;

    public ControlerLesPeremptions(CentresActifs centres, HorlogeDesCentres horloge, LecturesDuStock lectures, Articles articles,
                                   UniteDeTravail uniteDeTravail, EvenementsDePeremption evenements,
                                   NotificationsDesPeremptions notifications) {
        this.centres = centres;
        this.horloge = horloge;
        this.lectures = lectures;
        this.articles = articles;
        this.uniteDeTravail = uniteDeTravail;
        this.evenements = evenements;
        this.notifications = notifications;
    }

    /**
     * @param lots     nombre de lots signalés
     * @param notifie  vrai si l'alerte du jour a été émise par ce passage
     * @param incident cause de l'arrêt du centre, nulle sinon
     */
    public record ResultatDuCentre(CentreId centre, LocalDate jour, int lots, boolean notifie, String incident) {
    }

    public List<ResultatDuCentre> controler() {
        List<ResultatDuCentre> resultats = new ArrayList<>();
        for (CentreId centre : centres.liste()) {
            try {
                resultats.add(controler(centre));
            } catch (RuntimeException e) {
                resultats.add(new ResultatDuCentre(centre, null, 0, false,
                        e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage())));
            }
        }
        return resultats;
    }

    ResultatDuCentre controler(CentreId centre) {
        LocalDate jour = horloge.aujourdHui(centre);
        Map<UUID, Article> actifs = articles.tous(centre).stream().filter(Article::actif)
                .collect(Collectors.toMap(Article::id, Function.identity()));
        List<AlerteDeStock> lots = lectures.peremptions(centre, RegleDesPeremptions.horizon(jour)).stream()
                .filter(l -> actifs.containsKey(l.articleId()))
                .filter(l -> RegleDesPeremptions.aSignaler(l.datePeremption(), jour, l.recuLe()))
                .toList();
        if (lots.isEmpty()) {
            return new ResultatDuCentre(centre, jour, 0, false, null);
        }
        List<LotProcheExpiration> publies = lots.stream().map(l -> LotProcheExpiration.v1(centre.valeur(), jour, l.articleId(),
                actifs.get(l.articleId()).fiche().code(), l.lotId(), l.numeroLot(), l.datePeremption(), l.quantite(),
                RegleDesPeremptions.estPerime(l.datePeremption(), jour))).toList();
        uniteDeTravail.executer(() -> {
            publies.forEach(evenements::publier);
            return null;
        });
        List<LotSignale> signales = publies.stream().map(e -> new LotSignale(e.numeroLot(), e.codeArticle(),
                actifs.get(e.article()).fiche().libelle(), e.datePeremption(), e.quantite(), e.perime())).toList();
        boolean notifie = notifications.signaler(centre.valeur(), jour, signales);
        return new ResultatDuCentre(centre, jour, lots.size(), notifie, null);
    }
}
