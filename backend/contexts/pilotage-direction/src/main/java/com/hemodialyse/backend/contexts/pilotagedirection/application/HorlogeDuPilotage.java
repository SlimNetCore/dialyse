package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * L'heure du Pilotage. KB-DIR-03 corrigé (lot L6, ADR 0119) : « aujourd'hui » (fin de période par défaut, mois écoulé des instantanés,
 * cron du 1er du mois) est la date dans le fuseau de RÉFÉRENCE de l'application, {@value #FUSEAU_DE_REFERENCE} (celui que les autres
 * contextes appliquent à un centre sans fuseau), et non le fuseau de la JVM. L'horodatage de constat est en UTC. La {@link Clock} est
 * injectée : les tests la figent. Un fuseau par centre ou par société reste un résiduel nommé « fuseau par centre » : une société
 * peut porter plusieurs centres de fuseaux différents et {@code societes} n'a pas de fuseau.
 */
public final class HorlogeDuPilotage {

    /** Le fuseau de référence de l'application, repris des autres contextes (Planification, Absences, Stock, Séance). */
    public static final String FUSEAU_DE_REFERENCE = "Africa/Algiers";

    /**
     * La propriété qui le surcharge ({@code app.pilotage.fuseau-par-defaut}, puis {@code app.absences.fuseau-par-defaut}, puis Alger), au
     * format d'espace réservé de Spring : pour la valeur injectée de la composition et pour la zone de la tâche planifiée du 1er du mois, comme les autres tâches de nuit.
     */
    public static final String PROPRIETE_DU_FUSEAU = "${app.pilotage.fuseau-par-defaut:${app.absences.fuseau-par-defaut:" + FUSEAU_DE_REFERENCE + "}}";

    private final Clock horloge;

    public HorlogeDuPilotage(Clock horloge) {
        this.horloge = horloge;
    }

    /** L'horloge du Pilotage : les instants de {@code instants}, le jour dans le {@code fuseau} de référence. */
    public static HorlogeDuPilotage auFuseauDeReference(Clock instants, ZoneId fuseau) {
        return new HorlogeDuPilotage(instants.withZone(fuseau));
    }

    public LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    public OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge.withZone(ZoneOffset.UTC));
    }
}
