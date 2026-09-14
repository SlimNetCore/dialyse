package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.DecisionRcpJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionRcpJpaRepository extends JpaRepository<DecisionRcpJpaEntity, UUID> {

    List<DecisionRcpJpaEntity> findByBilanIdOrderByDateReunionDesc(UUID bilanId);

    void deleteByBilanId(UUID bilanId);
}
