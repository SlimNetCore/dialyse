package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Interface REQUISE du langage publié de la Planification (DM9, ADR 0101), fournie par la plateforme depuis le langage publié du Dossier
 * médical ({@code AbordsVasculairesPublies}) : les patients d'un centre porteurs d'un cathéter ({@code KT_TUNNELISE}, {@code KT_AIGU})
 * actif et non terminé au jour donné, pour la compétence {@code CATHETER} de RG-PLN-097. Lecture seule, bornée au centre.
 */
public interface AbordsVasculairesFournis {

    Set<UUID> porteursDUnCatheter(UUID centre, LocalDate auJour);
}
