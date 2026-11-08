package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.FenetreDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.SeancesARegulariser;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappel quotidien (07:00) à l'administrateur de chaque centre des séances « créées » restées sans validation sur les sept derniers
 * jours du centre (RG-SEA-046). Lecture seule : la régularisation passe par {@code DeverrouillerUneSeance} et {@code ValiderUneSeance}.
 * <p>
 * « Aujourd'hui » est le jour du FUSEAU DU CENTRE ({@link HorlogeDuCentre}, KB-SEA-36 corrigé : le legacy prenait le jour UTC). Une
 * seule émission par centre et par jour du centre, sous verrou consultatif (KB-SEA-35 corrigé). Un centre en échec n'arrête pas les
 * autres : l'incident est rendu dans le résultat, jamais propagé. Le cas d'usage ne lit aucune heure lui-même : le jour est fourni par
 * l'horloge, ou explicitement par {@link #executerPourLeCentre}.
 */
public class RappelerLesSeancesARegulariser {

    private final CentresARappeler centres;
    private final HorlogeDuCentre horloge;
    private final HistoriqueDesSeances historique;
    private final RappelsDeRegularisation rappels;
    private final VerrousDesRappels verrous;
    private final UniteDeTravail uniteDeTravail;

    public RappelerLesSeancesARegulariser(CentresARappeler centres, HorlogeDuCentre horloge, HistoriqueDesSeances historique,
                                          RappelsDeRegularisation rappels, VerrousDesRappels verrous, UniteDeTravail uniteDeTravail) {
        this.centres = centres;
        this.horloge = horloge;
        this.historique = historique;
        this.rappels = rappels;
        this.verrous = verrous;
        this.uniteDeTravail = uniteDeTravail;
    }

    /**
     * @param seances nombre de séances à régulariser vues par ce passage
     * @param rappele vrai si ce passage a émis le rappel (faux : rien à régulariser, rappel déjà émis ce jour, ou incident)
     * @param incident cause de l'arrêt du centre, nulle sinon
     */
    public record ResultatDuCentre(CentreId centre, LocalDate jour, long seances, boolean rappele, String incident) {
    }

    /** Tous les centres, chacun à son jour du centre. */
    public List<ResultatDuCentre> executer() {
        List<ResultatDuCentre> resultats = new ArrayList<>();
        for (CentreId centre : centres.tous()) {
            try {
                resultats.add(rappelerLeCentre(centre, horloge.aujourdhui(centre)));
            } catch (RuntimeException e) {
                resultats.add(incident(centre, null, e));
            }
        }
        return resultats;
    }

    /** Un centre pour le jour du centre donné (le travail planifié passe par {@link #executer()}). */
    public ResultatDuCentre executerPourLeCentre(CentreId centre, LocalDate jour) {
        try {
            return rappelerLeCentre(centre, jour);
        } catch (RuntimeException e) {
            return incident(centre, jour, e);
        }
    }

    private ResultatDuCentre rappelerLeCentre(CentreId centre, LocalDate jour) {
        SeancesARegulariser aRegulariser = aRegulariser(centre, jour);
        var rappel = RappelDeRegularisation.si(centre, jour, aRegulariser);
        if (rappel.isEmpty()) return new ResultatDuCentre(centre, jour, 0, false, null);
        boolean emis = uniteDeTravail.executer(() -> {
            verrous.verrouiller(centre, jour);
            return rappels.rappeler(rappel.get());
        });
        return new ResultatDuCentre(centre, jour, aRegulariser.total(), emis, null);
    }

    private SeancesARegulariser aRegulariser(CentreId centre, LocalDate jour) {
        var page = historique.rechercher(centre, FenetreDeRegularisation.avant(jour).rechercheDesOubliees(), new Pagination(0, 1));
        LocalDate plusAncienne = page.items().isEmpty() ? null : page.items().getFirst().jour();
        return page.total() == 0 ? SeancesARegulariser.aucune() : new SeancesARegulariser(page.total(), plusAncienne);
    }

    private static ResultatDuCentre incident(CentreId centre, LocalDate jour, RuntimeException e) {
        return new ResultatDuCentre(centre, jour, 0, false, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage()));
    }
}
