package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;

/**
 * Ce que l'administrateur doit régulariser : le nombre de séances « créées » oubliées de la fenêtre et la date de la plus ancienne.
 * {@code plusAncienne} est nulle quand il n'y a rien à régulariser, et peut l'être quand la seule ligne comptée est illisible
 * (KB-SEA-07 : l'historique l'ignore, la plateforme l'a comptée ; le legacy envoie alors une date vide, reproduit).
 */
public record SeancesARegulariser(long total, LocalDate plusAncienne) {

    public SeancesARegulariser {
        if (total < 0) throw new SaisieInvalide("Le nombre de seances a regulariser ne peut pas etre negatif");
        if (total == 0 && plusAncienne != null) throw new SaisieInvalide("Aucune seance a regulariser : pas de plus ancienne");
    }

    public static SeancesARegulariser aucune() {
        return new SeancesARegulariser(0, null);
    }

    public boolean estVide() {
        return total == 0;
    }
}
