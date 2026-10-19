package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementAComptabiliser;
import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.*;
import com.hemodialyse.backend.domain.comptabilite.service.ComptabiliteService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application service comptabilité legacy — wraps le domaine pur avec @Transactional et Spring.
 * <p>
 * <b>ACL transitoire (slice C5, retirée en C7)</b> : la génération des écritures de ventes et de règlements est faite par
 * le contexte {@code comptabilite} ({@link ComptabiliserLesVentesEtReglements}), chaque source dans sa propre
 * transaction ; ce service ne fait que traduire l'ancienne commande, traduire un refus du contexte en
 * {@link BusinessException} (même code, même message) et relire l'écriture dans l'ancien modèle. Il n'a plus d'appelant de
 * production : les écouteurs et {@code /rebuild} appellent le contexte directement.
 */
@Service
@Transactional
public class ComptabiliteApplicationService implements ComptabiliteUseCase {

    private final ComptabiliteService delegate;
    private final EcritureComptableRepositoryPort ecritureRepository;
    private final ComptabiliserLesVentesEtReglements comptabilisation;

    public ComptabiliteApplicationService(EcritureComptableRepositoryPort ecritureRepository,
                                          PeriodeComptableRepositoryPort periodePort,
                                          ExportComptablePort exportPort,
                                          ComptabiliserLesVentesEtReglements comptabilisation) {
        this.delegate = new ComptabiliteService(ecritureRepository, periodePort, exportPort);
        this.ecritureRepository = ecritureRepository;
        this.comptabilisation = comptabilisation;
    }

    private static <T> T traduireLesRefus(Supplier<T> action) {
        try {
            return action.get();
        } catch (RegleMetierViolee refus) {
            throw new BusinessException(refus.code(), refus.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EcritureComptable genererEcritureFacturation(GenererEcritureFacturationCommand cmd) {
        UUID id = traduireLesRefus(() -> comptabilisation.comptabiliserUneVente(new CentreId(cmd.centerId()),
                new FactureAComptabiliser(cmd.factureId(), cmd.numeroFacture(), cmd.tiersPayeurId(), cmd.totalHt(),
                        cmd.totalTva(), cmd.totalTtc(), cmd.dateFacture(), cmd.dateFacture(), cmd.libelle()),
                cmd.dateFacture()).id());
        return ecritureRepository.findById(id, cmd.centerId()).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EcritureComptable genererEcritureReglement(GenererEcritureReglementCommand cmd) {
        UUID id = traduireLesRefus(() -> comptabilisation.comptabiliserUnReglement(new CentreId(cmd.centerId()),
                new ReglementAComptabiliser(cmd.paiementId(), cmd.factureId(), cmd.montant(), cmd.dateReglement(),
                        cmd.modeReglement(), cmd.tiersLibelle(), cmd.tiersPayeurId())).id());
        return ecritureRepository.findById(id, cmd.centerId()).orElseThrow();
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
