package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "agence")
public class Agence {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(name = "caisse_id", nullable = false) private UUID caisseId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String nom;

    protected Agence() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public UUID getCaisseId() { return caisseId; }
    public String getCode() { return code; }
    public String getNom() { return nom; }
}

