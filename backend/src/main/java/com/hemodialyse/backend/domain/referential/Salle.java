package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "salle")
public class Salle {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String nom;

    protected Salle() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getCode() { return code; }
    public String getNom() { return nom; }
}

