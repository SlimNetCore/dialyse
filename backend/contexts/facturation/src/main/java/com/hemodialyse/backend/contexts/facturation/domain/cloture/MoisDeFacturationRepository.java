package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.YearMonth;
import java.util.List;

/**
 * Port sortant : les mois de facturation d'un centre ; un mois sans ligne est ouvert.
 */
public interface MoisDeFacturationRepository {

    /** Le mois, ouvert s'il n'a pas de ligne, sans verrou (lecture). */
    MoisDeFacturation duMois(CentreId centre, YearMonth mois);

    /**
     * Le mois en <b>verrouillant sa ligne jusqu'à la fin de la transaction</b> : la clôture et toute émission ou modification du
     * mois se sérialisent. La ligne est créée ouverte si elle n'existe pas encore, afin qu'il y ait toujours quelque chose à verrouiller.
     */
    MoisDeFacturation verrouiller(CentreId centre, YearMonth mois);

    /** Les mois enregistrés de l'année (ceux qui n'ont pas de ligne sont ouverts et absents). */
    List<MoisDeFacturation> deLAnnee(CentreId centre, int annee);

    /** Enregistre l'état du mois et ajoute l'entrée au journal, ensemble. */
    void enregistrer(MoisDeFacturation mois, EntreeDuJournal entree);

    /** Le journal du mois, du plus ancien au plus récent. */
    List<EntreeDuJournal> journal(CentreId centre, YearMonth mois);
}
