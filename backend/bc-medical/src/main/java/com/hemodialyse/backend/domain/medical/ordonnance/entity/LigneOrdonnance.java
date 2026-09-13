package com.hemodialyse.backend.domain.medical.ordonnance.entity;

import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.UUID;

/**
 * Entité (AGENTS.md §14) — une ligne d'ordonnance : le médicament (l'équivalent d'une ressource
 * FHIR {@code MedicationRequest.dosageInstruction}), sa posologie et sa durée. N'a de sens qu'à
 * l'intérieur de l'agrégat {@link com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance}.
 */
public final class LigneOrdonnance {

    private final UUID id;
    private final ConceptCode medicament;
    private final String libelle;
    private final String posologie;
    private final String voie;
    private final Integer dureeJours;
    private final Integer quantite;
    private final String instructions;

    private LigneOrdonnance(UUID id, ConceptCode medicament, String libelle, String posologie, String voie,
                            Integer dureeJours, Integer quantite, String instructions) {
        this.id = id;
        this.medicament = medicament;
        this.libelle = libelle;
        this.posologie = posologie;
        this.voie = voie;
        this.dureeJours = dureeJours;
        this.quantite = quantite;
        this.instructions = instructions;
    }

    public static LigneOrdonnance creer(ConceptCode medicament, String libelle, String posologie, String voie,
                                        Integer dureeJours, Integer quantite, String instructions) {
        if (medicament == null && (libelle == null || libelle.isBlank())) {
            throw new BusinessException("LIGNE_ORDONNANCE_MEDICAMENT_REQUIS",
                    "Un code ATC ou, à défaut, le nom du médicament est obligatoire pour chaque ligne");
        }
        if (posologie == null || posologie.isBlank()) {
            throw new BusinessException("LIGNE_ORDONNANCE_POSOLOGIE_REQUISE",
                    "La posologie est obligatoire pour chaque ligne d'ordonnance");
        }
        if (dureeJours != null && dureeJours <= 0) {
            throw new BusinessException("LIGNE_ORDONNANCE_DUREE_INVALIDE", "La durée doit être positive");
        }
        if (quantite != null && quantite <= 0) {
            throw new BusinessException("LIGNE_ORDONNANCE_QUANTITE_INVALIDE", "La quantité doit être positive");
        }
        return new LigneOrdonnance(UUID.randomUUID(), medicament, libelle, posologie, voie, dureeJours, quantite,
                instructions);
    }

    public static LigneOrdonnance reconstituer(UUID id, ConceptCode medicament, String libelle, String posologie,
                                               String voie, Integer dureeJours, Integer quantite, String instructions) {
        return new LigneOrdonnance(id, medicament, libelle, posologie, voie, dureeJours, quantite, instructions);
    }

    public UUID getId() {
        return id;
    }

    public ConceptCode getMedicament() {
        return medicament;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getPosologie() {
        return posologie;
    }

    public String getVoie() {
        return voie;
    }

    public Integer getDureeJours() {
        return dureeJours;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public String getInstructions() {
        return instructions;
    }
}
