package com.hemodialyse.backend.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link AppSettingJpaEntity} (center_id, cle).
 */
public class AppSettingId implements Serializable {
    private UUID centerId;
    private String cle;

    public AppSettingId() {
    }

    public AppSettingId(UUID centerId, String cle) {
        this.centerId = centerId;
        this.cle = cle;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getCle() {
        return cle;
    }

    public void setCle(String cle) {
        this.cle = cle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSettingId that)) return false;
        return Objects.equals(centerId, that.centerId) && Objects.equals(cle, that.cle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerId, cle);
    }
}

