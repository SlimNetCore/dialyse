package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.entity.DecisionRcp;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain Service — Bilan de préparation à la greffe rénale (receveur). Classe pure du domaine
 * (AGENTS.md §3), câblée en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class BilanPreGreffeDomainService implements BilanPreGreffeUseCase {

    private final BilanPreGreffeRepositoryPort repository;

    public BilanPreGreffeDomainService(BilanPreGreffeRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public BilanPreGreffe getOrCreate(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId)
                .orElseGet(() -> repository.save(BilanPreGreffe.ouvrir(patientId, centerId.value())));
    }

    @Override
    public BilanPreGreffe changerStatut(CenterId centerId, UUID patientId, StatutBilanGreffe statut) {
        BilanPreGreffe bilan = getOrCreate(centerId, patientId);
        bilan.changerStatut(statut);
        return repository.save(bilan);
    }

    @Override
    public BilanPreGreffe mettreAJourBilanImmunologique(CenterId centerId, UUID patientId,
                                                        String groupeSanguinConfirme, String typageHla,
                                                        BigDecimal praClasseI, BigDecimal praClasseII) {
        BilanPreGreffe bilan = getOrCreate(centerId, patientId);
        bilan.mettreAJourBilanImmunologique(groupeSanguinConfirme, typageHla, praClasseI, praClasseII);
        return repository.save(bilan);
    }

    @Override
    public BilanPreGreffe mettreAJourNotes(CenterId centerId, UUID patientId, String contreIndications,
                                           String conclusionNephrologue) {
        BilanPreGreffe bilan = getOrCreate(centerId, patientId);
        bilan.mettreAJourNotes(contreIndications, conclusionNephrologue);
        return repository.save(bilan);
    }

    @Override
    public BilanPreGreffe ajouterDecisionRcp(CenterId centerId, UUID patientId, LocalDate dateReunion, AvisRcp avis,
                                             String compteRendu, LocalDate prochaineDateRevue) {
        BilanPreGreffe bilan = getOrCreate(centerId, patientId);
        bilan.ajouterDecisionRcp(DecisionRcp.creer(dateReunion, avis, compteRendu, prochaineDateRevue));
        return repository.save(bilan);
    }
}
