package com.hemodialyse.backend.domain.center;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "centers")
public class Center {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Center() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}

