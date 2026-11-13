package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeForfait;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF2 : les valeurs de l'administration sont traduites vers le langage publié, les refus vers les erreurs de l'administration. */
class ForfaitsParPatientCouvertureTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID FORFAIT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private RuntimeException aLever;
    private final List<String> appels = new java.util.ArrayList<>();

    private final GestionDuCatalogueDeForfaits catalogue = new GestionDuCatalogueDeForfaits() {
        @Override
        public FicheDeForfait creer(UUID centre, String code, String libelle, String prix) {
            appels.add("creer:" + code + ":" + libelle + ":" + prix);
            if (aLever != null) throw aLever;
            return new FicheDeForfait(FORFAIT, code, libelle, prix == null ? null : new BigDecimal(prix));
        }

        @Override
        public FicheDeForfait modifier(UUID centre, UUID id, String code, String libelle, String prix) {
            appels.add("modifier:" + code + ":" + prix);
            return new FicheDeForfait(id, code, libelle, null);
        }

        @Override
        public void supprimer(UUID centre, UUID id) {
            if (aLever != null) throw aLever;
        }

        @Override
        public PageDeForfaits lister(UUID centre, String recherche, int page, int taille) {
            return new PageDeForfaits(List.of(new FicheDeForfait(FORFAIT, "HD", "Hémodialyse", new BigDecimal("5600.00"))), 1, page, taille);
        }

        @Override
        public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
            appels.add("importer:" + lignes.size() + ":" + ecrire);
            return new BilanDImport(1, 2, List.of(new Anomalie(3, "prix", "NEGATIVE_NUMBER", "Négatif.", Map.of())), false);
        }
    };

    private final ForfaitsParPatientCouverture acl = new ForfaitsParPatientCouverture(catalogue);

    @Test
    void elle_ne_prend_que_les_forfaits() {
        assertThat(acl.prend(ReferentielsDePatientCouverture.FORFAIT)).isTrue();
        assertThat(acl.prend(ReferentielsDePatientCouverture.TRANSPORTEUR)).isFalse();
        assertThat(acl.prend(com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.SALLE)).isFalse();
    }

    @Test
    void la_saisie_passe_le_code_le_libelle_et_le_prix_tels_quels_et_le_prix_est_rendu_en_texte_simple() {
        ReferentialEntry entree = acl.create(CENTRE, ReferentielsDePatientCouverture.FORFAIT, java.util.Collections.singletonMap("prix", "5600.50"));
        assertThat(entree.values()).containsEntry("prix", "5600.50");

        ReferentialEntry sansPrix = acl.update(CENTRE, ReferentielsDePatientCouverture.FORFAIT, FORFAIT, Map.of("code", "HD", "libelle", "Hémodialyse"));
        assertThat(sansPrix.values().get("prix")).isNull();
        assertThat(appels).containsExactly("creer:null:null:5600.50", "modifier:HD:null");
    }

    @Test
    void la_liste_rend_le_code_le_libelle_et_le_prix() {
        var page = acl.list(CENTRE, ReferentielsDePatientCouverture.FORFAIT, null, 0, 20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).values()).containsEntry("code", "HD").containsEntry("libelle", "Hémodialyse")
                .containsEntry("prix", "5600.00");
    }

    @Test
    void un_forfait_deja_existant_est_une_anomalie_sur_le_code_avec_le_message_d_avant() {
        aLever = RefusDeForfait.dejaExistant("doublon");
        assertThatThrownBy(() -> acl.create(CENTRE, ReferentielsDePatientCouverture.FORFAIT, Map.of("code", "HD")))
                .isInstanceOfSatisfying(ReferentialValidationException.class, e -> assertThat(e.getIssues())
                        .extracting(ValidationIssue::field, ValidationIssue::code, ValidationIssue::message)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("code", "ALREADY_EXISTS",
                                "Un élément de « Forfaits » avec ce code existe déjà.")));
    }

    @Test
    void un_forfait_introuvable_ou_utilise_est_une_erreur_metier_avec_le_message_d_avant() {
        aLever = RefusDeForfait.introuvable();
        assertThatThrownBy(() -> acl.delete(CENTRE, ReferentielsDePatientCouverture.FORFAIT, FORFAIT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("REFERENTIAL_NOT_FOUND");
                    assertThat(e.getMessage()).isEqualTo("Élément introuvable dans « Forfaits » pour ce centre.");
                });

        aLever = RefusDeForfait.utilise(4);
        assertThatThrownBy(() -> acl.delete(CENTRE, ReferentielsDePatientCouverture.FORFAIT, FORFAIT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("REFERENTIAL_IN_USE");
                    assertThat(e.getMessage()).isEqualTo("Suppression impossible : cet élément de « Forfaits » est encore utilisé "
                            + "(4 référence(s) : patients, prises en charge, séances ou autres référentiels).");
                });
    }

    @Test
    void l_import_traduit_les_lignes_et_rend_le_bilan() {
        ReferentialAdminDelegate.Outcome bilan = acl.importRows(CENTRE, ReferentielsDePatientCouverture.FORFAIT,
                List.of(new ReferentialAdminDelegate.Row(2, Map.of("code", "HD", "libelle", "x", "prix", "1"))), true);

        assertThat(appels).containsExactly("importer:1:false");
        assertThat(bilan.toCreate()).isEqualTo(1);
        assertThat(bilan.toUpdate()).isEqualTo(2);
        assertThat(bilan.applied()).isFalse();
        assertThat(bilan.errors()).extracting(ValidationIssue::row, ValidationIssue::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3, "NEGATIVE_NUMBER"));
    }
}
