package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "medecin")
public class Medecin {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(nullable = false) private String nom;
    private String prenom;
    private String specialite;

    protected Medecin() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getSpecialite() { return specialite; }
}

