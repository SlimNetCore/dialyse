package com.hemodialyse.backend.domain.medical.antecedent.service;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentUseCase;
import com.hemodialyse.backend.domain.medical.antecedent.specification.AntecedentDupliqueSpecification;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain Service — Antécédents médicaux. Classe pure du domaine (AGENTS.md §3), câblée en
 * {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class AntecedentDomainService implements AntecedentUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AntecedentRepositoryPort repository;

    public AntecedentDomainService(AntecedentRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<Antecedent> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public Antecedent create(CenterId centerId, UUID patientId, TypeAntecedent type, ConceptCode diagnostic,
                             String libelleLibre, LocalDate dateDebut, LocalDate dateFin, String severite,
                             String note) {
        var existants = repository.findByPatientId(patientId, centerId);
        if (!AntecedentDupliqueSpecification.estSatisfaitePar(existants, diagnostic, null)) {
            throw new IllegalStateException("Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
        }
        Antecedent antecedent = Antecedent.creer(patientId, centerId.value(), type, diagnostic, libelleLibre,
                dateDebut, dateFin, severite, note);
        return repository.save(antecedent);
    }

    @Override
    public Antecedent update(CenterId centerId, UUID patientId, UUID antecedentId, ConceptCode diagnostic,
                             String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                             StatutClinique statutClinique, String severite, String note) {
        Antecedent antecedent = repository.findById(antecedentId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Antécédent introuvable"));
        var existants = repository.findByPatientId(patientId, centerId);
        if (!AntecedentDupliqueSpecification.estSatisfaitePar(existants, diagnostic, antecedentId)) {
            throw new IllegalStateException("Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
        }
        antecedent.modifier(diagnostic, libelleLibre, dateDebut, dateFin, statutClinique, severite, note);
        return repository.save(antecedent);
    }

    @Override
    public Antecedent resoudre(CenterId centerId, UUID patientId, UUID antecedentId, LocalDate dateResolution) {
        Antecedent antecedent = repository.findById(antecedentId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Antécédent introuvable"));
        antecedent.resoudre(dateResolution);
        return repository.save(antecedent);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID antecedentId) {
        repository.deleteById(antecedentId, patientId, centerId);
    }
}
