package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du Dossier médical ({@code dossier-medical.v1}, RP3b, ADR 0172) : ce que l'import de l'historique d'un lot de reprise de
 * données écrit dans le dossier médical et les analyses (dossier de base, antécédents, sérologies, abords vasculaires, analyses : les mêmes
 * natures que {@link DonneesMedicalesDeLaReprise#NATURES_DU_LOT}, que l'annulation supprime). Réservé à l'adapter d'import de la reprise
 * (règle ArchUnit). N'ouvre aucune transaction : il participe à celle de l'import.
 * <p>
 * Une <em>nature</em> est celle de {@link DonneesMedicalesDeLaReprise} (liste fermée, aucune concaténation d'une valeur de l'appelant) ; un
 * <em>champ</em> est un nom de champ du fichier d'import (camelCase) accepté pour cette nature, toute autre nature ou tout autre champ lève
 * {@link IllegalArgumentException}. Les valeurs sont celles que l'outil de reprise a déjà contrôlées et converties (dates, décimaux,
 * booléens, identifiants).
 */
public interface RepriseDeLHistoriqueMedical {

    String DOSSIER_DE_BASE = "dossier_medical_patient";
    String ANTECEDENTS = "antecedents_medicaux";
    String SEROLOGIES = "serologies_patient";
    String ABORDS_VASCULAIRES = "abords_vasculaires";
    String ANALYSES = "resultats_analyses";

    /** Les natures que l'import peut écrire (égales à celles que l'annulation peut supprimer). */
    List<String> NATURES = List.of(DOSSIER_DE_BASE, ANTECEDENTS, SEROLOGIES, ABORDS_VASCULAIRES, ANALYSES);

    /**
     * La ligne de cette nature du centre qui porte la clé (champs de la clé ; une valeur absente se lit « est nulle »), la plus ancienne
     * d'abord ; vide si aucune.
     */
    Optional<UUID> dejaPresente(UUID centre, String nature, Map<String, Object> cle);

    /** Crée une ligne dans le centre avec ces valeurs (la date de création est posée, la date de modification quand la nature en a une). */
    UUID reprendre(UUID centre, String nature, Map<String, Object> valeurs);

    /** Met à jour la ligne du centre ; le patient d'une ligne ne change jamais ; aucune valeur à écrire ne fait rien. */
    void completer(UUID centre, String nature, UUID donnee, Map<String, Object> valeurs);
}
