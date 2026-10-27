package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeMensuelle;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Charge mensuelle des infirmiers (RG-INF-060) : séances du roulement (jours ouverts, hors absences), remplacements effectués et jours
 * d'absence. Un remplacement posé un jour d'absence ou de fermeture du centre n'est pas compté (KB-PLN-49, décision du 27/10/2026).
 * Tri par nom de famille puis prénom ; la moyenne porte sur tous les infirmiers actifs, à une décimale.
 */
public final class ChargeDesInfirmiers {

    private static final Comparator<InfirmierDeLaPresence> PAR_NOM_PUIS_PRENOM = Comparator
            .comparing((InfirmierDeLaPresence i) -> i.nom() == null ? "" : i.nom(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(i -> i.prenom() == null ? "" : i.prenom(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(InfirmierDeLaPresence::id);

    private ChargeDesInfirmiers() {
    }

    public static ChargeMensuelle mensuelle(DonneesDePresence donnees, YearMonth mois) {
        OuvertureDuCentre ouverture = OuvertureDuCentre.de(donnees.planning());
        List<ChargeInfirmier> lignes = donnees.personnel().infirmiers().stream().sorted(PAR_NOM_PUIS_PRENOM)
                .map(i -> charge(donnees, ouverture, i, mois)).toList();
        double moyenne = lignes.stream().mapToInt(ChargeInfirmier::total).average().orElse(0);
        return new ChargeMensuelle(lignes, Math.round(moyenne * 10) / 10.0);
    }

    private static ChargeInfirmier charge(DonneesDePresence donnees, OuvertureDuCentre ouverture, InfirmierDeLaPresence infirmier,
                                          YearMonth mois) {
        List<AffectationPrevue> roulement = donnees.personnel().affectations().stream()
                .filter(a -> a.infirmier().equals(infirmier.id())).toList();
        int seances = 0;
        int joursAbsence = 0;
        for (LocalDate date = mois.atDay(1); !date.isAfter(mois.atEndOfMonth()); date = date.plusDays(1)) {
            boolean absent = PresenceDeLaSemaine.absenceLe(donnees, infirmier.id(), date).isPresent();
            if (absent) joursAbsence++;
            if (absent || ouverture.ferme(date)) continue;
            JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
            seances += (int) roulement.stream().filter(a -> a.jours().contains(jour)).count();
        }
        int remplacements = (int) donnees.personnel().remplacements().stream()
                .filter(r -> r.infirmier().equals(infirmier.id()) && mois.equals(YearMonth.from(r.date())))
                .filter(r -> !ouverture.ferme(r.date()) && PresenceDeLaSemaine.absenceLe(donnees, infirmier.id(), r.date()).isEmpty())
                .count();
        return new ChargeInfirmier(infirmier.id(), infirmier.nomAffiche(), seances, remplacements, joursAbsence, seances + remplacements);
    }
}
