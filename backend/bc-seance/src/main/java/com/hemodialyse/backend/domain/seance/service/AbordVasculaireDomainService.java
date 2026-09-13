package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Abord vasculaire. Pure domain class (hexagonal, AGENTS.md §3);
 * wired in {@code infrastructure/config/DomainServiceConfig}.
 */
public class AbordVasculaireDomainService implements AbordVasculaireUseCase {

    /**
     * Taille de page par défaut lorsque l'appelant n'en fournit pas (AGENTS.md §9).
     */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * Garde-fou : empêche un client de contourner la pagination en réclamant une page géante.
     */
    private static final int MAX_PAGE_SIZE = 200;

    private final AbordVasculaireRepositoryPort repository;

    public AbordVasculaireDomainService(AbordVasculaireRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<AbordVasculaire> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public PagedResult<AbordVasculaire> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return repository.findPagedByPatientId(patientId, centerId, Math.max(page, 0), safeSize);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID abordId) {
        repository.deleteById(abordId, patientId, centerId);
    }

    @Override
    public AbordVasculaire save(CenterId centerId,
                                UUID patientId,
                                UUID abordId,
                                String typeAbord,
                                String cote,
                                String localisation,
                                LocalDate dateCreation,
                                LocalDate dateFin,
                                Boolean actif,
                                String complications) {
        // Invariant clinique : un abord ne peut pas être retiré avant d'avoir été créé.
        if (dateCreation != null && dateFin != null && dateFin.isBefore(dateCreation)) {
            throw new BusinessException(
                    "ABORD_DATE_FIN_ANTERIEURE",
                    "La date de fin de l'abord vasculaire ne peut pas précéder sa date de création");
        }
        AbordVasculaire abord = new AbordVasculaire();
        abord.setId(abordId != null ? abordId : UUID.randomUUID());
        abord.setPatientId(patientId);
        abord.setCenterId(centerId.value());
        abord.setTypeAbord(typeAbord);
        abord.setCote(cote);
        abord.setLocalisation(localisation);
        abord.setDateCreation(dateCreation);
        abord.setDateFin(dateFin);
        abord.setActif(actif != null ? actif : Boolean.TRUE);
        abord.setComplications(complications);
        abord.setCreatedAt(OffsetDateTime.now());
        return repository.save(abord);
    }
}

