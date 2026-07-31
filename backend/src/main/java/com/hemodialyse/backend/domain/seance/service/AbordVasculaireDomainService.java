package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
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

    private final AbordVasculaireRepositoryPort repository;

    public AbordVasculaireDomainService(AbordVasculaireRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<AbordVasculaire> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
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

