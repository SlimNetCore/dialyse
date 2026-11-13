package com.hemodialyse.backend.infrastructure.referential.admin;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteursDeReferentiels;
import com.hemodialyse.backend.infrastructure.acl.patientcouverture.ReferentielsDePatientCouverture;
import com.hemodialyse.backend.infrastructure.referential.admin.model.FieldType;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegates;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialCatalogue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL11-1 (ADR 0157) : le catalogue de la façade = les descripteurs directs + ceux que les contextes publient. */
class ReferentialCatalogueTest {

    private static ReferentialAdminDelegate fournisseurDePatientCouverture() {
        return new ReferentialAdminDelegates(List.of(delegue(List.of(ReferentielsDePatientCouverture.FORFAIT,
                ReferentielsDePatientCouverture.CAISSE, ReferentielsDePatientCouverture.AGENCE,
                ReferentielsDePatientCouverture.CENTRE_PAYEUR, ReferentielsDePatientCouverture.TRANSPORTEUR))));
    }

    private static ReferentialAdminDelegate delegue(List<ReferentialKind> publies) {
        return new ReferentialAdminDelegate() {
            @Override
            public boolean prend(ReferentialKind kind) {
                return publies.contains(kind);
            }

            @Override
            public List<ReferentialKind> descripteurs() {
                return publies;
            }

            @Override
            public com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry> list(
                    com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId c, ReferentialKind k, String s, int p, int z) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry create(
                    com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId c, ReferentialKind k, java.util.Map<String, String> v) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry update(
                    com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId c, ReferentialKind k, java.util.UUID id,
                    java.util.Map<String, String> v) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId c, ReferentialKind k, java.util.UUID id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome importRows(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId c, ReferentialKind k, List<Row> r,
                                      boolean d) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void le_catalogue_garde_l_ordre_d_import_puis_l_ordre_d_affichage_historique_de_l_ecran() {
        ReferentialCatalogue catalogue = new ReferentialCatalogue(List.of(fournisseurDePatientCouverture()));

        assertThat(catalogue.kinds()).extracting(ReferentialKind::slug).containsExactly("forfaits", "creneaux", "salles",
                "medecins", "caisses", "transporteurs", "agences", "centres-payeurs");
    }

    @Test
    void un_referentiel_se_retrouve_par_son_segment_d_url_sans_egard_a_la_casse_et_un_inconnu_est_refuse_comme_avant() {
        ReferentialCatalogue catalogue = new ReferentialCatalogue(List.of(fournisseurDePatientCouverture()));

        assertThat(catalogue.fromSlug("Centres-Payeurs")).isEqualTo(ReferentielsDePatientCouverture.CENTRE_PAYEUR);
        assertThat(catalogue.fromSlug("salles")).isSameAs(ReferentialKind.SALLE);
        assertThatThrownBy(() -> catalogue.fromSlug("generateurs")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Référentiel inconnu : generateurs");
    }

    @Test
    void sans_fournisseur_la_facade_ne_connait_que_les_trois_referentiels_encore_directs() {
        assertThat(new ReferentialCatalogue(List.of()).kinds()).extracting(ReferentialKind::slug)
                .containsExactly("creneaux", "salles", "medecins");
    }

    @Test
    void les_descripteurs_recus_de_patient_et_couverture_gardent_leurs_champs_alias_et_references() {
        var agence = ReferentielsDePatientCouverture.AGENCE;
        var caisse = agence.field("caisse").orElseThrow();

        assertThat(agence.label()).isEqualTo(DescripteursDeReferentiels.AGENCE.libelle());
        assertThat(agence.importOrder()).isEqualTo(2);
        assertThat(agence.naturalKey()).containsExactly("code");
        assertThat(caisse.type()).isEqualTo(FieldType.REFERENCE);
        assertThat(caisse.referenceSlug()).isEqualTo("caisses");
        assertThat(caisse.referenceLabel()).isEqualTo("Caisses");
        assertThat(caisse.matchesHeader("codecaisse")).isTrue();
        assertThat(ReferentielsDePatientCouverture.TRANSPORTEUR.field("telephone").orElseThrow().type()).isEqualTo(FieldType.PHONE);
        assertThat(ReferentielsDePatientCouverture.CAISSE.field("typeCaisse").orElseThrow().allowedValues())
                .containsExactly("STANDARD", "VACANCIER");
    }
}
