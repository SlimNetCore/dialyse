package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/**
 * Les référentiels administrables de l'Organisation (PL11-2, ADR 0158) : mêmes champs, alias, exemples, libellés et ordre d'import
 * qu'avant leur retrait de l'administration générique (le modèle d'import téléchargé ne change pas d'un octet).
 */
public final class DescripteursDeLOrganisation {

    public static final DescripteurDeReferentiel CRENEAU = new DescripteurDeReferentiel("creneaux", "Créneaux", 1,
            List.of(new DescripteurDeReferentiel.Champ("code", "Code", true, 50, "CR1", List.of("code creneau", "code position")),
                    new DescripteurDeReferentiel.Champ("libelle", "Libellé", true, 255, "Matin (06h30 – 10h30)",
                            List.of("horaire", "plage horaire", "creneau"))),
            List.of("code"));

    /** PL11-3 (ADR 0159) : les médecins traitants. Clé d'unicité : nom puis prénom. */
    public static final DescripteurDeReferentiel MEDECIN = new DescripteurDeReferentiel("medecins", "Médecins traitants", 1,
            List.of(new DescripteurDeReferentiel.Champ("nom", "Nom", true, 255, "OUARET", List.of("nom medecin")),
                    new DescripteurDeReferentiel.Champ("prenom", "Prénom", false, 255, "Mustapha", List.of("prenom medecin")),
                    new DescripteurDeReferentiel.Champ("specialite", "Spécialité", false, 255, "Néphrologue", List.of())),
            List.of("nom", "prenom"));

    /**
     * PL11-4 (ADR 0160) : les salles. Clé d'unicité : le code. Isolement : NON par défaut ; capacité : entier facultatif (générateurs max.).
     */
    public static final DescripteurDeReferentiel SALLE = new DescripteurDeReferentiel("salles", "Salles", 1,
            List.of(new DescripteurDeReferentiel.Champ("code", "Code", true, 50, "S1", List.of("code salle")),
                    new DescripteurDeReferentiel.Champ("nom", "Nom", true, 255, "Salle 1", List.of("libelle", "salle")),
                    new DescripteurDeReferentiel.Champ("isolement", "Salle d'isolement", true, 30, "NON",
                            List.of("isolement", "salle isolement"), DescripteurDeReferentiel.Genre.CHOIX, List.of("NON", "OUI"), "NON"),
                    new DescripteurDeReferentiel.Champ("capacite", "Capacité (générateurs max.)", false, 0, "12",
                            List.of("capacite", "nombre de generateurs"), DescripteurDeReferentiel.Genre.ENTIER, List.of(), null)),
            List.of("code"));

    private DescripteursDeLOrganisation() {
    }
}
