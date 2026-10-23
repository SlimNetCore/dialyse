package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.YearMonth;
import java.util.ArrayList;
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

    /**
     * Chaque mois de {@code premier} à {@code dernier} inclus, <b>verrouillés du plus ancien au plus récent</b> jusqu'à la fin de la
     * transaction (lignes manquantes créées ouvertes), dans l'ordre croissant. Même garantie que {@link #verrouiller} appelé mois par
     * mois ; un adaptateur peut le faire en un nombre de requêtes indépendant de la longueur de l'intervalle (revue F7, constat 3).
     */
    default List<MoisDeFacturation> verrouillerDe(CentreId centre, YearMonth premier, YearMonth dernier) {
        if (premier == null || dernier == null || dernier.isBefore(premier)) {
            throw new IllegalArgumentException("L'intervalle de mois est invalide");
        }
        List<MoisDeFacturation> verrouilles = new ArrayList<>();
        for (YearMonth courant = premier; !courant.isAfter(dernier); courant = courant.plusMonths(1)) {
            verrouilles.add(verrouiller(centre, courant));
        }
        return verrouilles;
    }

    /** Les mois enregistrés de l'année (ceux qui n'ont pas de ligne sont ouverts et absents). */
    List<MoisDeFacturation> deLAnnee(CentreId centre, int annee);

    /** Enregistre l'état du mois et ajoute l'entrée au journal, ensemble. */
    void enregistrer(MoisDeFacturation mois, EntreeDuJournal entree);

    /** Le journal du mois, du plus ancien au plus récent. */
    List<EntreeDuJournal> journal(CentreId centre, YearMonth mois);
}
