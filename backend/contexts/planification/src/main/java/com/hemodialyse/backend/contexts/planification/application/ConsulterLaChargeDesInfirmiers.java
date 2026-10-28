package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ChargeDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeMensuelle;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;

import java.time.YearMonth;
import java.util.List;

/**
 * Charge mensuelle paginée des infirmiers actifs (RG-INF-060), triée par nom puis prénom ; la moyenne porte sur tous les infirmiers du
 * centre. Sans mois, le mois courant du centre. Lecture seule.
 */
public class ConsulterLaChargeDesInfirmiers {
    private final LecturesDeLaPresence lectures;
    private final HorlogeDesCentres horloge;

    public ConsulterLaChargeDesInfirmiers(PersonnelDeLaPresence personnel, PlanningDesPlaces places, HorlogeDesCentres horloge) {
        this.lectures = new LecturesDeLaPresence(personnel, places);
        this.horloge = horloge;
    }

    public record ChargeDuMois(PageDeResultats<ChargeInfirmier> page, double moyenne) {
    }

    /** @param mois le mois voulu, ou {@code null} pour le mois courant du centre */
    public ChargeDuMois executer(CentreId centre, YearMonth mois, Pagination pagination) {
        YearMonth retenu = mois != null ? mois : YearMonth.from(horloge.aujourdhui(centre));
        ChargeMensuelle charge = ChargeDesInfirmiers.mensuelle(lectures.charger(centre, retenu.atDay(1), retenu.atEndOfMonth()), retenu);
        List<ChargeInfirmier> tous = charge.infirmiers();
        int de = (int) Math.min(pagination.decalage(), tous.size());
        int a = Math.min(de + pagination.taille(), tous.size());
        return new ChargeDuMois(new PageDeResultats<>(tous.subList(de, a), tous.size(), pagination.page(), pagination.taille()),
                charge.moyenne());
    }
}
