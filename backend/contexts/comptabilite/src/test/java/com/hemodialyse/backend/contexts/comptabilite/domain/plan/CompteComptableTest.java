package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompteComptableTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final NumeroDeCompte COMPTE = NumeroDeCompte.de("4112");

    private final UsagesEnMemoire usages = new UsagesEnMemoire();

    private CompteComptable ouvert() {
        return CompteComptable.ouvrir(CENTRE, COMPTE, "Clients", true, usages);
    }

    @Test
    void le_libelle_est_obligatoire() {
        for (String libelle : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> CompteComptable.ouvrir(CENTRE, COMPTE, libelle, true, usages))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Le libellé du compte est obligatoire");
        }
    }

    @Test
    void le_libelle_est_rogne_et_limite_a_150_caracteres() {
        assertThat(CompteComptable.ouvrir(CENTRE, COMPTE, "  Rogné  ", true, usages).libelle()).isEqualTo("Rogné");
        assertThat(CompteComptable.ouvrir(CENTRE, COMPTE, "é".repeat(150), true, usages).libelle()).hasSize(150);
        assertThatThrownBy(() -> CompteComptable.ouvrir(CENTRE, COMPTE, "é".repeat(151), true, usages))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le libellé du compte dépasse 150 caractères");
    }

    @Test
    void un_compte_libre_se_desactive_et_se_reactive() {
        CompteComptable compte = ouvert();

        compte.redefinir("Clients (ancien)", false, usages);
        assertThat(compte.actif()).isFalse();
        assertThat(compte.libelle()).isEqualTo("Clients (ancien)");

        compte.redefinir("Clients", true, usages);
        assertThat(compte.actif()).isTrue();
    }

    @Test
    void un_compte_choisi_par_le_parametrage_un_payeur_ou_un_modele_ne_se_desactive_pas() {
        usages.mapping("4112");
        CompteComptable compte = ouvert();

        assertThatThrownBy(() -> compte.redefinir("Clients", false, usages)).isInstanceOf(CompteUtilise.class)
                .hasMessage("Le compte 4112 est utilisé par le paramétrage, un payeur ou un modèle de pièce : "
                        + "remplacez-le avant de le désactiver")
                .extracting("code").isEqualTo("COMPTE_UTILISE");
        assertThat(compte.actif()).isTrue();
    }

    @Test
    void un_compte_utilise_garde_son_libelle_quand_la_desactivation_est_refusee() {
        usages.utiliseParUnPayeurOuUnModele("4112");
        CompteComptable compte = ouvert();

        assertThatThrownBy(() -> compte.redefinir("Autre libellé", false, usages)).isInstanceOf(CompteUtilise.class);
        assertThat(compte.libelle()).isEqualTo("Clients");
    }

    @Test
    void un_compte_utilise_peut_changer_de_libelle_tant_qu_il_reste_actif() {
        usages.mapping("4112");
        CompteComptable compte = ouvert();

        compte.redefinir("Clients patients", true, usages);

        assertThat(compte.libelle()).isEqualTo("Clients patients");
    }

    @Test
    void un_compte_rattache_a_un_article_ne_se_desactive_pas_ecart_decide_KB_CPT_13a() {
        usages.rattacheAUnArticle("4112");
        CompteComptable compte = ouvert();

        assertThatThrownBy(() -> compte.redefinir("Clients", false, usages)).isInstanceOf(CompteUtilise.class)
                .hasMessage("Le compte 4112 est rattaché à un article du stock : changez le compte de l'article avant de le désactiver")
                .extracting("code").isEqualTo("COMPTE_UTILISE");
    }

    @Test
    void ouvrir_un_compte_deja_utilise_directement_desactive_est_refuse() {
        usages.mapping("4112");

        assertThatThrownBy(() -> CompteComptable.ouvrir(CENTRE, COMPTE, "Clients", false, usages))
                .isInstanceOf(CompteUtilise.class);
    }

    @Test
    void un_compte_libre_se_supprime() {
        assertThatCode(() -> ouvert().exigerSuppressible(usages)).doesNotThrowAnyException();
    }

    @Test
    void un_compte_utilise_ne_se_supprime_pas_meme_s_il_porte_des_ecritures() {
        usages.utiliseParUnPayeurOuUnModele("4112").avecEcritures("4112");

        assertThatThrownBy(() -> ouvert().exigerSuppressible(usages)).isInstanceOf(CompteUtilise.class)
                .hasMessageEndingWith("avant de le supprimer");
    }

    @Test
    void un_compte_rattache_a_un_article_ne_se_supprime_pas() {
        usages.rattacheAUnArticle("4112");

        assertThatThrownBy(() -> ouvert().exigerSuppressible(usages)).isInstanceOf(CompteUtilise.class)
                .hasMessageEndingWith("avant de le supprimer");
    }

    @Test
    void un_compte_qui_porte_des_ecritures_se_desactive_mais_ne_se_supprime_pas() {
        usages.avecEcritures("4112");
        CompteComptable compte = ouvert();

        compte.redefinir("Clients", false, usages);
        assertThat(compte.actif()).isFalse();
        assertThatThrownBy(() -> compte.exigerSuppressible(usages)).isInstanceOf(CompteAvecEcritures.class)
                .hasMessage("Le compte 4112 porte des écritures : désactivez-le au lieu de le supprimer")
                .extracting("code").isEqualTo("COMPTE_AVEC_ECRITURES");
    }
}
