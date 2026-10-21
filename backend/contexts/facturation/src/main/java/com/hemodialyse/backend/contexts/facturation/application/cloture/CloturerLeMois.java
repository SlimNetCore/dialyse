package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Cas d'usage « clôturer un mois de facturation » (RG-FAC-040) : aucune précondition, l'administrateur décide. La réponse porte
 * l'avertissement chiffré : le nombre de séances encore éligibles à la facturation (validées ou signées, non facturées, non exclues)
 * dans le mois. Le mois est verrouillé avant d'être compté : une émission concurrente est terminée avant (ou refusée après),
 * l'avertissement est donc exact. Deux clôtures du même mois : la seconde est refusée {@code PERIODE_DEJA_CLOTUREE}.
 */
public final class CloturerLeMois {

    private final MoisDeFacturationRepository mois;
    private final SeancesFacturables seances;
    private final PublicationDesPeriodes publication;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public CloturerLeMois(MoisDeFacturationRepository mois, SeancesFacturables seances, PublicationDesPeriodes publication,
                          UniteDeTravail uniteDeTravail, Clock horloge) {
        this.mois = mois;
        this.seances = seances;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    /**
     * @param auteur l'utilisateur de la session (obligatoire)
     */
    public ResultatDeCloture executer(CentreId centre, YearMonth moisACloturer, UUID auteur) {
        return uniteDeTravail.executer(() -> {
            MoisDeFacturation courant = mois.verrouiller(centre, moisACloturer);
            int restantes = seances.dansLaPeriode(centre, PeriodeDeFacturation.deMois(moisACloturer)).size();
            EntreeDuJournal entree = courant.cloturer(horloge.instant(), auteur, restantes);
            mois.enregistrer(courant, entree);
            publication.cloturee(new PeriodeDeFacturationCloturee(centre.valeur(), moisACloturer, auteur, entree.le(), restantes));
            return new ResultatDeCloture(moisACloturer, entree.le(), restantes);
        });
    }

    /**
     * @param seancesEligiblesRestantes l'avertissement : séances du mois qui restent à facturer
     */
    public record ResultatDeCloture(YearMonth mois, Instant le, int seancesEligiblesRestantes) {
    }
}
