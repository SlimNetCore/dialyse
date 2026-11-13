package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ;

import java.util.List;

import static com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ.decimal;
import static com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ.enumeration;
import static com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ.reference;
import static com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ.telephone;
import static com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel.Champ.texte;

/**
 * Les référentiels administrables de Patient et couverture (PL11-1, ADR 0157) : mêmes champs, alias, exemples, libellés et ordre
 * d'import qu'avant leur retrait de l'administration générique (les modèles d'import téléchargés ne changent pas d'un octet).
 */
public final class DescripteursDeReferentiels {

    public static final DescripteurDeReferentiel TRANSPORTEUR = new DescripteurDeReferentiel("transporteurs", "Transporteurs", 1,
            List.of(texte("nom", "Nom", true, 255, "Ambulances Rouiba", "transporteur", "raison sociale"),
                    telephone("telephone", "Téléphone", false, 50, "0795006136", "tel", "telephone portable", "mobile")),
            List.of("nom"));

    public static final DescripteurDeReferentiel FORFAIT = new DescripteurDeReferentiel("forfaits", "Forfaits", 1,
            List.of(texte("code", "Code", true, 50, "HD-CONV", "code forfait"),
                    texte("libelle", "Libellé", true, 255, "Hémodialyse conventionnelle", "description", "designation"),
                    decimal("prix", "Prix", false, "5600.00", "tarif", "montant")),
            List.of("code"));

    public static final DescripteurDeReferentiel CAISSE = new DescripteurDeReferentiel("caisses", "Caisses", 1,
            List.of(texte("code", "Code", true, 50, "CNAS", "code caisse"),
                    texte("nom", "Nom", true, 255, "Caisse nationale des assurances sociales", "libelle", "caisse"),
                    enumeration("typeCaisse", "Type de caisse", List.of("STANDARD", "VACANCIER"), "STANDARD", "type")),
            List.of("code"));

    public static final DescripteurDeReferentiel AGENCE = new DescripteurDeReferentiel("agences", "Agences", 2,
            List.of(texte("code", "Code", true, 50, "CNAS-AG1", "code agence"),
                    texte("nom", "Nom", true, 255, "Agence CNAS Alger", "libelle", "agence"),
                    reference("caisse", "Caisse (code)", "caisses", "Caisses", "CNAS", "caisse", "code caisse", "caisse code")),
            List.of("code"));

    public static final DescripteurDeReferentiel CENTRE_PAYEUR = new DescripteurDeReferentiel("centres-payeurs", "Centres payeurs", 3,
            List.of(texte("code", "Code", true, 50, "CP-ALG-01", "code centre payeur", "code centre"),
                    texte("nom", "Nom", true, 255, "Centre payeur Alger Centre", "libelle", "centre payeur"),
                    texte("adresse", "Adresse", false, 255, "12 rue Didouche Mourad, Alger"),
                    reference("agence", "Agence (code)", "agences", "Agences", "CNAS-AG1", "agence", "code agence", "agence code")),
            List.of("code"));

    private DescripteursDeReferentiels() {
    }

    /** Les cinq descripteurs, dans l'ordre historique de l'administration générique. */
    public static List<DescripteurDeReferentiel> tous() {
        return List.of(FORFAIT, CAISSE, AGENCE, CENTRE_PAYEUR, TRANSPORTEUR);
    }

    static Champ champDe(DescripteurDeReferentiel d, String cle) {
        return d.champs().stream().filter(c -> c.cle().equals(cle)).findFirst().orElseThrow();
    }
}
