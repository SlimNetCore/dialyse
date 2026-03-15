package com.hemodialyse.backend.domain.center;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_center_assignment",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "center_id", "role_code"}))
public class UserCenterAssignment {

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

    protected UserCenterAssignment() {}

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public UUID getCenterId() { return centerId; }
    public String getRoleCode() { return roleCode; }
}

