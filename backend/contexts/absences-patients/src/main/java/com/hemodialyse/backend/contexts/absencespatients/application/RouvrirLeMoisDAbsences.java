package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert;

import java.time.YearMonth;

/**
 * Cas d'usage « rouvrir un mois d'absences clos » (A18, ADR 0042) : motif obligatoire, auteur tracé, journalisé. Un mois non clos
 * ne se rouvre pas ({@code MOIS_D_ABSENCES_NON_CLOS}, avant le contrôle du motif). Aucun lien avec la Facturation : la réouverture
 * n'est pas refusée quand le mois de facturation est clos (l'ordre de l'ADR 0009 reste une recommandation).
 */
public class RouvrirLeMoisDAbsences {

    private final MoisAbsencesRepository mois;
    private final PublicationDesMoisDAbsences publication;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDesCentres horloge;

    public RouvrirLeMoisDAbsences(MoisAbsencesRepository mois, PublicationDesMoisDAbsences publication,
                                  UniteDeTravail uniteDeTravail, HorlogeDesCentres horloge) {
        this.mois = mois;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public void executer(CentreId centre, YearMonth moisARouvrir, UtilisateurId auteur, String motif) {
        uniteDeTravail.executer(() -> {
            MoisAbsences courant = mois.verrouiller(centre, moisARouvrir);
            EntreeDuJournalDuMois entree = courant.rouvrir(horloge.maintenant(), auteur, motif);
            mois.enregistrer(courant, entree);
            publication.rouvert(new MoisDAbsencesRouvert(centre.valeur(), moisARouvrir, auteur.valeur(), entree.le(), entree.motif()));
            return null;
        });
    }
}
