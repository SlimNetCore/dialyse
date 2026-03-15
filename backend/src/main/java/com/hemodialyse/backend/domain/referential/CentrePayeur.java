package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "centre_payeur")
public class CentrePayeur {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(name = "agence_id", nullable = false) private UUID agenceId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String nom;
    private String adresse;

    protected CentrePayeur() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public UUID getAgenceId() { return agenceId; }
    public String getCode() { return code; }
    public String getNom() { return nom; }
    public String getAdresse() { return adresse; }
}

