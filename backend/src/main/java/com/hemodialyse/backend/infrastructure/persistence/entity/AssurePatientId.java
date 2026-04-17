package com.hemodialyse.backend.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AssurePatientId implements Serializable {
    private UUID patientId;
    private String numeroAssurance;

    public AssurePatientId() {}

    public AssurePatientId(UUID patientId, String numeroAssurance) {
        this.patientId = patientId;
        this.numeroAssurance = numeroAssurance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssurePatientId that)) return false;
        return Objects.equals(patientId, that.patientId) && Objects.equals(numeroAssurance, that.numeroAssurance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId, numeroAssurance);
    }
}

