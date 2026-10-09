package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.service.ComptabiliteStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comptabilisation du stock — enveloppe transactionnelle du domaine pur. Une synchronisation est une transaction par
 * centre : elle aboutit entièrement ou pas du tout, et se rejoue sans doublon.
 */
@Service
public class ComptabiliteStockApplicationService implements ComptabiliteStockUseCase {

    private final ComptabiliteStockService delegate;
    private final OperationsStockPort stock;

    public ComptabiliteStockApplicationService(EcritureComptableRepositoryPort ecritures, MappingComptablePort mappings,
                                               PeriodeComptableRepositoryPort periodes, OperationsStockPort stock) {
        this.delegate = new ComptabiliteStockService(ecritures, mappings, periodes, stock);
        this.stock = stock;
    }

    @Override
    @Transactional
    public Synchronisation synchroniser(UUID centerId, LocalDate du, LocalDate au, LocalDate aujourdhui) {
        return delegate.synchroniser(centerId, du, au, aujourdhui);
    }

    /**
     * Centres à traiter par la tâche de nuit.
     */
    @Transactional(readOnly = true)
    public List<UUID> centres() {
        return stock.centres();
    }
}
