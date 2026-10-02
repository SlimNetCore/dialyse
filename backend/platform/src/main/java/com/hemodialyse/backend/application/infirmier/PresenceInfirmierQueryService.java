package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Candidat;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeMensuelle;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.infirmier.service.ChargeInfirmierService;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.infirmier.service.RemplacantService;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Lecture du planning de présence des infirmiers : semaine, alertes de sous-effectif, remplaçants possibles et charge
 * mensuelle. Lecture seule, bornée au centre, non mise en cache (absences et remplacements changent en cours de journée).
 */
@Service
public class PresenceInfirmierQueryService {

    /**
     * Horizon maximal des alertes (jours).
     */
    public static final int HORIZON_MAX_JOURS = 60;
    /**
     * Nombre maximal de remplaçants proposés.
     */
    public static final int LIMITE_REMPLACANTS = 10;

    private final PresenceDonneesPort donnees;

    public PresenceInfirmierQueryService(PresenceDonneesPort donnees) {
        this.donnees = donnees;
    }

    /**
     * @param date un jour de la semaine voulue (semaine du dimanche au samedi)
     */
    public SemainePresence semaine(UUID centerId, LocalDate date) {
        LocalDate debut = PlanningSemaineService.debutSemaine(date);
        return PresenceInfirmierService.construire(donnees.charger(centerId, debut, debut.plusDays(6)), debut);
    }

    /**
     * Cases en sous-effectif des {@code jours} prochains jours à partir de {@code aujourdhui}.
     */
    public List<AlertePresence> alertes(UUID centerId, LocalDate aujourdhui, int jours) {
        if (jours < 1 || jours > HORIZON_MAX_JOURS) {
            throw new IllegalArgumentException("L'horizon des alertes doit être compris entre 1 et " + HORIZON_MAX_JOURS + " jours");
        }
        LocalDate au = aujourdhui.plusDays(jours - 1L);
        LocalDate debut = PlanningSemaineService.debutSemaine(aujourdhui);
        DonneesPresence d = donnees.charger(centerId, debut, PlanningSemaineService.debutSemaine(au).plusDays(6));
        return PresenceInfirmierService.alertes(d, aujourdhui, au);
    }

    public List<Candidat> remplacants(UUID centerId, LocalDate date, UUID salleId, UUID creneauId) {
        return RemplacantService.proposer(donneesSemaine(centerId, date), date, salleId, creneauId, LIMITE_REMPLACANTS);
    }

    /**
     * Charge mensuelle paginée (tri par nom) ; la moyenne porte sur tous les infirmiers du centre.
     */
    public ChargePage charge(UUID centerId, YearMonth mois, int page, int size) {
        DonneesPresence d = donnees.charger(centerId, mois.atDay(1), mois.atEndOfMonth());
        ChargeMensuelle charge = ChargeInfirmierService.chargeMensuelle(d, mois);
        List<ChargeInfirmier> tous = charge.infirmiers();
        int de = (int) Math.min((long) page * size, tous.size());
        int a = Math.min(de + size, tous.size());
        return new ChargePage(PagedResult.of(tous.subList(de, a), tous.size(), page, size), charge.moyenne());
    }

    DonneesPresence donneesSemaine(UUID centerId, LocalDate date) {
        LocalDate debut = PlanningSemaineService.debutSemaine(date);
        return donnees.charger(centerId, debut, debut.plusDays(6));
    }

    public record ChargePage(PagedResult<ChargeInfirmier> page, double moyenne) {
    }
}
