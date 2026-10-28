package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


import java.time.YearMonth;
import java.util.List;

/**
 * Port sortant du domaine : les mois d'absences d'un centre (tables {@code mois_absences} et {@code mois_absences_journal}) ; un
 * mois sans ligne est ouvert. Lit aussi comme {@link MoisDAbsencesOuverts}, le port des politiques de nuit : un seul adaptateur
 * pour l'état des mois.
 */
public interface MoisAbsencesRepository extends MoisDAbsencesOuverts {

    /**
     * Le mois, ouvert s'il n'a pas de ligne, sans verrou (lecture).
     */
    MoisAbsences duMois(CentreId centre, YearMonth mois);

    /**
     * Le mois en <b>verrouillant sa ligne jusqu'à la fin de la transaction</b> : la clôture, la réouverture et toute écriture
     * d'absence du mois se sérialisent. La ligne est créée ouverte si elle n'existe pas encore, afin qu'il y ait toujours
     * quelque chose à verrouiller.
     *
     * @throws EcritureConcurrente le verrou n'a pas pu être pris (délai dépassé)
     */
    MoisAbsences verrouiller(CentreId centre, YearMonth mois);

    /**
     * Les mois enregistrés de l'année (ceux qui n'ont pas de ligne sont ouverts et absents), du premier au dernier.
     */
    List<MoisAbsences> deLAnnee(CentreId centre, int annee);

    /**
     * Enregistre l'état du mois et ajoute l'entrée au journal, ensemble.
     */
    void enregistrer(MoisAbsences mois, EntreeDuJournalDuMois entree);

    /**
     * Le journal du mois, du plus ancien au plus récent.
     */
    List<EntreeDuJournalDuMois> journal(CentreId centre, YearMonth mois);
}
