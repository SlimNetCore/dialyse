package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeMensuelle;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Charge de travail mensuelle des infirmiers : séances du roulement (jours ouverts, hors absences), remplacements
 * effectués et jours d'absence. Sert à répartir équitablement les remplacements.
 */
public final class ChargeInfirmierService {

    private ChargeInfirmierService() {
    }

    public static ChargeMensuelle chargeMensuelle(DonneesPresence donnees, YearMonth mois) {
        CalendrierCentre calendrier = CalendrierCentre.de(donnees.planning());
        List<ChargeInfirmier> lignes = donnees.infirmiers().stream()
                .map(i -> charge(donnees, calendrier, i, mois))
                .sorted(Comparator.comparing(ChargeInfirmier::nom))
                .toList();
        double moyenne = lignes.stream().mapToInt(ChargeInfirmier::total).average().orElse(0);
        return new ChargeMensuelle(lignes, Math.round(moyenne * 10) / 10.0);
    }

    private static ChargeInfirmier charge(DonneesPresence donnees, CalendrierCentre calendrier, InfirmierRef infirmier,
                                          YearMonth mois) {
        List<AffectationInfirmier> roulement = donnees.affectations().stream()
                .filter(a -> a.infirmierId().equals(infirmier.id())).toList();
        int seances = 0;
        int joursAbsence = 0;
        for (LocalDate date = mois.atDay(1); !date.isAfter(mois.atEndOfMonth()); date = date.plusDays(1)) {
            boolean absent = PresenceInfirmierService.absenceLe(donnees, infirmier.id(), date).isPresent();
            if (absent) joursAbsence++;
            if (absent || calendrier.ferme(date)) continue;
            JourSemaine jour = JourSemaine.de(date.getDayOfWeek());
            seances += (int) roulement.stream().filter(a -> a.jours().contains(jour)).count();
        }
        int remplacements = (int) donnees.remplacements().stream()
                .filter(r -> r.infirmierId().equals(infirmier.id()) && mois.equals(YearMonth.from(r.date())))
                .map(RemplacementInfirmier::id).count();
        return new ChargeInfirmier(infirmier.id(), infirmier.nom(), seances, remplacements, joursAbsence,
                seances + remplacements);
    }
}
