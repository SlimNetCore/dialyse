package com.hemodialyse.backend.application.service;

import com.hemodialyse.backend.application.port.in.AttestationUseCase;
import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AttestationServiceImpl implements AttestationUseCase {

    private final AttestationRepositoryPort repo;

    public AttestationServiceImpl(AttestationRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public AttestationDroit create(CenterId centerId, UUID patientId, LocalDate dateDebut, LocalDate dateFin) {
        return repo.save(new AttestationDroit(UUID.randomUUID(), patientId, centerId.value(), dateDebut, dateFin));
    }

    @Override
    public List<AttestationDroit> listByPatient(CenterId centerId, UUID patientId) {
        return repo.findByPatient(centerId, patientId);
    }
}

