package com.hemodialyse.backend.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteurDeReferentiel;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

/**
 * PL11-2 (ADR 0158) : les descripteurs publiés par l'Organisation, traduits dans le vocabulaire de la façade d'administration des
 * référentiels. La façade ne décrit plus les créneaux : elle les reçoit du contexte (champs, alias d'en-têtes, exemples, libellé, ordre).
 */
public final class ReferentielsDeLOrganisation {

    public static final ReferentialKind CRENEAU = versKind(DescripteursDeLOrganisation.CRENEAU);

    /** PL11-3 (ADR 0159) : les médecins traitants, décrits par l'Organisation. */
    public static final ReferentialKind MEDECIN = versKind(DescripteursDeLOrganisation.MEDECIN);

    /** PL11-4 (ADR 0160) : les salles, décrites par l'Organisation. */
    public static final ReferentialKind SALLE = versKind(DescripteursDeLOrganisation.SALLE);

    private ReferentielsDeLOrganisation() {
    }

    static ReferentialKind versKind(DescripteurDeReferentiel d) {
        return new ReferentialKind(d.slug(), d.libelle(), d.ordreDImport(),
                d.champs().stream().map(ReferentielsDeLOrganisation::champ).toList(), d.cleNaturelle());
    }

    private static ReferentialField champ(DescripteurDeReferentiel.Champ c) {
        String[] alias = c.alias().toArray(new String[0]);
        return switch (c.genre()) {
            case TEXTE -> ReferentialField.text(c.cle(), c.libelle(), c.obligatoire(), c.longueurMax(), c.exemple(), alias);
            case CHOIX -> ReferentialField.enumeration(c.cle(), c.libelle(), c.valeursAutorisees(), c.valeurParDefaut(), alias);
            case ENTIER -> ReferentialField.integer(c.cle(), c.libelle(), c.obligatoire(), c.exemple(), alias);
        };
    }
}
