package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Type d'équipement GMAO
 */
public enum TypeEquipement {
    GENERATEUR_DIALYSE("Générateur de dialyse"),
    STATION_TRAITEMENT_EAU("Station de traitement d'eau"),
    RO_REVERSE_OSMOSIS("RO (Reverse Osmosis)"),
    ULTRAFILTRE("Ultrafiltre"),
    CHARBON_ACTIF("Charbon actif"),
    ADOUCISSEUR("Adoucisseur"),
    DESINFECTANT_CHIMIQUE("Désinfectant chimique"),
    FILTRE_PARTICULES("Filtre à particules"),
    POMPE_EAU("Pompe d'eau"),
    COMPRESSEUR_AIR("Compresseur d'air"),
    ALARME_SURVEILLANCE("Alarme de surveillance"),
    AUTRE("Autre");

    private final String label;

    TypeEquipement(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

