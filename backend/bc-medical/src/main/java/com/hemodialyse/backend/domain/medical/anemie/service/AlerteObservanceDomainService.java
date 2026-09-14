package com.hemodialyse.backend.domain.medical.anemie.service;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceUseCase;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Alertes d'observance des prescriptions EPO/fer. Classe pure du domaine
 * (AGENTS.md §3), câblée en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class AlerteObservanceDomainService implements AlerteObservanceUseCase {

    private final AlerteObservanceRepositoryPort repository;

    public AlerteObservanceDomainService(AlerteObservanceRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<AlerteObservance> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public AlerteObservance resoudre(CenterId centerId, UUID patientId, UUID alerteId) {
        AlerteObservance alerte = repository.findById(alerteId, patientId, centerId)
                .orElseThrow(() -> new BusinessException("ALERTE_OBSERVANCE_INTROUVABLE", "Alerte d'observance introuvable"));
        return repository.save(alerte.resoudre());
    }

    @Override
    public void signalerNonConformite(CenterId centerId, UUID patientId, TypeTraitementAnemie typeTraitement,
                                      LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues,
                                      int dosesAdministrees, String message) {
        boolean dejaOuverte = repository.findActiveByPatientAndType(patientId, centerId, typeTraitement).isPresent();
        if (dejaOuverte) {
            return;
        }
        AlerteObservance alerte = AlerteObservance.declencher(patientId, centerId.value(), typeTraitement,
                periodeDebut, periodeFin, dosesAttendues, dosesAdministrees, message);
        repository.save(alerte);
    }

    @Override
    public void resoudreSiConforme(CenterId centerId, UUID patientId, TypeTraitementAnemie typeTraitement) {
        repository.findActiveByPatientAndType(patientId, centerId, typeTraitement)
                .ifPresent(alerte -> repository.save(alerte.resoudre()));
    }
}
