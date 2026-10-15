package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * Modèle de persistance du compte client d'un payeur : table {@code comptes_payeurs} (V33), clé primaire (centre, payeur),
 * sans clé étrangère vers {@code centre_payeur} (KB-CPT-13c). Jamais exposé hors de {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "comptes_payeurs")
@IdClass(ComptePayeurJpa.Cle.class)
public class ComptePayeurJpa {

    @Id
    @Column(name = "center_id", nullable = false, columnDefinition = "uuid")
    private UUID centerId;

    @Id
    @Column(name = "payeur_id", nullable = false, columnDefinition = "uuid")
    private UUID payeurId;

    @Column(name = "compte", nullable = false, length = 20)
    private String compte;

    protected ComptePayeurJpa() {
    }

    ComptePayeurJpa(UUID centerId, UUID payeurId, String compte) {
        this.centerId = centerId;
        this.payeurId = payeurId;
        this.compte = compte;
    }

    String getCompte() {
        return compte;
    }

    void setCompte(String compte) {
        this.compte = compte;
    }

    /**
     * Clé composite (centre, payeur).
     */
    public static class Cle implements Serializable {
        private UUID centerId;
        private UUID payeurId;

        public Cle() {
        }

        public Cle(UUID centerId, UUID payeurId) {
            this.centerId = centerId;
            this.payeurId = payeurId;
        }

        @Override
        public boolean equals(Object autre) {
            return autre instanceof Cle cle && java.util.Objects.equals(centerId, cle.centerId)
                    && java.util.Objects.equals(payeurId, cle.payeurId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(centerId, payeurId);
        }
    }
}
