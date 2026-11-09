package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.RapprochementDesAlertes.Rapprochement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R2 option (a) : l'identité d'une alerte ouverte est (centre, code), sans jour ; rien n'est retouché tant qu'elle persiste (KB-DIR-04). */
class RapprochementDesAlertesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static AlerteLevee levee(UUID centre, String code) {
        return new AlerteLevee(centre, "Centre", code, GraviteDAlerte.CRITICAL, BigDecimal.ONE);
    }

    private static AlerteOuverte ouverte(String id, UUID centre, String code) {
        return new AlerteOuverte(UUID.fromString("10000000-0000-0000-0000-00000000000" + id), new CleDAlerte(centre, code));
    }

    @Test
    void une_alerte_levee_sans_ligne_ouverte_est_ouverte() {
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(), List.of(levee(A, "STOCK_SOUS_SEUIL")));
        assertEquals(List.of(levee(A, "STOCK_SOUS_SEUIL")), r.aOuvrir());
        assertTrue(r.aResoudre().isEmpty());
    }

    @Test
    void une_alerte_deja_ouverte_n_est_ni_rouverte_ni_retouchee_meme_si_sa_valeur_a_change() {
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(ouverte("1", A, "STOCK_SOUS_SEUIL")),
                List.of(new AlerteLevee(A, "Centre renomme", "STOCK_SOUS_SEUIL", GraviteDAlerte.WARNING, BigDecimal.TEN)));
        assertTrue(r.aOuvrir().isEmpty());
        assertTrue(r.aResoudre().isEmpty());
    }

    @Test
    void une_alerte_qui_n_est_plus_levee_est_resolue() {
        AlerteOuverte o = ouverte("1", A, "LOTS_PERIMES");
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(o), List.of());
        assertEquals(List.of(o.id()), r.aResoudre());
    }

    @Test
    void la_cle_est_le_centre_et_le_code_un_autre_centre_ou_un_autre_code_est_une_autre_alerte() {
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(ouverte("1", A, "STOCK_SOUS_SEUIL")),
                List.of(levee(A, "STOCK_SOUS_SEUIL"), levee(A, "LOTS_PERIMES"), levee(B, "STOCK_SOUS_SEUIL")));
        assertEquals(List.of(levee(A, "LOTS_PERIMES"), levee(B, "STOCK_SOUS_SEUIL")), r.aOuvrir());
        assertTrue(r.aResoudre().isEmpty());
    }

    @Test
    void les_resolutions_gardent_l_ordre_de_l_historique_et_une_cle_encore_levee_n_est_pas_resolue() {
        AlerteOuverte o1 = ouverte("1", A, "LOTS_PERIMES");
        AlerteOuverte o2 = ouverte("2", A, "STOCK_SOUS_SEUIL");
        AlerteOuverte o3 = ouverte("3", B, "HB_HORS_CIBLE");
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(o1, o2, o3), List.of(levee(A, "STOCK_SOUS_SEUIL")));
        assertEquals(List.of(o1.id(), o3.id()), r.aResoudre());
    }

    @Test
    void la_reapparition_apres_resolution_est_une_nouvelle_ligne() {
        // la ligne résolue n'est plus « ouverte » : elle n'est pas fournie au rapprochement
        Rapprochement r = RapprochementDesAlertes.rapprocher(List.of(), List.of(levee(B, "STOCK_SOUS_SEUIL")));
        assertEquals(1, r.aOuvrir().size());
    }
}
