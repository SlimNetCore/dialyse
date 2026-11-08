package com.hemodialyse.backend.characterization.seance.se5;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE5, lot 2 : le forfait courant d'une séance tel que le lisent le détail ({@code GET /api/v1/seances/{id}}) et la liste
 * ({@code GET /api/v1/seances}) : priorité surcharge > prise en charge effective > prise en charge demandée (RG-FAC-021), période
 * couvrant le jour de la séance, repli sur la plus récente, statut de la prise en charge, cloisonnement des forfaits par centre.
 */
class LeForfaitCourantDUneSeanceCharacterizationIntegrationTest extends Se5CharacterizationSupport {

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC);

    @Override
    protected String dossier() {
        return "forfait_courant";
    }

    private Object forfaitDuDetail(UUID seance) throws Exception {
        return corps(doGet(detailApi(seance, CENTRE_A), adminA())).path("forfait");
    }

    @Test
    void la_priorite_est_la_surcharge_puis_le_forfait_effectif_puis_le_forfait_demande() throws Exception {
        UUID demande = forfaitSe5(CENTRE_A, "DEMANDE", "100.00");
        UUID effectif = forfaitSe5(CENTRE_A, "EFFECTIF", "120.00");
        UUID surcharge = forfaitSe5(CENTRE_A, "SURCHARGE", "150.00");
        UUID seulementDemande = patientSe1(CENTRE_A, "PAT_DEMANDE");
        pecSe5(CENTRE_A, seulementDemande, "VALIDEE", demande, "2020-01-01", "2099-12-31", null, null, null, T1);
        UUID lesDeux = patientSe1(CENTRE_A, "PAT_DEUX");
        pecSe5(CENTRE_A, lesDeux, "VALIDEE", demande, "2020-01-01", "2099-12-31", effectif, "2020-01-01", "2099-12-31", T1);
        UUID s1 = seanceEnBase(CENTRE_A, seulementDemande, "S_DEMANDE", fixe(1), "CREE");
        UUID s2 = seanceEnBase(CENTRE_A, lesDeux, "S_DEUX", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seule la PEC demandee porte un forfait", forfaitDuDetail(s1));
        m.put("02 PEC avec forfait demande et forfait effectif : l'effectif", forfaitDuDetail(s2));
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'SE5-SURCHARGE', forfait_override_nom = 'Forfait SURCHARGE', "
                + "forfait_override_prix = 150.00, forfait_override_updated_at = ?, forfait_override_updated_by = 'compta-01' WHERE id = ?",
                surcharge, T1, s2);
        m.put("03 la surcharge prime sur l'effectif", forfaitDuDetail(s2));
        m.put("04 la surcharge de s2 ne touche pas s1", forfaitDuDetail(s1));
        arbre("priorite", m);
    }

    @Test
    void la_prise_en_charge_qui_couvre_le_jour_prime_sinon_la_plus_recente_qui_porte_un_forfait() throws Exception {
        UUID ancien = forfaitSe5(CENTRE_A, "ANCIEN", "80.00");
        UUID recent = forfaitSe5(CENTRE_A, "RECENT", "90.00");
        UUID courant = forfaitSe5(CENTRE_A, "COURANT", "110.00");
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        pecSe5(CENTRE_A, patient, "VALIDEE", ancien, "2020-01-01", "2020-12-31", ancien, "2020-01-01", "2020-12-31", T1);
        pecSe5(CENTRE_A, patient, "VALIDEE", recent, "2021-01-01", "2021-12-31", recent, "2021-01-01", "2021-12-31", T2);
        UUID dedans = seanceEnBase(CENTRE_A, patient, "S_DEDANS", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 aucune PEC ne couvre 2026 : la plus recente creee (RECENT)", forfaitDuDetail(dedans));
        pecSe5(CENTRE_A, patient, "VALIDEE", courant, "2026-01-01", "2026-12-31", null, null, null, T1);
        m.put("02 une PEC demandee couvre 2026 : COURANT, meme si elle est plus ancienne", forfaitDuDetail(dedans));
        UUID avant = seanceEnBase(CENTRE_A, patient, "S_AVANT", fixe(-30), "CREE");
        m.put("03 une seance avant la periode courante : repli sur la plus recente (RECENT)", forfaitDuDetail(avant));
        pecSe5(CENTRE_A, patient, "VALIDEE", ancien, "2026-01-01", null, recent, "2025-12-01", null, T1);
        m.put("04 PEC ouverte (sans fin) dont l'effectif couvre : l'effectif (RECENT) devant la demande", forfaitDuDetail(dedans));
        arbre("periode_et_repli", m);
    }

    @Test
    void le_forfait_effectif_est_prefere_dans_le_repli_puis_la_pec_la_plus_recente() throws Exception {
        UUID a = forfaitSe5(CENTRE_A, "A", "10.00");
        UUID b = forfaitSe5(CENTRE_A, "B", "20.00");
        UUID c = forfaitSe5(CENTRE_A, "C", "30.00");
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        pecSe5(CENTRE_A, patient, "VALIDEE", a, "2020-01-01", "2020-12-31", null, null, null, T2);
        pecSe5(CENTRE_A, patient, "VALIDEE", b, "2021-01-01", "2021-12-31", c, "2021-01-01", "2021-12-31", T1);
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 repli : l'effectif (C, creee plus tot) devant la demandee plus recente (A)", forfaitDuDetail(seance));
        arbre("repli_effectif_d_abord", m);
    }

    @Test
    void sans_aucune_prise_en_charge_porteuse_de_forfait_le_forfait_est_nul() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 aucune PEC", forfaitDuDetail(seance));
        pecSe1(CENTRE_A, patient, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", false);
        m.put("02 PEC sans forfait", forfaitDuDetail(seance));
        arbre("aucun_forfait", m);
    }

    @Test
    void le_statut_de_la_prise_en_charge_n_intervient_pas_dans_le_forfait_courant() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : java.util.List.of("VALIDEE", "EN_ATTENTE", "REFUSEE", "ANNULEE", "BROUILLON", "INCONNU")) {
            nettoyerSe1();
            UUID forfait = forfaitSe5(CENTRE_A, "F", "75.00");
            UUID patient = patientSe1(CENTRE_A, "PAT_A");
            pecSe5(CENTRE_A, patient, statut, forfait, "2020-01-01", "2099-12-31", null, null, null, T1);
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
            m.put(statut, forfaitDuDetail(seance));
        }
        arbre("statut_de_la_pec_ignore", m);
    }

    @Test
    void un_forfait_d_un_autre_centre_n_est_jamais_porte_par_une_pec_et_la_surcharge_est_lue_par_centre() throws Exception {
        UUID forfaitB = forfaitSe5(CENTRE_B, "F_B", "300.00");
        UUID forfaitA = forfaitSe5(CENTRE_A, "F_A", "100.00");
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        pecSe5(CENTRE_A, patient, "VALIDEE", forfaitB, "2020-01-01", "2099-12-31", null, null, null, T1);
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 PEC du centre A pointant un forfait du centre B : ignoree", forfaitDuDetail(seance));
        sansPec(patient);
        pecSe5(CENTRE_A, patient, "VALIDEE", forfaitA, "2020-01-01", "2099-12-31", null, null, null, T1);
        m.put("02 PEC valide", forfaitDuDetail(seance));
        m.put("03 liste du centre A : une ligne, le forfait", corps(doGet(SEANCES_API + "?centerId=" + CENTRE_A, adminA())).path("items").get(0)
                .path("forfait"));
        arbre("cloisonnement", m);
    }
}
