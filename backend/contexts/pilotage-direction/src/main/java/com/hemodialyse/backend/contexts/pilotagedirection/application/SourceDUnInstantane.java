package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.SourceDuRapport;

import java.util.UUID;

/** Port sortant : le contenu figé d'un mois, prêt à être mis en rapport (lit l'instantané mensuel, PD2). */
public interface SourceDUnInstantane {

    /** @throws com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee instantané illisible ou vide (422) */
    SourceDUnMois source(UUID societeId, String mois);

    record SourceDUnMois(String mois, SourceDuRapport source) {
    }
}
