package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping the {@code user_center_assignment} table (multi-center scoping).
 * <p>
 * Relocated from {@code domain/center} to keep the domain layer free of any
 * JPA/Spring dependency (hexagonal architecture — see AGENTS.md §3).
 * This entity is the sole source of the {@code user_center_assignment} DDL
 * (created by Hibernate {@code ddl-auto: update} before {@code seed.sql} runs).
 */
@Entity
@Table(name = "user_center_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "center_id", "role_code"}))
public class UserCenterAssignmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected UserCenterAssignmentJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

