package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "position_creneau")
public class PositionCreneau {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String libelle;

    protected PositionCreneau() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
}

