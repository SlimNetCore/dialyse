package com.hemodialyse.backend.domain.referential;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "caisse_assurance")
public class CaisseAssurance {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String nom;
    @Column(name = "type_caisse") private String typeCaisse;

    protected CaisseAssurance() {}
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getCode() { return code; }
    public String getNom() { return nom; }
    public String getTypeCaisse() { return typeCaisse; }
}

