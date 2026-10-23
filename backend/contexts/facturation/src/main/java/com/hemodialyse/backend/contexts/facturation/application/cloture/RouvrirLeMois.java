package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisNonClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.ReouvertureRefuseeParLaComptabilite;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Cas d'usage « rouvrir un mois de facturation clos » (RG-FAC-043, RG-FAC-044) : motif obligatoire, auteur tracé, journalisé.
 * Ordre des refus : un mois non clos ne se rouvre pas ({@code PERIODE_NON_CLOTUREE}) ; puis le motif est vérifié
 * ({@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE}) ; enfin la réouverture est refusée ({@code PERIODE_COMPTABLE_CLOTUREE}) si la période
 * comptable du même mois et du même centre est déjà clôturée (revue F7 du 2026-10-23, constat 5).
 */
public final class RouvrirLeMois {

    private final MoisDeFacturationRepository mois;
    private final ClotureComptable comptabilite;
    private final PublicationDesPeriodes publication;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public RouvrirLeMois(MoisDeFacturationRepository mois, ClotureComptable comptabilite, PublicationDesPeriodes publication,
                         UniteDeTravail uniteDeTravail, Clock horloge) {
        this.mois = mois;
        this.comptabilite = comptabilite;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public void executer(CentreId centre, YearMonth moisARouvrir, UUID auteur, String motif) {
        uniteDeTravail.executer(() -> {
            MoisDeFacturation courant = mois.verrouiller(centre, moisARouvrir);
            if (!courant.estClos()) {
                throw new MoisNonClos(moisARouvrir);
            }
            MoisDeFacturation.motifDeReouvertureValide(motif);
            if (comptabilite.estCloturee(centre, moisARouvrir)) {
                throw new ReouvertureRefuseeParLaComptabilite(moisARouvrir);
            }
            EntreeDuJournal entree = courant.rouvrir(horloge.instant(), auteur, motif);
            mois.enregistrer(courant, entree);
            publication.rouverte(new PeriodeDeFacturationRouverte(centre.valeur(), moisARouvrir, auteur, entree.le(), entree.motif()));
            return null;
        });
    }
}
