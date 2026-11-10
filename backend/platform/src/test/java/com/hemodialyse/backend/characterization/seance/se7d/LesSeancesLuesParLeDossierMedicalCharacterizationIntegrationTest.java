package com.hemodialyse.backend.characterization.seance.se7d;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7d lot d3 : le lecteur SQL de {@code seances} du Dossier médical ({@code HistoriqueDesConstantesJdbc} : total puis page, jointure
 * {@code volet_paramedical} sur la séance ET le centre, tri date décroissante, création décroissante, identifiant décroissant), observé
 * par {@code GET /api/v1/patients/{id}/constantes}. Complète DM1 sur les égalités complètes (même date, même création), la création
 * nulle (colonne {@code seances.created_at} nullable), et le total quand la page coupe une égalité.
 * <p>
 * Lot L5a (2026-11-10, ADR 0117, KB-SEA-79 corrigé) : la création nulle se classe EN PREMIER en tri décroissant (NULLS FIRST écrit), sur H2 comme
 * sur PostgreSQL 16 ; l'ancien golden {@code creation_nulle_sur_h2} (H2 : en dernier) est archivé sous {@code _ecarts-legacy/dossier-medical}.
 * Le rejeu PostgreSQL 16 est fait par {@code backend/scripts/seance-se7d-pg16-rejeu.sh}.
 */
class LesSeancesLuesParLeDossierMedicalCharacterizationIntegrationTest extends Se7dCharacterizationSupport {

    @Override
    protected String dossier() {
        return "dossier-medical";
    }

    /** Ordre servi : alias des séances (les identifiants sont nommés par le harnais). */
    private List<String> ordre(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode i : mapper.readTree(r.raw()).path("items")) {
            l.add(aliasNommes.getOrDefault(i.path("seanceId").asString(), i.path("seanceId").asString()));
        }
        return l;
    }

    private Map<String, Object> page(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        JsonNode c = mapper.readTree(r.raw());
        m.put("total", c.path("total"));
        m.put("page", c.path("page"));
        m.put("size", c.path("size"));
        m.put("ordre", ordre(r));
        return m;
    }

    @Test
    void a_date_et_creation_egales_l_identifiant_decroissant_departage_et_la_page_coupe_l_egalite() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "P1");
        // trois séances le même jour, même création (CREATION_FIXE) : seul l'identifiant les départage
        List<String> poses = new ArrayList<>();
        for (String alias : List.of("EGAL_A", "EGAL_B", "EGAL_C")) {
            UUID id = seanceEnBase(CENTRE_A, patient, alias, fixe(5), "VALIDEE");
            poses.add(alias + "=" + id.toString().substring(0, 8));
        }
        seanceEnBase(CENTRE_A, patient, "PLUS_RECENTE", fixe(6), "ANNULEE");
        seanceEnBase(CENTRE_A, patient, "PLUS_ANCIENNE", fixe(4), "CREE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identifiants poses (8 premiers caracteres, l'ordre attendu est decroissant)", poses);
        m.put("page 0 taille 20", page(constantesDuPatient(patient, null, as(CENTRE_A, "MEDECIN"))));
        m.put("page 0 taille 2", page(constantesDuPatient(patient, "page=0&size=2", as(CENTRE_A, "MEDECIN"))));
        m.put("page 1 taille 2", page(constantesDuPatient(patient, "page=1&size=2", as(CENTRE_A, "MEDECIN"))));
        m.put("page 2 taille 2", page(constantesDuPatient(patient, "page=2&size=2", as(CENTRE_A, "MEDECIN"))));
        arbre("egalite_complete_et_pagination", m);
    }

    @Test
    void une_creation_nulle_se_classe_en_premier_du_meme_jour_sur_toutes_les_bases() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "P1");
        UUID sansCreation = seanceEnBase(CENTRE_A, patient, "CREATION_NULLE", fixe(5), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "CREATION_FIXE", fixe(5), "SIGNEE");
        UUID plusTard = seanceEnBase(CENTRE_A, patient, "CREATION_PLUS_TARD", fixe(5), "VALIDEE");
        jdbc.update("UPDATE seances SET created_at = NULL WHERE id = ?", sansCreation);
        jdbc.update("UPDATE seances SET created_at = ? WHERE id = ?", CREATION_FIXE.plusHours(1), plusTard);
        voletParamedicalComplet(CENTRE_A, sansCreation, "70.5", "68.0", "13/8", "12/7", 240, "2500");

        Resp r = constantesDuPatient(patient, null, as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("H2 comme PostgreSQL 16 (NULLS FIRST explicite en tri decroissant, KB-SEA-79 corrige)", page(r));
        List<String> mesures = new ArrayList<>();
        for (JsonNode i : mapper.readTree(r.raw()).path("items")) {
            mesures.add(aliasNommes.getOrDefault(i.path("seanceId").asString(), "?") + " poidsAvant=" + i.path("poidsAvantKg")
                    + " taAvant=" + i.path("taAvant") + " duree=" + i.path("dureeMinutes") + " uf=" + i.path("ultrafiltrationMl"));
        }
        m.put("mesures (le volet est joint malgre la creation nulle)", mesures);
        arbre("creation_nulle_en_premier_nouveau_code", m);
    }

    @Test
    void le_total_compte_toutes_les_seances_du_patient_dans_le_centre_quel_que_soit_le_statut() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "P1");
        String[] statuts = {"VALIDEE", "validee", "ABSENT", "ANNULEE", "BROUILLON", "FACTUREE"};
        for (int i = 0; i < statuts.length; i++) {
            seanceEnBase(CENTRE_A, patient, "S" + i, fixe(10 + i), statuts[i]);
        }
        // même patient (même identifiant) posé dans le centre B par une séance : ni comptée ni lue pour le centre A
        seanceEnBase(CENTRE_B, patient, "S_CENTRE_B", fixe(20), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A, taille 3", page(constantesDuPatient(patient, "size=3", as(CENTRE_A, "MEDECIN"))));
        m.put("centre B (le patient n'y a pas de fiche)", page(constantesDuPatient(patient, null, as(CENTRE_B, "MEDECIN"))));
        arbre("total_tous_statuts_et_centre", m);
    }
}
