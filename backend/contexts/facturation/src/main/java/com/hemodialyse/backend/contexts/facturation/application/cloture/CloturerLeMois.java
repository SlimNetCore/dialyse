package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationNonTermine;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Cas d'usage « clôturer un mois de facturation » (RG-FAC-040) : aucune précondition d'état, l'administrateur décide, mais seul un
 * <b>mois terminé</b> se clôture, au fuseau du centre et sans jours de grâce (jamais le mois en cours ni un mois futur :
 * {@code PERIODE_NON_TERMINEE}, ADR 0044, même règle que les absences). La réponse porte l'avertissement chiffré : le nombre de
 * séances encore éligibles à la facturation (validées ou signées, non facturées, non exclues) dans le mois. Le mois est verrouillé
 * avant d'être compté : une émission concurrente est terminée avant (ou refusée après), l'avertissement est donc exact. Deux
 * clôtures du même mois : la seconde est refusée {@code PERIODE_DEJA_CLOTUREE}.
 */
public final class CloturerLeMois {

    private final MoisDeFacturationRepository mois;
    private final SeancesFacturables seances;
    private final PublicationDesPeriodes publication;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;
    private final AbsencesDuMois absences;
    private final FuseauxDesCentres fuseaux;

    public CloturerLeMois(MoisDeFacturationRepository mois, SeancesFacturables seances, PublicationDesPeriodes publication,
                          UniteDeTravail uniteDeTravail, Clock horloge, AbsencesDuMois absences, FuseauxDesCentres fuseaux) {
        this.mois = mois;
        this.seances = seances;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.absences = absences;
        this.fuseaux = fuseaux;
    }

    /**
     * @param auteur l'utilisateur de la session (obligatoire)
     * @throws MoisDeFacturationNonTermine le mois est le mois en cours ou un mois futur au fuseau du centre (avant tout verrou)
     */
    public ResultatDeCloture executer(CentreId centre, YearMonth moisACloturer, UUID auteur) {
        LocalDate aujourdhui = LocalDate.now(horloge.withZone(fuseaux.de(centre)));
        MoisDeFacturation.exigerTermine(moisACloturer, aujourdhui);
        return uniteDeTravail.executer(() -> {
            MoisDeFacturation courant = mois.verrouiller(centre, moisACloturer);
            int restantes = seances.dansLaPeriode(centre, PeriodeDeFacturation.deMois(moisACloturer)).size();
            EntreeDuJournal entree = courant.cloturer(horloge.instant(), auteur, restantes);
            mois.enregistrer(courant, entree);
            publication.cloturee(new PeriodeDeFacturationCloturee(centre.valeur(), moisACloturer, auteur, entree.le(), restantes));
            // second avertissement, jamais bloquant (ADR 0042) : le mois d'absences est-il encore ouvert ?
            boolean absencesNonCloses = !absences.sontCloses(centre, moisACloturer);
            return new ResultatDeCloture(moisACloturer, entree.le(), restantes, absencesNonCloses);
        });
    }

    /**
     * @param seancesEligiblesRestantes l'avertissement : séances du mois qui restent à facturer
     * @param absencesDuMoisNonCloses   second avertissement : le mois d'absences du même centre n'est pas clos (ADR 0009 recommande de
     *                                  clore les absences d'abord ; rien n'est bloqué)
     */
    public record ResultatDeCloture(YearMonth mois, Instant le, int seancesEligiblesRestantes,
                                    boolean absencesDuMoisNonCloses) {
    }
}
