package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;

import java.time.Clock;
import java.time.Duration;

/**
 * Politique de reprise (P20, B1 de la revue P8) : chaque instance du serveur signe ses calculs en cours (battement de cœur) ; un
 * calcul dont le battement a plus de {@link #DELAI_D_ORPHELIN} a été abandonné avec son processus : il échoue avec un motif
 * lisible, pour ne pas bloquer le centre (« une seule en cours »). Les calculs d'une AUTRE instance vivante ne sont jamais touchés ;
 * une exécution qui n'est plus en cours en base ne change plus (transition conditionnelle).
 */
public class ReprendreLesExecutionsInterrompues {

    public static final String MOTIF = "Calcul interrompu par l'arrêt du serveur";
    /**
     * Un battement toutes les 10 s (job) : 60 s sans signe de vie = instance disparue.
     */
    public static final Duration DELAI_D_ORPHELIN = Duration.ofSeconds(60);

    private final UniteDeTravail uniteDeTravail;
    private final ExecutionsRepository executions;
    private final Clock horloge;

    public ReprendreLesExecutionsInterrompues(UniteDeTravail uniteDeTravail, ExecutionsRepository executions, Clock horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.executions = executions;
        this.horloge = horloge;
    }

    /**
     * Signe de vie de cette instance pour ses calculs en cours.
     */
    public void battre() {
        uniteDeTravail.executer(() -> {
            executions.battre(horloge.instant());
            return null;
        });
    }

    /** @return le nombre d'exécutions orphelines interrompues */
    public int executer() {
        return uniteDeTravail.executer(() -> {
            executions.battre(horloge.instant());
            return executions.interrompreLesOrphelines(MOTIF, horloge.instant(), DELAI_D_ORPHELIN);
        });
    }
}
