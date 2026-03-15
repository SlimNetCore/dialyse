package com.hemodialyse.backend.domain.pec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriseEnChargeRepository extends JpaRepository<PriseEnCharge, UUID> {
    Optional<PriseEnCharge> findByIdAndCenterId(UUID id, UUID centerId);
}

