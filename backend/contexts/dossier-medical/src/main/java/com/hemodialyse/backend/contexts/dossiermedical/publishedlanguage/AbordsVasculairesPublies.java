package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : les patients d'un centre porteurs d'un abord vasculaire de type cathéter
 * ({@code KT_TUNNELISE}, {@code KT_AIGU}) ACTIF et non terminé au jour donné (fin absente ou postérieure ou égale au jour), pour la
 * compétence {@code CATHETER} exigée des infirmiers par la Planification (RG-PLN-097). Reproduit : l'abord est lu par SON centre, pas celui
 * de la fiche (KB-MED-108). Lecture seule.
 */
public interface AbordsVasculairesPublies {

    Set<UUID> porteursDUnCatheter(UUID centre, LocalDate auJour);
}
