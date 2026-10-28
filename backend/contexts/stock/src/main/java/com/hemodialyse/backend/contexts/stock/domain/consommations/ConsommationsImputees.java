package com.hemodialyse.backend.contexts.stock.domain.consommations;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les consommations de séance d'un centre (tables {@code consommations_de_seance} et
 * {@code consommations_de_seance_imputations}).
 */
public interface ConsommationsImputees {
    Optional<ConsommationDeSeance> parSeance(CentreId centre, UUID seance);

    /**
     * Enregistre la consommation (en-tête et imputations remplacées) dans la transaction de l'appelant.
     */
    void enregistrer(ConsommationDeSeance consommation);

    void supprimer(CentreId centre, UUID seance);

    /**
     * Total imputé par lot de la sortie par les consommations des autres séances.
     */
    Map<UUID, BigDecimal> totauxParLotSaufSeance(CentreId centre, UUID sortie, UUID seance);
}