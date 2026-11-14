package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la numérotation des documents (S-DDL2, ADR 0185) : le double en mémoire et l'adapter JDBC (H2, PostgreSQL 16) passent ce
 * même jeu de tests. Comportement repris du Stock et du Dossier médical à l'identique.
 */
public abstract class NumerotationDesDocumentsContrat {

    protected static final UUID CENTRE_A = UUID.fromString("99999a00-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99999a00-0000-0000-0000-00000000000b");

    protected abstract NumerotationDesDocuments numerotation();

    /** Pose un compteur existant (ligne déjà présente pour le centre et le type). */
    protected abstract void precharger(UUID centre, String type, String prefixe, long compteur);

    @Test
    void le_premier_numero_d_un_centre_demarre_a_un_avec_le_prefixe_par_defaut() {
        assertThat(numerotation().suivant(CENTRE_A, "SEQ_BC", "BC-")).isEqualTo("BC-00001");
    }

    @Test
    void les_numeros_d_un_meme_type_et_d_un_meme_centre_se_suivent_sans_trou() {
        var numerotation = numerotation();
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BR", "BR-")).isEqualTo("BR-00001");
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BR", "BR-")).isEqualTo("BR-00002");
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BR", "BR-")).isEqualTo("BR-00003");
    }

    @Test
    void chaque_centre_et_chaque_type_ont_leur_propre_compteur() {
        var numerotation = numerotation();
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BS", "BS-")).isEqualTo("BS-00001");
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_BS", "BS-")).isEqualTo("BS-00002");
        assertThat(numerotation.suivant(CENTRE_B, "SEQ_BS", "BS-")).isEqualTo("BS-00001");
        assertThat(numerotation.suivant(CENTRE_A, "SEQ_ORD", "ORD-")).isEqualTo("ORD-00001");
    }

    @Test
    void un_compteur_existant_est_poursuivi_avec_son_prefixe_enregistre() {
        precharger(CENTRE_A, "SEQ_BR", "BRX-", 6000);
        assertThat(numerotation().suivant(CENTRE_A, "SEQ_BR", "BR-")).isEqualTo("BRX-06001");
    }

    @Test
    void le_numero_garde_cinq_chiffres_au_moins_et_s_elargit_au_dela() {
        precharger(CENTRE_A, "INV", "INV-", 99999);
        var numerotation = numerotation();
        assertThat(numerotation.suivant(CENTRE_A, "INV", "INV-")).isEqualTo("INV-100000");
        assertThat(numerotation.suivant(CENTRE_A, "INV", "INV-")).isEqualTo("INV-100001");
    }
}
