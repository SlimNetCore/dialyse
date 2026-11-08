package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les consommables relevés à la validation de la séance. Le Stock décide (imputation sur la sortie pour séances du centre
 * ou sortie en FEFO) ; la Séance ne touche ni lot ni mouvement. Un refus du Stock est une {@link ConsommationRefusee}.
 */
public interface ConsommationsALaValidation {

    /** L'article du catalogue du centre ; inconnu ou d'un autre centre : vide. */
    Optional<Article> article(CentreId centre, UUID article);

    /** Enregistre les lignes relevées sur la séance, au nom de {@code auteur}. */
    void enregistrer(CentreId centre, Seance seance, List<Ligne> lignes, String auteur);

    record Article(UUID id, String code, boolean actif) {
    }

    record Ligne(UUID article, BigDecimal quantite) {
    }
}
