package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.*;
import com.hemodialyse.backend.domain.comptabilite.service.ComptabiliteService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service comptabilité — wraps le domaine pur avec @Transactional et Spring.
 * Isolation multi-centre garantie par centerId dans chaque commande. Le mapping et les règles de TVA sont tenus par le
 * contexte {@code contexts/comptabilite} (slice C2) ; ce service les lit par ses ports (ACL de la plateforme).
 */
@Service
@Transactional
public class ComptabiliteApplicationService implements ComptabiliteUseCase {

    private final ComptabiliteService delegate;

    public ComptabiliteApplicationService(EcritureComptableRepositoryPort ecritureRepository,
                                          MappingComptablePort mappingPort,
                                          PeriodeComptableRepositoryPort periodePort,
                                          ExportComptablePort exportPort,
                                          ComptePayeurRepositoryPort comptesPayeurs) {
        this.delegate = new ComptabiliteService(ecritureRepository, mappingPort, periodePort, exportPort, comptesPayeurs);
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
}
