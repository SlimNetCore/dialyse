package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteurDeReferentiel;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteursDeReferentiels;
import com.hemodialyse.backend.infrastructure.referential.admin.model.FieldType;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.List;

/**
 * PL11-1 (ADR 0157) : les descripteurs publiés par Patient et couverture, traduits dans le vocabulaire de la façade d'administration
 * des référentiels. La façade ne les décrit plus : elle les reçoit du contexte (champs, alias d'en-têtes, exemples, libellés, ordre).
 */
public final class ReferentielsDePatientCouverture {

    public static final ReferentialKind TRANSPORTEUR = versKind(DescripteursDeReferentiels.TRANSPORTEUR);
    public static final ReferentialKind FORFAIT = versKind(DescripteursDeReferentiels.FORFAIT);
    public static final ReferentialKind CAISSE = versKind(DescripteursDeReferentiels.CAISSE);
    public static final ReferentialKind AGENCE = versKind(DescripteursDeReferentiels.AGENCE);
    public static final ReferentialKind CENTRE_PAYEUR = versKind(DescripteursDeReferentiels.CENTRE_PAYEUR);

    /** La chaîne des payeurs. */
    public static final List<ReferentialKind> PAYEURS = List.of(CAISSE, AGENCE, CENTRE_PAYEUR);

    private ReferentielsDePatientCouverture() {
    }

    /** Les cinq référentiels du contexte, dans l'ordre historique de la façade. */
    public static List<ReferentialKind> tous() {
        return List.of(FORFAIT, CAISSE, AGENCE, CENTRE_PAYEUR, TRANSPORTEUR);
    }

    static ReferentialKind versKind(DescripteurDeReferentiel d) {
        return new ReferentialKind(d.slug(), d.libelle(), d.ordreDImport(),
                d.champs().stream().map(ReferentielsDePatientCouverture::champ).toList(), d.cleNaturelle());
    }

    private static ReferentialField champ(DescripteurDeReferentiel.Champ c) {
        FieldType type = switch (c.type()) {
            case TEXTE -> FieldType.TEXT;
            case TELEPHONE -> FieldType.PHONE;
            case DECIMAL -> FieldType.DECIMAL;
            case ENTIER -> FieldType.INTEGER;
            case ENUMERATION -> FieldType.ENUM;
            case REFERENCE -> FieldType.REFERENCE;
        };
        return new ReferentialField(c.cle(), c.libelle(), type, c.obligatoire(), c.longueurMax(), c.valeursAutorisees(),
                c.valeurParDefaut(), c.referentielCible(), c.libelleDuReferentielCible(), c.exemple(), c.alias());
    }
}
