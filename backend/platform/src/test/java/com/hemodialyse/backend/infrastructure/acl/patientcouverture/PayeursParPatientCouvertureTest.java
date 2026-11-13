package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.Niveau;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDePayeur;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF3a : les valeurs de l'administration (caisses, agences, centres payeurs) sont traduites vers le langage publié, les refus vers les
 * erreurs de l'administration (mêmes codes et mêmes messages qu'avant).
 */
class PayeursParPatientCouvertureTest {

    static java.util.List<ReferentialKind> payeurs() {
        return ReferentielsDePatientCouverture.PAYEURS;
    }

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID PARENT = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private RuntimeException aLever;
    private final List<String> appels = new ArrayList<>();

    private final GestionDesPayeurs payeurs = new GestionDesPayeurs() {
        @Override
        public FicheDePayeur creer(UUID centre, Niveau niveau, SaisieDePayeur saisie) {
            appels.add("creer:" + niveau + ":" + saisie.code() + ":" + saisie.nom() + ":" + saisie.typeCaisse() + ":" + saisie.adresse()
                    + ":" + saisie.parent());
            if (aLever != null) throw aLever;
            return fiche(niveau, saisie.code());
        }

        @Override
        public FicheDePayeur modifier(UUID centre, Niveau niveau, UUID id, SaisieDePayeur saisie) {
            appels.add("modifier:" + niveau + ":" + saisie.code());
            return fiche(niveau, saisie.code());
        }

        @Override
        public void supprimer(UUID centre, Niveau niveau, UUID id) {
            appels.add("supprimer:" + niveau);
            if (aLever != null) throw aLever;
        }

        @Override
        public PageDePayeurs lister(UUID centre, Niveau niveau, String recherche, int page, int taille) {
            return new PageDePayeurs(List.of(fiche(niveau, "X")), 1, page, taille);
        }

        @Override
        public List<FicheDePayeur> toutes(UUID centre, Niveau niveau) {
            return List.of(fiche(niveau, "T"));
        }

        @Override
        public BilanDImport importer(UUID centre, Niveau niveau, List<LigneAImporter> lignes, boolean ecrire) {
            appels.add("importer:" + niveau + ":" + lignes.size() + ":" + ecrire + ":" + lignes.getFirst().saisie().parent());
            return new BilanDImport(1, 2, List.of(new Anomalie(3, "caisse", "REFERENCE_NOT_FOUND", "Introuvable.", Map.of("a", "b"))), false);
        }
    };

    private static GestionDesPayeurs.FicheDePayeur fiche(Niveau niveau, String code) {
        return switch (niveau) {
            case CAISSE -> new GestionDesPayeurs.FicheDePayeur(ID, code, "Nom", "VACANCIER", null, null, null);
            case AGENCE -> new GestionDesPayeurs.FicheDePayeur(ID, code, "Nom", null, null, PARENT, "CNAS · Caisse");
            case CENTRE_PAYEUR -> new GestionDesPayeurs.FicheDePayeur(ID, code, "Nom", null, "Rue", PARENT, "AG-1 · Agence");
        };
    }

    private final PayeursParPatientCouverture acl = new PayeursParPatientCouverture(payeurs);

    @Test
    void elle_prend_les_caisses_les_agences_et_les_centres_payeurs_et_rien_d_autre() {
        assertThat(acl.prend(ReferentielsDePatientCouverture.CAISSE)).isTrue();
        assertThat(acl.prend(ReferentielsDePatientCouverture.AGENCE)).isTrue();
        assertThat(acl.prend(ReferentielsDePatientCouverture.CENTRE_PAYEUR)).isTrue();
        assertThat(acl.prend(ReferentielsDePatientCouverture.FORFAIT)).isFalse();
        assertThat(acl.prend(ReferentielsDePatientCouverture.TRANSPORTEUR)).isFalse();
        assertThat(acl.prend(ReferentialKind.SALLE)).isFalse();
    }

    @Test
    void une_caisse_est_rendue_avec_son_code_son_nom_et_son_type_sans_reference() {
        ReferentialEntry entree = acl.create(CENTRE, ReferentielsDePatientCouverture.CAISSE, Map.of("code", "CNAS", "nom", "Caisse", "typeCaisse", "x"));

        assertThat(entree.id()).isEqualTo(ID);
        assertThat(entree.values()).containsOnlyKeys("code", "nom", "typeCaisse").containsEntry("typeCaisse", "VACANCIER");
        assertThat(entree.references()).isEmpty();
        assertThat(appels).containsExactly("creer:CAISSE:CNAS:Caisse:x:null:null");
    }

    @Test
    void une_agence_est_rendue_avec_l_identifiant_de_sa_caisse_et_son_libelle_de_reference() {
        ReferentialEntry entree = acl.update(CENTRE, ReferentielsDePatientCouverture.AGENCE, ID, Map.of("code", "AG-1", "nom", "Agence", "caisse", "CNAS"));

        assertThat(entree.values()).containsOnlyKeys("code", "nom", "caisse").containsEntry("caisse", PARENT.toString());
        assertThat(entree.references()).containsExactly(Map.entry("caisse", "CNAS · Caisse"));
    }

    @Test
    void un_centre_payeur_est_rendu_avec_son_adresse_sa_agence_et_son_libelle_de_reference_et_l_adresse_absente_reste_nulle() {
        ReferentialEntry entree = acl.create(CENTRE, ReferentielsDePatientCouverture.CENTRE_PAYEUR,
                Map.of("code", "CP-1", "nom", "Centre", "adresse", "Rue", "agence", "AG-1"));

        assertThat(entree.values()).containsOnlyKeys("code", "nom", "adresse", "agence").containsEntry("adresse", "Rue")
                .containsEntry("agence", PARENT.toString());
        assertThat(entree.references()).containsExactly(Map.entry("agence", "AG-1 · Agence"));
        assertThat(appels).containsExactly("creer:CENTRE_PAYEUR:CP-1:Centre:null:Rue:AG-1");
    }

    @Test
    void la_liste_rend_les_valeurs_de_chaque_niveau() {
        var caisses = acl.list(CENTRE, ReferentielsDePatientCouverture.CAISSE, null, 0, 20);
        var centres = acl.list(CENTRE, ReferentielsDePatientCouverture.CENTRE_PAYEUR, "x", 1, 5);

        assertThat(caisses.items().getFirst().values()).containsEntry("code", "X").containsEntry("typeCaisse", "VACANCIER");
        assertThat(centres.items().getFirst().values()).containsEntry("adresse", "Rue");
        assertThat(centres.page()).isEqualTo(1);
        assertThat(centres.size()).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("payeurs")
    void un_payeur_deja_existant_est_une_anomalie_sur_le_code_avec_le_message_d_avant(ReferentialKind kind) {
        aLever = RefusDePayeur.dejaExistant("doublon");

        assertThatThrownBy(() -> acl.create(CENTRE, kind, Map.of("code", "HD")))
                .isInstanceOfSatisfying(ReferentialValidationException.class, e -> assertThat(e.getIssues())
                        .extracting(ValidationIssue::field, ValidationIssue::code, ValidationIssue::message)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("code", "ALREADY_EXISTS",
                                "Un élément de « " + kind.label() + " » avec ce code existe déjà.")));
    }

    @Test
    void une_saisie_invalide_rend_ses_anomalies_sans_ligne() {
        aLever = RefusDePayeur.saisieInvalide(List.of(
                new GestionDesPayeurs.Anomalie(0, "code", "REQUIRED", "« Code » est obligatoire.", Map.of())));

        assertThatThrownBy(() -> acl.create(CENTRE, ReferentielsDePatientCouverture.AGENCE, Map.of()))
                .isInstanceOfSatisfying(ReferentialValidationException.class, e -> assertThat(e.getIssues())
                        .extracting(ValidationIssue::row, ValidationIssue::field, ValidationIssue::code)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple(0, "code", "REQUIRED")));
    }

    @Test
    void un_payeur_introuvable_ou_utilise_est_une_erreur_metier_avec_le_message_d_avant() {
        aLever = RefusDePayeur.introuvable();
        assertThatThrownBy(() -> acl.delete(CENTRE, ReferentielsDePatientCouverture.CAISSE, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("REFERENTIAL_NOT_FOUND");
                    assertThat(e.getMessage()).isEqualTo("Élément introuvable dans « Caisses » pour ce centre.");
                });

        aLever = RefusDePayeur.utilise(4);
        assertThatThrownBy(() -> acl.delete(CENTRE, ReferentielsDePatientCouverture.CENTRE_PAYEUR, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("REFERENTIAL_IN_USE");
                    assertThat(e.getMessage()).isEqualTo("Suppression impossible : cet élément de « Centres payeurs » est encore utilisé "
                            + "(4 référence(s) : patients, prises en charge, séances ou autres référentiels).");
                });
        assertThat(appels).containsExactly("supprimer:CAISSE", "supprimer:CENTRE_PAYEUR");
    }

    @Test
    void l_import_traduit_les_lignes_du_fichier_par_niveau_et_rend_le_bilan() {
        ReferentialAdminDelegate.Outcome bilan = acl.importRows(CENTRE, ReferentielsDePatientCouverture.AGENCE,
                List.of(new ReferentialAdminDelegate.Row(2, Map.of("code", "AG", "nom", "x", "caisse", "CNAS"))), true);

        assertThat(appels).containsExactly("importer:AGENCE:1:false:CNAS");
        assertThat(bilan.toCreate()).isEqualTo(1);
        assertThat(bilan.toUpdate()).isEqualTo(2);
        assertThat(bilan.applied()).isFalse();
        assertThat(bilan.errors()).extracting(ValidationIssue::row, ValidationIssue::field, ValidationIssue::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3, "caisse", "REFERENCE_NOT_FOUND"));
    }
}
