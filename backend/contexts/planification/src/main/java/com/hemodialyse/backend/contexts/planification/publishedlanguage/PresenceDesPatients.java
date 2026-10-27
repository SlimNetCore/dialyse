package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>la présence des patients d'un centre</b>, calculée une seule fois par la planification (RG-PLN-045,
 * KB-PLN-71) pour les patients attendus d'Absences et la file active de la Direction. Un patient en sommeil n'est ni attendu ni
 * compté ; une place libérée ne compte plus ; un patient transféré ou guéri n'est plus attendu le jour de son départ. Bornée au
 * centre ; aucun cache.
 */
public interface PresenceDesPatients {

    /**
     * Les patients dont la fiche prévoit une séance ce jour-là (jour de la semaine coché) et qui sont présents : admis, ni en sommeil,
     * ni partis. Ne juge ni la fermeture du centre ni les séances déjà réalisées. Triés par identifiant.
     */
    List<UUID> attendusLe(UUID centre, LocalDate jour);

    /** Le nombre de patients présents au moins un jour de la période (bornes incluses), hors sommeil : la file active (RG-PAT-032). */
    long fileActive(UUID centre, LocalDate du, LocalDate au);
}
