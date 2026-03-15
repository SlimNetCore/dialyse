package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "transporteur")
public class Transporteur {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(nullable = false) private String nom;
    private String telephone;

    protected Transporteur() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getNom() { return nom; }
    public String getTelephone() { return telephone; }
}

