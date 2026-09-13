package com.hemodialyse.backend.domain.medical.serologie.service;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieUseCase;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Sérologies. Classe pure du domaine (AGENTS.md §3), câblée en
 * {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class SerologieDomainService implements SerologieUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final SerologieRepositoryPort repository;

    public SerologieDomainService(SerologieRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<Serologie> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public List<Serologie> listDerniersResultatsByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Serologie::getMarqueur,
                        s -> s,
                        (a, b) -> a.getDatePrelevement().isAfter(b.getDatePrelevement()) ? a : b))
                .values().stream()
                .sorted(Comparator.comparing(Serologie::getMarqueur))
                .toList();
    }

    @Override
    public Serologie create(CenterId centerId, UUID patientId, MarqueurSerologique marqueur,
                            ResultatSerologique resultat, BigDecimal titre, String unite,
                            LocalDate datePrelevement, String laboratoire, LocalDate dateProchainControle,
                            String conduiteATenir) {
        LocalDate effectiveDate = datePrelevement != null ? datePrelevement : LocalDate.now();
        if (repository.existsByPatientIdAndMarqueurAndDatePrelevement(patientId, centerId, marqueur, effectiveDate)) {
            throw new IllegalStateException("Un résultat existe déjà pour ce marqueur à cette date de prélèvement");
        }
        Serologie serologie = Serologie.enregistrer(patientId, centerId.value(), marqueur, resultat, titre, unite,
                effectiveDate, laboratoire, dateProchainControle, conduiteATenir);
        return repository.save(serologie);
    }

    @Override
    public Serologie update(CenterId centerId, UUID patientId, UUID serologieId, ResultatSerologique resultat,
                            String conduiteATenir) {
        Serologie serologie = repository.findById(serologieId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Sérologie introuvable"));
        serologie.corrigerResultat(resultat, conduiteATenir);
        return repository.save(serologie);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID serologieId) {
        repository.deleteById(serologieId, patientId, centerId);
    }
}
