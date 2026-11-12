package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : les instantanés mensuels figés d'une société (table {@code direction_snapshot}). Un instantané n'est jamais modifié. */
public interface InstantanesMensuels {

    /** Les mois figés de la société, du plus récent au plus ancien (le contenu n'est pas lu : un contenu illisible est listé). */
    List<InstantaneListe> lister(UUID societeId);

    Optional<InstantaneFige> trouver(UUID societeId, MoisDInstantane mois);

    /** @return {@code false} si l'instantané existe déjà (créé entre-temps par une autre demande) : il reste inchangé */
    boolean enregistrer(InstantaneMensuel instantane);

    /**
     * PL10a (KB-DIR-06 d, décision du 2026-11-12) : l'instant de capture est celui de l'horloge du Pilotage, injectée (et non l'heure de la
     * base). Par défaut, repli sur {@link #enregistrer(InstantaneMensuel)} pour les doubles qui ne portent pas l'instant.
     */
    default boolean enregistrer(InstantaneMensuel instantane, OffsetDateTime genereLe) {
        return enregistrer(instantane);
    }

    record InstantaneListe(String mois, OffsetDateTime genereLe) {
    }

    /** {@code contenu} : le texte figé tel que stocké (non interprété ici). */
    record InstantaneFige(String mois, OffsetDateTime genereLe, String contenu) {
    }
}
