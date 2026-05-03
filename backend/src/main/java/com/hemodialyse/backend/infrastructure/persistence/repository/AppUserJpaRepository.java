package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppUserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUserJpaEntity, UUID>, JpaSpecificationExecutor<AppUserJpaEntity> {
}

