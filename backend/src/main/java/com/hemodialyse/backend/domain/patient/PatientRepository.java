package com.hemodialyse.backend.domain.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByCenterIdAndNumeroAssurance(UUID centerId, String numeroAssurance);
    Optional<Patient> findByIdAndCenterId(UUID id, UUID centerId);
    List<Patient> findByCenterId(UUID centerId);
}
