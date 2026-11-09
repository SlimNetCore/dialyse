package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repris de {@code DashboardDiffTest} (plateforme, retiré) : la détection de changement entre deux constats. */
class ConstatDesIndicateursTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static ConstatDesIndicateurs constat() {
        return new ConstatDesIndicateurs();
    }

    @Test
    void deux_constats_identiques_ne_produisent_aucun_changement() {
        ConstatDesIndicateurs avant = constat().retenir(A, "A", ConstatDesIndicateurs.SEANCES, "seances", 12);
        assertTrue(constat().retenir(A, "A", ConstatDesIndicateurs.SEANCES, "seances", 12).changementsDepuis(avant).isEmpty());
    }

    @Test
    void des_valeurs_numeriquement_egales_ne_sont_pas_un_changement() {
        ConstatDesIndicateurs avant = constat().retenir(A, "A", ConstatDesIndicateurs.FINANCE, "caTtc", new BigDecimal("100.0"));
        ConstatDesIndicateurs apres = constat().retenir(A, "A", ConstatDesIndicateurs.FINANCE, "caTtc", new BigDecimal("100.00"));
        assertTrue(apres.changementsDepuis(avant).isEmpty());
    }

    @Test
    void chaque_indicateur_modifie_est_rapporte_avec_sa_valeur_avant_et_apres() {
        ConstatDesIndicateurs avant = constat().retenir(A, "A", ConstatDesIndicateurs.SEANCES, "seances", 12)
                .retenir(B, "B", ConstatDesIndicateurs.FINANCE, "caTtc", new BigDecimal("500"));
        ConstatDesIndicateurs apres = constat().retenir(A, "A", ConstatDesIndicateurs.SEANCES, "seances", 13)
                .retenir(B, "B", ConstatDesIndicateurs.FINANCE, "caTtc", new BigDecimal("500"));
        List<Changement> changements = apres.changementsDepuis(avant);
        assertEquals(1, changements.size());
        assertEquals("seances", changements.get(0).nom());
        assertEquals(new BigDecimal("12"), changements.get(0).avant());
        assertEquals(new BigDecimal("13"), changements.get(0).apres());
        assertEquals("A", changements.get(0).centre());
        assertEquals(ConstatDesIndicateurs.SEANCES, changements.get(0).famille());
    }

    @Test
    void un_indicateur_masque_qui_devient_public_n_est_jamais_rapporte_dans_aucun_sens() {
        ConstatDesIndicateurs masque = constat();
        ConstatDesIndicateurs public_ = constat().retenir(A, "A", ConstatDesIndicateurs.PATIENTS, "patients", 5);
        assertTrue(public_.changementsDepuis(masque).isEmpty());
        assertTrue(masque.changementsDepuis(public_).isEmpty());
    }

    @Test
    void une_valeur_absente_n_est_pas_retenue() {
        assertEquals(0, constat().retenir(A, "A", ConstatDesIndicateurs.PATIENTS, "patients", null).taille());
    }

    @Test
    void une_alerte_apparue_ou_disparue_est_rapportee_contre_zero() {
        ConstatDesIndicateurs avant = constat().retenir(A, "A", ConstatDesIndicateurs.ALERTES, "LOTS_PERIMES", 1);
        ConstatDesIndicateurs apres = constat().retenir(A, "A", ConstatDesIndicateurs.ALERTES, "STOCK_SOUS_SEUIL", 2);
        List<Changement> changements = apres.changementsDepuis(avant);
        assertEquals(2, changements.size());
        Changement levee = changements.stream().filter(c -> c.nom().equals("STOCK_SOUS_SEUIL")).findFirst().orElseThrow();
        Changement resolue = changements.stream().filter(c -> c.nom().equals("LOTS_PERIMES")).findFirst().orElseThrow();
        assertEquals(0, levee.avant().signum());
        assertEquals(new BigDecimal("2"), levee.apres());
        assertEquals(new BigDecimal("1"), resolue.avant());
        assertEquals(0, resolue.apres().signum());
    }
}
