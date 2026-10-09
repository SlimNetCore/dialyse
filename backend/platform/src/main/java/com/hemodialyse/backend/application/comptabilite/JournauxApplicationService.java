package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournauxUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.service.JournauxService;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Journaux comptables d'un centre — enveloppe transactionnelle du domaine pur.
 */
@Service
@Transactional
public class JournauxApplicationService implements JournauxUseCase {

    private final JournauxService delegate;

    public JournauxApplicationService(JournalRepositoryPort journaux, MappingComptablePort mappings,
                                      EcritureComptableRepositoryPort ecritures) {
        this.delegate = new JournauxService(journaux, mappings, ecritures);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Journal> lister(UUID centerId) {
        return delegate.lister(centerId);
    }

    @Override
    public Journal enregistrer(UUID centerId, Journal journal) {
        return delegate.enregistrer(centerId, journal);
    }

    @Override
    public void supprimer(UUID centerId, JournalCode code) {
        delegate.supprimer(centerId, code);
    }
}
