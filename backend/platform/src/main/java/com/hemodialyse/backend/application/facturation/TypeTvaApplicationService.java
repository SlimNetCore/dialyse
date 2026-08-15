package com.hemodialyse.backend.application.facturation;

import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.facturation.port.TypeTvaRepositoryPort;
import com.hemodialyse.backend.domain.facturation.service.TypeTvaDomainService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class TypeTvaApplicationService {

    private final TypeTvaDomainService delegate;

    public TypeTvaApplicationService(TypeTvaRepositoryPort repository) {
        this.delegate = new TypeTvaDomainService(repository);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "tva-types-list", key = "#centerId + ':' + #page + ':' + #size")
    public PagedResult<TypeTVA> listerTypesTva(UUID centerId, int page, int size) {
        return delegate.listerTypesTva(centerId, page, size);
    }

    @CacheEvict(value = {"tva-types-list", "tva-active"}, key = "#centerId.toString()", allEntries = true)
    public TypeTVA creerTypeTva(UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                                boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                                String texteReference, String createdBy) {
        return delegate.creerTypeTva(centerId, libelle, taux, typePrestation,
                exonere, dateDebutValidite, dateFinValidite, texteReference, createdBy);
    }

    @CacheEvict(value = {"tva-types-list", "tva-active"}, key = "#centerId.toString()", allEntries = true)
    public TypeTVA modifierTypeTva(UUID id, UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                                   boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                                   String texteReference, String updatedBy) {
        return delegate.modifierTypeTva(id, centerId, libelle, taux, typePrestation,
                exonere, dateDebutValidite, dateFinValidite, texteReference, updatedBy);
    }

    @CacheEvict(value = {"tva-types-list", "tva-active"}, allEntries = true)
    public void desactiverTypeTva(UUID id, UUID centerId) {
        delegate.desactiverTypeTva(id, centerId);
    }
}



