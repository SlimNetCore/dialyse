package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.service.InventoryGuardedMovementRepository;
import com.hemodialyse.backend.infrastructure.persistence.adapter.StockMovementRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Inventaire de stock : tous les services qui écrivent des mouvements (réceptions, sorties, consommations de
 * séance…) reçoivent le port <b>gardé</b>, qui interdit tout mouvement pendant un inventaire et dans une période
 * clôturée. Seule la clôture d'inventaire utilise l'adaptateur brut.
 */
@Configuration
public class StockInventoryConfig {

    @Bean
    @Primary
    public StockMovementRepositoryPort guardedStockMovementRepository(StockMovementRepositoryAdapter adapter,
                                                                      InventaireRepositoryPort inventaires) {
        return new InventoryGuardedMovementRepository(adapter, inventaires);
    }
}

