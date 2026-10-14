package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** Type d'équipement (RG-GMA-010). Les codes sont ceux du contrat observé et de la base. */
public enum TypeEquipement {
    GENERATEUR_DIALYSE,
    STATION_TRAITEMENT_EAU,
    RO_REVERSE_OSMOSIS,
    ULTRAFILTRE,
    CHARBON_ACTIF,
    ADOUCISSEUR,
    DESINFECTANT_CHIMIQUE,
    FILTRE_PARTICULES,
    POMPE_EAU,
    COMPRESSEUR_AIR,
    ALARME_SURVEILLANCE,
    AUTRE;

    /** Reconnaissance exacte du code (casse comprise, comme {@code Enum.valueOf} du legacy) ; sinon {@link TypeEquipementInconnu}. */
    public static TypeEquipement depuis(String code) {
        for (TypeEquipement type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        throw new TypeEquipementInconnu(code);
    }

    /** Seuls les générateurs de dialyse sont soumis à la capacité des salles et alertent la Planification. */
    public boolean estGenerateurDeDialyse() {
        return this == GENERATEUR_DIALYSE;
    }
}
