package com.hemodialyse.backend.contexts.planification.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La place qu'un patient tient encore sur sa fiche (RG-PLN-080 à 083, RG-PAT-032 à 034), avec le dernier jour où il a le droit de
 * l'occuper. Lorsque ce dernier jour est dépassé, la place est <b>libérée</b> : politique {@code LiberationDesPlaces}, événement
 * {@link PlaceLiberee}. Le calcul du dernier jour dépend de l'état du patient (modèle de Patient et couverture) : il est fait par
 * l'ACL, pas ici.
 *
 * @param etat               état du patient tel qu'enregistré (conservé tel quel dans le mouvement ; jamais interprété ici)
 * @param dernierJourDePlace dernier jour où la place est tenue ; nul : sans limite (jamais libérée) ; {@link LocalDate#MIN} : sortie
 *                           sans date, libérée dès la première nuit
 * @param place              la place tenue (une partie seulement est possible : donnée héritée)
 */
public record PlaceTenue(CentreId centre, PatientId patient, String etat, LocalDate dernierJourDePlace,
                         PlaceQuittee place) {

    public PlaceTenue {
        if (centre == null || patient == null || place == null) throw new SaisieInvalide("Place tenue incomplète");
    }

    /**
     * La place est-elle tenue et son dernier jour dépassé à ce jour ?
     */
    public boolean estEchueLe(LocalDate jour) {
        return place.estTenue() && dernierJourDePlace != null && jour.isAfter(dernierJourDePlace);
    }

    /**
     * Libère la place si son dernier jour est dépassé.
     *
     * @param echeanceJugeeLe jour auquel l'échéance est jugée (aujourd'hui du centre pour la nuit ; la veille à l'enregistrement d'une
     *                        fiche : le contrôle des absences de la veille voit encore les jours de dialyse)
     * @param dateDEffet      jour réel de la libération, daté du jour du centre dans les deux cas
     * @return vide si la place n'est pas échue (aucun effet)
     */
    public Optional<PlaceLiberee> liberer(LocalDate echeanceJugeeLe, LocalDate dateDEffet, Instant maintenant) {
        if (!estEchueLe(echeanceJugeeLe)) return Optional.empty();
        return Optional.of(new PlaceLiberee(centre, patient, etat, place, dateDEffet, true, maintenant));
    }

    /**
     * Ce qui est effacé de la fiche : salle, créneau, générateur et jours de dialyse. Au moins l'un des trois premiers existe, sinon
     * le patient n'a pas de place à libérer.
     */
    public record PlaceQuittee(SalleId salle, CreneauId creneau, GenerateurId generateur, Set<JourDeLaSemaine> jours) {
        public PlaceQuittee {
            jours = jours == null || jours.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(jours));
        }

        public boolean estTenue() {
            return salle != null || creneau != null || generateur != null;
        }

        /**
         * Les jours dans l'ordre de la semaine (dimanche d'abord), séparés par des virgules ; vide sans jour.
         */
        public String joursEnTexte() {
            return jours.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        }
    }

    /**
     * La place libérée : ce que la fiche a perdu, au jour réel de la libération (jour du centre), de façon automatique.
     *
     * @param dateDEffet jour du centre auquel la libération a eu lieu (jamais le jour jugé échu)
     */
    public record PlaceLiberee(CentreId centre, PatientId patient, String etat, PlaceQuittee placeQuittee,
                               LocalDate dateDEffet,
                               boolean automatique, Instant survenueLe) {
    }
}
