package com.hemodialyse.backend.contexts.documents.infrastructure;

import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.documents.infrastructure.acl.organisation.IdentitesImprimablesParOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDeLaSociete;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDuCentre;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACL vers l'identité imprimée d'Organisation : traduction champ à champ, absence conservée. */
class IdentitesImprimablesParOrganisationTest {

    private static final UUID CENTRE = new UUID(0L, 1L);
    private static final UUID SOCIETE = new UUID(0L, 2L);
    private static final byte[] LOGO = {1, 2, 3};

    private static final IdentiteDeLaSociete S = new IdentiteDeLaSociete("Raison", "adr", "ville", "wilaya", "tel", "mail", "web", "nif", "nis", "rc",
            "pied", LOGO);
    private static final IdentiteDuCentre C = new IdentiteDuCentre("Centre", "cadr", "cville", "cwilaya", "ctel", "cmail", "cweb", S);

    private final IdentiteImprimee organisation = new IdentiteImprimee() {
        @Override
        public Optional<IdentiteDuCentre> duCentre(UUID centre) {
            return CENTRE.equals(centre) ? Optional.of(C) : Optional.empty();
        }

        @Override
        public Optional<IdentiteDeLaSociete> deLaSociete(UUID societe) {
            return SOCIETE.equals(societe) ? Optional.of(S) : Optional.empty();
        }
    };

    private final IdentitesImprimablesParOrganisation acl = new IdentitesImprimablesParOrganisation(organisation);

    @Test
    void should_translate_the_identity_of_a_centre_and_its_company_field_by_field() {
        IdentiteImprimable identite = acl.duCentre(CENTRE).orElseThrow();

        assertThat(identite.nom()).isEqualTo("Centre");
        assertThat(identite.adresse()).isEqualTo("cadr");
        assertThat(identite.ville()).isEqualTo("cville");
        assertThat(identite.wilaya()).isEqualTo("cwilaya");
        assertThat(identite.telephone()).isEqualTo("ctel");
        assertThat(identite.email()).isEqualTo("cmail");
        assertThat(identite.siteWeb()).isEqualTo("cweb");
        assertThat(identite.societe()).isEqualTo(new IdentiteImprimable.Societe("Raison", "adr", "ville", "wilaya", "tel", "mail", "web", "nif", "nis",
                "rc", "pied", LOGO));
    }

    @Test
    void should_translate_a_company_alone() {
        assertThat(acl.deLaSociete(SOCIETE).orElseThrow().raisonSociale()).isEqualTo("Raison");
        assertThat(acl.deLaSociete(SOCIETE).orElseThrow().logo()).isEqualTo(LOGO);
    }

    @Test
    void should_keep_absence_for_unknown_or_null_identifiers() {
        assertThat(acl.duCentre(new UUID(9L, 9L))).isEmpty();
        assertThat(acl.duCentre(null)).isEmpty();
        assertThat(acl.deLaSociete(new UUID(9L, 9L))).isEmpty();
        assertThat(acl.deLaSociete(null)).isEmpty();
    }
}
