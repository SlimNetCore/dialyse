package com.hemodialyse.backend.characterization.seance.se7e;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7e lot e3 : les deux lectures SQL de {@code seances} du cahier de dialyse, observées par
 * {@code GET /api/v1/cahier-dialyse/{patientId}/imprimer} (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; centre de la session) :
 * <ul>
 *   <li>{@code CahierDialyseReportService.nombreSeances} ({@code :84}) : compte préalable, {@code CAHIER_VIDE} s'il est nul ;</li>
 *   <li>l'état Jasper {@code reports/cahier_dialyse.jrxml} ({@code :95-135}) : une page par séance VALIDEE, SIGNEE ou FACTUREE de la
 *   période (bornes incluses ; sans borne : 1900-01-01 à 2999-12-31), triée par date décroissante puis identifiant de séance.</li>
 * </ul>
 * Le PDF est décodé en texte (PDFBox) ; seules les lignes qui portent la séance (titre de page, statut, forfait, poids) sont gardées ; la
 * date d'édition est masquée. Complète {@code CahierDialyseReportIntegrationTest} (état Jasper rempli directement, sans HTTP) sur les bornes,
 * la casse, l'égalité de date, le centre, la surcharge de forfait.
 * <p>Lot L5a (2026-11-10, ADR 0117, KB-SEA-88) : le golden {@code surcharge_de_forfait_nouveau_code} remplace {@code surcharge_de_forfait}
 * (archivé sous {@code _ecarts-legacy/cahier/surcharge_de_forfait_avant_l5a.legacy.json}) : une surcharge sans code recopié imprime le code et le
 * libellé du forfait du référentiel (même centre) au lieu de « Forfait : - » ; le prix reste celui de la surcharge.
 */
class LeCahierDeDialyseImprimeCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    @Override
    protected String dossier() {
        return "cahier";
    }

    private Map<String, Object> cahier(UUID patient, LocalDate du, LocalDate au) throws Exception {
        String parametres = "?tz=UTC" + (du == null ? "" : "&from=" + du) + (au == null ? "" : "&to=" + au);
        MockHttpServletResponse r = binaire("/api/v1/cahier-dialyse/" + patient + "/imprimer" + parametres, infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.getStatus() != 200) {
            m.putAll(refusOuStatut(r.getStatus(), r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
            return m;
        }
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("contentDisposition", r.getHeader("Content-Disposition"));
        List<String> lignes = new ArrayList<>();
        for (String ligne : texteNormalise(r.getContentAsByteArray())) {
            if (ligne.contains("Séance d'hémodialyse") || ligne.contains("Statut") || ligne.contains("Forfait") || ligne.contains("Poids avant")
                    || ligne.startsWith("pages=")) {
                lignes.add(ligne);
            }
        }
        m.put("lignes des seances", lignes);
        return m;
    }

    @Test
    void le_cahier_n_imprime_que_les_seances_validees_signees_ou_facturees_de_la_periode_la_plus_recente_d_abord() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PCD");
        UUID c1 = seanceEnBase(CENTRE_A, patient, "C1_DEBUT_VALIDEE", fixe(31), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "C2_MEME_JOUR_SIGNEE", fixe(40), "SIGNEE");
        seanceEnBase(CENTRE_A, patient, "C3_MEME_JOUR_FACTUREE", fixe(40), "FACTUREE");
        seanceEnBase(CENTRE_A, patient, "C4_MINUSCULES", fixe(45), "validee");
        seanceEnBase(CENTRE_A, patient, "C5_ANNULEE", fixe(46), "ANNULEE");
        seanceEnBase(CENTRE_A, patient, "C6_FIN_VALIDEE", fixe(58), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "C7_LENDEMAIN", fixe(59), "VALIDEE");
        seanceEnBase(CENTRE_B, patient, "C8_CENTRE_B", fixe(41), "VALIDEE");
        voletParamedical(CENTRE_A, c1, "72.50", "70.00", "13/8", "2500");

        Map<String, Object> m = new LinkedHashMap<>();
        List<String> ordre = new ArrayList<>();
        for (String alias : List.of("C2_MEME_JOUR_SIGNEE", "C3_MEME_JOUR_FACTUREE")) {
            UUID id = UUID.nameUUIDFromBytes(("se1-seance-" + alias + "-" + CENTRE_A).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ordre.add(alias + "=" + id.toString().substring(0, 8));
        }
        m.put("identifiants des deux seances du meme jour (8 premiers caracteres : l'ordre imprime est croissant)", ordre);
        m.put("periode D+31 a D+58", cahier(patient, fixe(31), fixe(58)));
        m.put("sans periode", cahier(patient, null, null));
        arbre("statuts_bornes_et_egalite", m);
    }

    @Test
    void un_cahier_sans_seance_imprimable_du_centre_est_refuse() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PVIDE");
        seanceEnBase(CENTRE_A, patient, "V1_MINUSCULES", fixe(31), "validee");
        seanceEnBase(CENTRE_A, patient, "V2_BLANC_DE_FIN", fixe(32), "VALIDEE ");
        seanceEnBase(CENTRE_A, patient, "V3_ANNULEE", fixe(33), "ANNULEE");
        seanceEnBase(CENTRE_A, patient, "V4_ABSENT", fixe(34), "ABSENT");
        seanceEnBase(CENTRE_B, patient, "V5_CENTRE_B", fixe(35), "VALIDEE");
        UUID autre = patientSe1(CENTRE_B, "PB_CAHIER");
        seanceEnBase(CENTRE_B, autre, "V6_PATIENT_DU_CENTRE_B", fixe(35), "VALIDEE");
        UUID realise = patientCouvert(CENTRE_A, "PREALISE");
        seanceEnBase(CENTRE_A, realise, "V7_REALISEE", fixe(40), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seances du centre non imprimables (casse, blanc, ANNULEE, ABSENT) et une seance VALIDEE au centre B", cahier(patient, null, null));
        m.put("fiche du centre B demandee par une session du centre A", cahier(autre, null, null));
        m.put("periode sans seance (D+41 a D+59)", cahier(realise, fixe(41), fixe(59)));
        m.put("debut apres la fin", cahier(realise, fixe(41), fixe(40)));
        m.put("un seul jour (D+40)", cahier(realise, fixe(40), fixe(40)));
        arbre("refus", m);
    }

    @Test
    void la_surcharge_de_forfait_imprime_le_code_recopie_sur_la_seance_ou_a_defaut_celui_du_referentiel() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PSURCH");
        UUID surcharge = forfait(CENTRE_A, "FORFAIT_SURCHARGE", "SURCH", "150.00");
        UUID avecCode = seanceEnBase(CENTRE_A, patient, "F1_SURCHARGE_AVEC_CODE", fixe(31), "VALIDEE");
        UUID sansCode = seanceEnBase(CENTRE_A, patient, "F2_SURCHARGE_SANS_CODE", fixe(32), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "F3_FORFAIT_DE_LA_PEC", fixe(33), "VALIDEE");
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'SURCH', forfait_override_nom = 'Surcharge fictive', "
                + "forfait_override_prix = ? WHERE id = ?", surcharge, new BigDecimal("150.00"), avecCode);
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = NULL, forfait_override_nom = NULL, "
                + "forfait_override_prix = NULL WHERE id = ?", surcharge, sansCode);

        arbre("surcharge_de_forfait_nouveau_code", cahier(patient, null, null));
    }
}
