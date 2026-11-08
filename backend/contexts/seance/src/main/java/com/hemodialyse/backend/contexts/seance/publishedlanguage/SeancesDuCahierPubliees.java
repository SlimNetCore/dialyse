package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0099, SE7e) : le nombre de séances d'un patient imprimables dans son cahier de
 * dialyse. Lecture seule. Le contenu du cahier reste lu par l'état Jasper du centre (modèle de document personnalisable, ADR 0099,
 * décision 3) : ce port ne sert qu'au refus {@code CAHIER_VIDE}.
 */
public interface SeancesDuCahierPubliees {

    /**
     * Les séances du patient DANS le centre, au statut {@code VALIDEE}, {@code SIGNEE} ou {@code FACTUREE} en casse EXACTE (KB-SEA-88 :
     * une séance {@code validee} n'est ni comptée ni imprimée), datées dans {@code [du, au]} bornes incluses (E10). Les bornes sont
     * obligatoires : l'appelant fournit ses valeurs par défaut.
     */
    int nombreDeSeancesAImprimer(UUID centre, UUID patient, LocalDate du, LocalDate au);
}
