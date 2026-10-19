package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.CloturerPeriodeCommand;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.ExporterJournalQuery;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.SearchEcrituresQuery;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.ValiderEcritureCommand;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ExportComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.List;

/**
 * Service de domaine legacy restant : consultation, validation, export et clôture dans l'ancien modèle. La génération des
 * écritures de ventes et de règlements est passée au contexte {@code comptabilite} (slice C5).
 */
public class ComptabiliteService {

    private final EcritureComptableRepositoryPort ecritureRepository;
    private final PeriodeComptableRepositoryPort periodePort;
    private final ExportComptablePort exportPort;

    public ComptabiliteService(EcritureComptableRepositoryPort ecritureRepository,
                               PeriodeComptableRepositoryPort periodePort,
                               ExportComptablePort exportPort) {
        this.ecritureRepository = ecritureRepository;
        this.periodePort = periodePort;
        this.exportPort = exportPort;
    }

    public PagedResult<EcritureComptable> search(SearchEcrituresQuery query) {
        return ecritureRepository.findByCenterAndPeriod(
                query.centerId(), query.from(), query.to(),
                query.journalCode(), query.statut(), query.page(), query.size());
    }

    public EcritureComptable valider(ValiderEcritureCommand cmd) {
        EcritureComptable ecriture = ecritureRepository.findById(cmd.ecritureId(), cmd.centerId())
                .orElseThrow(() -> new BusinessException("ECRITURE_INTROUVABLE", "Écriture introuvable"));
        ecriture.valider();
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    public byte[] exporter(ExporterJournalQuery query) {
        List<EcritureComptable> ecritures = ecritureRepository.findForExport(
                query.centerId(), query.from(), query.to(), query.journalCode());
        byte[] result = exportPort.exporter(ecritures);
        // Marquer les écritures validées comme exportées
        ecritures.stream()
                .filter(e -> e.getStatut() == StatutEcriture.VALIDEE)
                .forEach(e -> {
                    e.marquerExportee();
                    ecritureRepository.save(e);
                });
        return result;
    }

    public void cloturerPeriode(CloturerPeriodeCommand cmd) {
        if (periodePort.isClotured(cmd.centerId(), cmd.periode())) {
            throw new BusinessException("PERIODE_DEJA_CLOTUREE",
                    "La période " + cmd.periode() + " est déjà clôturée");
        }
        periodePort.cloturer(cmd.centerId(), cmd.periode(), cmd.userId());
    }
}
