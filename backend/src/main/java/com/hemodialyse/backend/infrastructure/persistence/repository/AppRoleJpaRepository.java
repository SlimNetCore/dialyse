package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppRoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AppRoleJpaRepository extends JpaRepository<AppRoleJpaEntity, UUID>, JpaSpecificationExecutor<AppRoleJpaEntity> {
}

