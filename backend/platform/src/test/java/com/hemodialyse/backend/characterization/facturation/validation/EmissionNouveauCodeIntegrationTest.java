package com.hemodialyse.backend.characterization.facturation.validation;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Scénarios propres au nouveau code de la slice F3 (décisions du 2026-10-20), qui n'existaient pas dans le filet de
 * caractérisation du legacy : auteur de l'émission (KB-FAC-30) et refus atomique des séances sans forfait (KB-FAC-32).
 */
class EmissionNouveauCodeIntegrationTest extends ValidationCharacterizationSupport {

    private static final UUID AUTEUR = UUID.fromString("99998000-0000-0000-0000-00000000c001");

    private static RequestPostProcessor adminAuteur() {
        return user(UserPrincipal.create(AUTEUR.toString(), CENTRE_A.toString(), "auteur", "", List.of("ADMIN"), true));
    }

    @Override
    protected String theme() {
        return "nouveau_code";
    }

    @Test
    void l_utilisateur_de_la_session_est_enregistre_comme_auteur_de_l_emission() throws Exception {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");

        Resp reponse = doPost("/validate", mois(CENTRE_A, JANVIER, true), adminAuteur());

        assertEquals(200, reponse.status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE center_id = ? AND emise_par = ?", Integer.class,
                CENTRE_A, AUTEUR));
    }

    @Test
    void les_factures_deja_emises_avant_la_colonne_d_auteur_gardent_un_auteur_inconnu() {
        UUID ancienne = factureExistante(CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2025-12-01", "2025-12-31");

        assertNull(jdbc.queryForObject("SELECT emise_par FROM factures WHERE id = ?", UUID.class, ancienne));
    }

    @Test
    void une_seule_seance_sans_forfait_empeche_toute_emission_meme_des_patients_qui_ont_un_forfait() throws Exception {
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", P_CHIKHI);
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");

        golden("une_seance_sans_forfait_422", valider(mois(CENTRE_A, JANVIER, true)));

        goldenEtat("une_seance_sans_forfait_etat", CENTRE_A);
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void une_surcharge_de_forfait_leve_le_refus_des_seances_sans_prise_en_charge() throws Exception {
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", P_CHIKHI);
        UUID seance = seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");
        surcharge(seance, FORFAIT_PREMIUM, "Forfait HD Premium", "5200.00");

        golden("surcharge_leve_le_refus_200", valider(mois(CENTRE_A, JANVIER, true)));

        assertEquals(1, evenements.recus().size());
    }
}
