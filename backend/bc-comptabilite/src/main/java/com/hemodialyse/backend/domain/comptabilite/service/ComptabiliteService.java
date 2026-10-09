package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.*;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
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
    private final JournalRepositoryPort journalPort;
    private final ComptePayeurRepositoryPort comptesPayeurs;
    private final PlanComptableUseCase plan;
    private final GenerateurEcritureComptable generateur;

    public ComptabiliteService(EcritureComptableRepositoryPort ecritureRepository,
                               MappingComptablePort mappingPort,
                               ParametrageFiscalPort fiscalPort,
                               PeriodeComptableRepositoryPort periodePort,
                               ExportComptablePort exportPort,
                               JournalRepositoryPort journalPort,
                               ComptePayeurRepositoryPort comptesPayeurs,
                               PlanComptableUseCase plan) {
        this.ecritureRepository = ecritureRepository;
        this.mappingPort = mappingPort;
        this.fiscalPort = fiscalPort;
        this.periodePort = periodePort;
        this.exportPort = exportPort;
        this.journalPort = journalPort;
        this.comptesPayeurs = comptesPayeurs;
        this.plan = plan;
        this.generateur = new GenerateurEcritureComptable();
    }

    /**
     * Compte client d'une facture : celui de son payeur s'il en a un, sinon le compte client par défaut du centre ;
     * une facture sans payeur (patient qui paie lui-même) relève du compte client des patients.
     */
    private String compteClient(UUID centerId, UUID payeurId, MappingComptable mapping) {
        if (payeurId == null) return mapping.compteClientPatient();
        return comptesPayeurs.find(centerId, payeurId).orElse(mapping.compteClientDefaut());
    }

    @Override
    public EcritureComptable genererEcritureFacturation(GenererEcritureFacturationCommand cmd) {
        // Idempotence : si l'écriture existe déjà pour cette facture, ne pas recréer
        var existing = ecritureRepository.findBySourceId(cmd.factureId(), cmd.centerId());
        if (existing.isPresent()) return existing.get();

        // Vérification période non clôturée
        YearMonth periode = YearMonth.from(cmd.dateFacture());
        if (periodePort.isClotured(cmd.centerId(), periode)) {
            throw new BusinessException("PERIODE_CLOTUREE",
                    "La période " + periode + " est clôturée pour le centre " + cmd.centerId());
        }

        MappingComptable mapping = mappingPort.findByCenterId(cmd.centerId());
        String numeroPiece = ecritureRepository.nextNumeroPiece(cmd.centerId(),
                mapping.journalDe(OperationComptable.VENTE), cmd.dateFacture().getYear());

        EcritureComptable ecriture = generateur.genererEcritureFacturation(cmd, mapping,
                compteClient(cmd.centerId(), cmd.tiersPayeurId(), mapping), numeroPiece);
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    @Override
    public EcritureComptable genererEcritureReglement(GenererEcritureReglementCommand cmd) {
        // Idempotence : si l'écriture existe déjà pour ce paiement
        var existing = ecritureRepository.findBySourceId(cmd.paiementId(), cmd.centerId());
        if (existing.isPresent()) return existing.get();

        YearMonth periode = YearMonth.from(cmd.dateReglement());
        if (periodePort.isClotured(cmd.centerId(), periode)) {
            throw new BusinessException("PERIODE_CLOTUREE",
                    "La période " + periode + " est clôturée pour le centre " + cmd.centerId());
        }

        MappingComptable mapping = mappingPort.findByCenterId(cmd.centerId());
        var journalCible = mapping.journalDe("CAISSE".equalsIgnoreCase(cmd.modeReglement())
                ? OperationComptable.REGLEMENT_CAISSE : OperationComptable.REGLEMENT_BANQUE);
        String numeroPiece = ecritureRepository.nextNumeroPiece(cmd.centerId(), journalCible, cmd.dateReglement().getYear());

        // le règlement solde le compte que la facture a débité, même si le paramétrage a changé entre-temps
        String compteClient = (cmd.factureId() == null ? Optional.<EcritureComptable>empty()
                : ecritureRepository.findBySourceId(cmd.factureId(), cmd.centerId()))
                .flatMap(facture -> facture.getLignes().stream()
                        .filter(l -> l.getMontantDebit().signum() > 0).map(LigneEcriture::getCompteSCF).findFirst())
                .orElseGet(() -> compteClient(cmd.centerId(), cmd.tiersPayeurId(), mapping));

        EcritureComptable ecriture = generateur.genererEcritureReglement(cmd, mapping, compteClient, numeroPiece);
        ecritureRepository.save(ecriture);
        return ecriture;
    }

    @Override
    public PagedResult<EcritureComptable> search(SearchEcrituresQuery query) {
        return ecritureRepository.findByCenterAndPeriod(
                query.centerId(), query.from(), query.to(),
                query.journalCode(), query.statut(), query.page(), query.size());
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
        // chaque opération s'écrit dans un journal que le centre a défini et laissé actif
        List<Journal> connus = journalPort.findByCenter(mapping.centerId());
        List<Journal> journaux = connus.isEmpty() ? Journal.parDefaut() : connus;
        mapping.journaux().forEach((operation, code) -> {
            boolean actif = journaux.stream().anyMatch(j -> j.code().equals(code) && j.actif());
            if (!actif) {
                throw new BusinessException("JOURNAL_INCONNU",
                        "Le journal " + code + " choisi pour " + operation + " n'existe pas ou est désactivé");
            }
        });
        // et sur des comptes du plan comptable du centre
        plan.exigerActifs(mapping.centerId(), mapping.comptes());
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



