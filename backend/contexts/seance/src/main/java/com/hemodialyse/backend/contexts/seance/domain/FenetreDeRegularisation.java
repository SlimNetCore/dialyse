package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * Jours examinés par le rappel de régularisation (RG-SEA-046) : les sept jours PASSÉS du centre, la veille comprise, le jour du centre
 * exclu (la séance du jour n'est pas « oubliée »). Même fenêtre que la détection des absences.
 * <p>
 * KB-SEA-37, REPRODUIT (risque en tête de l'ADR 0091) : le verrou des jours passés n'a pas de borne, le rappel si : une séance oubliée
 * depuis plus de sept jours reste bloquée pour l'infirmier mais n'est plus jamais rappelée à l'administrateur.
 */
public record FenetreDeRegularisation(LocalDate du, LocalDate au) {

    /** Jours passés examinés. */
    public static final int JOURS = 7;

    public FenetreDeRegularisation {
        if (du == null || au == null) throw new SaisieInvalide("La fenetre de regularisation doit avoir deux bornes");
        if (du.isAfter(au)) throw new SaisieInvalide("La date de debut doit preceder la date de fin");
    }

    /** La fenêtre vue du jour du centre : de J-7 à J-1 inclus. */
    public static FenetreDeRegularisation avant(LocalDate aujourdhui) {
        return new FenetreDeRegularisation(aujourdhui.minusDays(JOURS), aujourdhui.minusDays(1));
    }

    public boolean contient(LocalDate jour) {
        return !jour.isBefore(du) && !jour.isAfter(au);
    }

    /** Séances « créées » de la fenêtre, pas encore déverrouillées par l'administrateur, la plus ancienne d'abord. */
    public RechercheDeSeances rechercheDesOubliees() {
        return new RechercheDeSeances(du, au, Set.of(StatutDeSeance.CREE), null, RechercheDeSeances.OrdreDeTri.DATE, false, false);
    }
}
