package com.hemodialyse.backend.contexts.documents.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.documents.application.IdentitesImprimables;
import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDeLaSociete;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDuCentre;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers Organisation (ADR 0140 D4, ADR 0142) : traduit le langage publié {@code IdentiteImprimee} (organisation.v1, ADR 0111) dans
 * l'identité imprimable de Documents. Aucune règle : les textes sont déjà rognés et jamais nuls côté Organisation.
 */
public class IdentitesImprimablesParOrganisation implements IdentitesImprimables {

    private final IdentiteImprimee organisation;

    public IdentitesImprimablesParOrganisation(IdentiteImprimee organisation) {
        this.organisation = organisation;
    }

    @Override
    public Optional<IdentiteImprimable> duCentre(UUID centre) {
        return organisation.duCentre(centre).map(IdentitesImprimablesParOrganisation::traduire);
    }

    @Override
    public Optional<IdentiteImprimable.Societe> deLaSociete(UUID societe) {
        return organisation.deLaSociete(societe).map(IdentitesImprimablesParOrganisation::traduire);
    }

    private static IdentiteImprimable traduire(IdentiteDuCentre c) {
        return new IdentiteImprimable(c.nom(), c.adresse(), c.ville(), c.wilaya(), c.telephone(), c.email(), c.siteWeb(), traduire(c.societe()));
    }

    private static IdentiteImprimable.Societe traduire(IdentiteDeLaSociete s) {
        return new IdentiteImprimable.Societe(s.raisonSociale(), s.adresse(), s.ville(), s.wilaya(), s.telephone(), s.email(), s.siteWeb(),
                s.nif(), s.nis(), s.rc(), s.piedDePage(), s.logo());
    }
}
