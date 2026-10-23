package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.DelaiDeQualification;
import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesNonTermine;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Cas d'usage « clôturer un mois d'absences » (A18, ADR 0042) : aucune précondition d'état, l'administrateur décide. Seul un mois
 * terminé se clôt, au fuseau du centre et sans jours de grâce (jamais le mois en cours ni un mois futur : {@code
 * MOIS_D_ABSENCES_NON_TERMINE}). La réponse porte l'avertissement chiffré : absences encore « à qualifier » du mois et, parmi elles,
 * celles en retard. Le mois est verrouillé avant d'être compté : une écriture concurrente se termine avant (ou est refusée après),
 * l'avertissement est donc exact. Deux clôtures du même mois : la seconde est refusée {@code MOIS_D_ABSENCES_DEJA_CLOS}.
 */
public class CloturerLeMoisDAbsences {

    private final MoisAbsencesRepository mois;
    private final LectureDesAbsences lecture;
    private final PublicationDesMoisDAbsences publication;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDesCentres horloge;

    public CloturerLeMoisDAbsences(MoisAbsencesRepository mois, LectureDesAbsences lecture, PublicationDesMoisDAbsences publication,
                                   UniteDeTravail uniteDeTravail, HorlogeDesCentres horloge) {
        this.mois = mois;
        this.lecture = lecture;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    /**
     * @throws MoisDAbsencesNonTermine le mois est le mois en cours ou un mois futur au fuseau du centre
     */
    public ResultatDeCloture executer(CentreId centre, YearMonth moisACloturer, UtilisateurId auteur) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        if (!moisACloturer.isBefore(YearMonth.from(aujourdhui))) {
            throw new MoisDAbsencesNonTermine(moisACloturer);
        }
        return uniteDeTravail.executer(() -> {
            MoisAbsences courant = mois.verrouiller(centre, moisACloturer);
            LocalDate du = moisACloturer.atDay(1);
            LocalDate au = moisACloturer.atEndOfMonth();
            long aQualifier = lecture.nombreAQualifierEntre(centre, du, au);
            LocalDate dernierJourEnRetard = DelaiDeQualification.limite(aujourdhui).minusDays(1);
            LocalDate finDuRetard = au.isBefore(dernierJourEnRetard) ? au : dernierJourEnRetard;
            long enRetard = du.isAfter(finDuRetard) ? 0 : lecture.nombreAQualifierEntre(centre, du, finDuRetard);
            EntreeDuJournalDuMois entree = courant.cloturer(horloge.maintenant(), auteur, aQualifier, enRetard);
            mois.enregistrer(courant, entree);
            publication.cloture(new MoisDAbsencesCloture(centre.valeur(), moisACloturer, auteur.valeur(), entree.le(), aQualifier,
                    enRetard));
            return new ResultatDeCloture(moisACloturer, entree.le(), aQualifier, enRetard);
        });
    }

    /**
     * @param aQualifierRestantes l'avertissement : absences du mois encore « à qualifier »
     * @param enRetard            parmi elles, celles dont le délai de qualification est dépassé
     */
    public record ResultatDeCloture(YearMonth mois, Instant le, long aQualifierRestantes, long enRetard) {
    }
}
