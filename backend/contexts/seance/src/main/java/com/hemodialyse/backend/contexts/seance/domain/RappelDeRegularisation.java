package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Le rappel quotidien à l'administrateur d'un centre : il n'existe que s'il y a des séances à régulariser, et il est UNIQUE par centre
 * et par jour du centre (KB-SEA-35, corrigé : le couple centre + jour est la clé qui évite le doublon et la double émission de deux
 * instances du serveur). Le jour est celui du fuseau du centre (KB-SEA-36, corrigé : le legacy prenait le jour UTC).
 */
public record RappelDeRegularisation(CentreId centre, LocalDate jour, SeancesARegulariser seances) {

    public RappelDeRegularisation {
        if (centre == null) throw new SaisieInvalide("Le centre du rappel est obligatoire");
        if (jour == null) throw new SaisieInvalide("Le jour du rappel est obligatoire");
        if (seances == null || seances.estVide()) throw new SaisieInvalide("Pas de rappel sans seance a regulariser");
    }

    /** Le rappel du centre pour ce jour, vide quand il n'y a rien à régulariser. */
    public static Optional<RappelDeRegularisation> si(CentreId centre, LocalDate jour, SeancesARegulariser seances) {
        return seances.estVide() ? Optional.empty() : Optional.of(new RappelDeRegularisation(centre, jour, seances));
    }
}
