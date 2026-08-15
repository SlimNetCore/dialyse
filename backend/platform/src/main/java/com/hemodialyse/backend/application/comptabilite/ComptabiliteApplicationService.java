package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.*;
import com.hemodialyse.backend.domain.comptabilite.service.ComptabiliteService;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service comptabilité — wraps le domaine pur avec @Transactional et Spring.
 * Isolation multi-centre garantie par centerId dans chaque commande.
 */
@Service
@Transactional
public class ComptabiliteApplicationService implements ComptabiliteUseCase {

    private final ComptabiliteService delegate;

    public ComptabiliteApplicationService(EcritureComptableRepositoryPort ecritureRepository,
                                          MappingComptablePort mappingPort,
                                          ParametrageFiscalPort fiscalPort,
                                          PeriodeComptableRepositoryPort periodePort,
                                          ExportComptablePort exportPort) {
        this.delegate = new ComptabiliteService(ecritureRepository, mappingPort, fiscalPort, periodePort, exportPort);
    }

    @Override
    public EcritureComptable genererEcritureFacturation(GenererEcritureFacturationCommand cmd) {
        return delegate.genererEcritureFacturation(cmd);
    }

    @Override
    public EcritureComptable genererEcritureReglement(GenererEcritureReglementCommand cmd) {
        return delegate.genererEcritureReglement(cmd);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<EcritureComptable> search(SearchEcrituresQuery query) {
        return delegate.search(query);
    }

    @Override
    public EcritureComptable valider(ValiderEcritureCommand cmd) {
        return delegate.valider(cmd);
    }

    @Override
    public byte[] exporter(ExporterJournalQuery query) {
        return delegate.exporter(query);
    }

    @Override
    public void cloturerPeriode(CloturerPeriodeCommand cmd) {
        delegate.cloturerPeriode(cmd);
    }

    @Override
    @Transactional(readOnly = true)
    public MappingComptable getMappingComptable(UUID centerId) {
        return delegate.getMappingComptable(centerId);
    }

    @Override
    public MappingComptable saveMappingComptable(MappingComptable mapping) {
        return delegate.saveMappingComptable(mapping);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegleTVA> getReglesTV(UUID centerId) {
        return delegate.getReglesTV(centerId);
    }

    @Override
    public void saveRegleTVA(UUID centerId, RegleTVA regle) {
        delegate.saveRegleTVA(centerId, regle);
    }
}

