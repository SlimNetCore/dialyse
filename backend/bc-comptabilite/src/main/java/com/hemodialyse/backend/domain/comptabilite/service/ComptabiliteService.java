package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.*;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Service de domaine pur — implémente ComptabiliteUseCase sans aucune dépendance Spring/JPA.
 * Orchestration métier : résolution mapping + règle TVA + période clôturée + idempotence.
 */
public class ComptabiliteService implements ComptabiliteUseCase {

    private final EcritureComptableRepositoryPort ecritureRepository;
    private final MappingComptablePort mappingPort;
    private final ParametrageFiscalPort fiscalPort;
    private final PeriodeComptableRepositoryPort periodePort;
    private final ExportComptablePort exportPort;
    private final GenerateurEcritureComptable generateur;

    public ComptabiliteService(EcritureComptableRepositoryPort ecritureRepository,
                               MappingComptablePort mappingPort,
                               ParametrageFiscalPort fiscalPort,
                               PeriodeComptableRepositoryPort periodePort,
                               ExportComptablePort exportPort) {
        this.ecritureRepository = ecritureRepository;
        this.mappingPort = mappingPort;
        this.fiscalPort = fiscalPort;
        this.periodePort = periodePort;
        this.exportPort = exportPort;
        this.generateur = new GenerateurEcritureComptable();
    }

    @Override
    public EcritureComptable genererEcritureFacturation(GenererEcritureFacturationCommand cmd) {
        // Idempotence : si l'écriture existe déjà pour cette facture, ne pas recréer
        var existing = ecritureRepository.findBySourceId(cmd.factureId(), cmd.centerId(), JournalCode.VE);
        if (existing.isPresent()) return existing.get();

        // Vérification période non clôturée
        YearMonth periode = YearMonth.from(cmd.dateFacture());
        if (periodePort.isClotured(cmd.centerId(), periode)) {
            throw new BusinessException("PERIODE_CLOTUREE",
                    "La période " + periode + " est clôturée pour le centre " + cmd.centerId());
        }

        MappingComptable mapping = mappingPort.findByCenterId(cmd.centerId());
        var regleTVA = fiscalPort.findActiveAt(cmd.centerId(), "DIALYSE", cmd.dateFacture()).orElse(null);
        String numeroPiece = ecritureRepository.nextNumeroPiece(cmd.centerId(), JournalCode.VE, cmd.dateFacture().getYear());

        EcritureComptable ecriture = generateur.genererEcritureFacturation(cmd, mapping, numeroPiece, regleTVA);
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    @Override
    public EcritureComptable genererEcritureReglement(GenererEcritureReglementCommand cmd) {
        // Idempotence : si l'écriture existe déjà pour ce paiement
        var journalCible = "CAISSE".equalsIgnoreCase(cmd.modeReglement()) ? JournalCode.CA : JournalCode.BQ;
        var existing = ecritureRepository.findBySourceId(cmd.paiementId(), cmd.centerId(), journalCible);
        if (existing.isPresent()) return existing.get();

        YearMonth periode = YearMonth.from(cmd.dateReglement());
        if (periodePort.isClotured(cmd.centerId(), periode)) {
            throw new BusinessException("PERIODE_CLOTUREE",
                    "La période " + periode + " est clôturée pour le centre " + cmd.centerId());
        }

        MappingComptable mapping = mappingPort.findByCenterId(cmd.centerId());
        String numeroPiece = ecritureRepository.nextNumeroPiece(cmd.centerId(), journalCible, cmd.dateReglement().getYear());

        EcritureComptable ecriture = generateur.genererEcritureReglement(cmd, mapping, numeroPiece);
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    @Override
    public PagedResult<EcritureComptable> search(SearchEcrituresQuery query) {
        return ecritureRepository.findByCenterAndPeriod(
                query.centerId(), query.from(), query.to(), query.journalCode(), query.page(), query.size());
    }

    @Override
    public EcritureComptable valider(ValiderEcritureCommand cmd) {
        EcritureComptable ecriture = ecritureRepository.findById(cmd.ecritureId(), cmd.centerId())
                .orElseThrow(() -> new BusinessException("ECRITURE_INTROUVABLE", "Écriture introuvable"));
        ecriture.valider();
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    @Override
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

    @Override
    public void cloturerPeriode(CloturerPeriodeCommand cmd) {
        if (periodePort.isClotured(cmd.centerId(), cmd.periode())) {
            throw new BusinessException("PERIODE_DEJA_CLOTUREE",
                    "La période " + cmd.periode() + " est déjà clôturée");
        }
        periodePort.cloturer(cmd.centerId(), cmd.periode(), cmd.userId());
    }

    @Override
    public MappingComptable getMappingComptable(UUID centerId) {
        return mappingPort.findByCenterId(centerId);
    }

    @Override
    public MappingComptable saveMappingComptable(MappingComptable mapping) {
        mappingPort.save(mapping);
        return mapping;
    }

    @Override
    public List<RegleTVA> getReglesTV(UUID centerId) {
        return fiscalPort.findAll(centerId);
    }

    @Override
    public void saveRegleTVA(UUID centerId, RegleTVA regle) {
        fiscalPort.save(centerId, regle);
    }
}

