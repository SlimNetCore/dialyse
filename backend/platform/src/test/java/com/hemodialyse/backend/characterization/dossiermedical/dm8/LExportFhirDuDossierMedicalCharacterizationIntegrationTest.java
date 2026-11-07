package com.hemodialyse.backend.characterization.dossiermedical.dm8;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM8, lot 2 : export FHIR R4 du dossier médical ({@code GET /api/v1/patients/{id}/dossier-medical/export-fhir}) : bundle complet (ordre des
 * six familles de ressources), ressources détaillées, bundle réduit au patient, patient inconnu ou d'un autre centre, correspondances de
 * codes et de statuts (KB-MED-70), plafond de 500 observations et ordonnances, droits, audit, aucune écriture.
 */
class LExportFhirDuDossierMedicalCharacterizationIntegrationTest extends Dm8CharacterizationSupport {

    @Override
    protected String dossier() {
        return "fhir";
    }

    // ------------------------------------------------------------------ projections lisibles

    private String v(JsonNode ressource, String chemin) {
        JsonNode n = ressource;
        for (String segment : chemin.split("\\.")) {
            n = segment.matches("\\d+") ? n.path(Integer.parseInt(segment)) : n.path(segment);
        }
        return n.isMissingNode() || n.isNull() ? "-" : n.asString();
    }

    private String champs(JsonNode r, String... chemins) {
        List<String> l = new ArrayList<>();
        for (String c : chemins) l.add(v(r, c));
        return String.join(" | ", l);
    }

    private String ligne(JsonNode r) {
        String type = r.path("resourceType").asString();
        return type + " : " + switch (type) {
            case "Patient" -> champs(r, "id", "name.0.family", "name.0.given.0", "gender", "birthDate", "identifier.0.system", "identifier.0.value");
            case "Condition" -> champs(r, "id", "code.coding.0.system", "code.coding.0.code", "code.coding.0.display", "code.text",
                    "clinicalStatus.coding.0.code", "onsetDateTime", "abatementDateTime", "severity.text", "note.0.text");
            case "AllergyIntolerance" -> champs(r, "id", "code.coding.0.system", "code.coding.0.code", "code.text", "criticality",
                    "clinicalStatus.coding.0.system", "clinicalStatus.coding.0.code", "recordedDate", "reaction.0.manifestation.0.text");
            case "Observation" -> champs(r, "id", "status", "code.coding.0.system", "code.coding.0.code", "code.text", "valueQuantity.value",
                    "valueQuantity.unit", "valueString", "effectiveDateTime");
            case "MedicationRequest" -> champs(r, "id", "status", "intent", "medicationCodeableConcept.coding.0.system",
                    "medicationCodeableConcept.coding.0.code", "medicationCodeableConcept.text", "authoredOn", "requester.display",
                    "dosageInstruction.0.text", "dosageInstruction.0.route.text", "dosageInstruction.0.timing.repeat.duration",
                    "dispenseRequest.quantity.value");
            case "Procedure" -> champs(r, "id", "status", "code.text", "bodySite.0.text", "performedPeriod.start", "performedPeriod.end", "note.0.text");
            default -> r.toString();
        };
    }

    private List<String> lignes(JsonNode bundle) {
        List<String> l = new ArrayList<>();
        bundle.path("entry").forEach(e -> l.add(ligne(e.path("resource"))));
        return l;
    }

    private List<String> identifiants(JsonNode bundle) {
        List<String> l = new ArrayList<>();
        bundle.path("entry").forEach(e -> l.add(e.path("resource").path("resourceType").asString() + " " + e.path("resource").path("id").asString()));
        return l;
    }

    private JsonNode exporter(UUID patient) throws Exception {
        return corps(doGet(fhirApi(patient), medecinA()));
    }

    /** Un patient du centre A avec une ressource de chaque famille, des dates toutes différentes (l'ordre dans une famille est date décroissante). */
    private UUID patientComplet() {
        UUID p = patientDm8(CENTRE_A, "PAT_A", "F", fixe(-9000));
        antecedentEnBase(CENTRE_A, p, "ANT_ANCIEN", "CIM10", "N18.5", "Maladie renale chronique stade 5", null, fixe(10), null, "ACTIF", "Severe", "Note fictive");
        antecedentEnBase(CENTRE_A, p, "ANT_RECENT", null, null, null, "Hypertension arterielle fictive", fixe(50), fixe(80), "RESOLU", null, null);
        allergieEnBase(CENTRE_A, p, "ALG_ANCIENNE", "ATC", "J01CA04", "Amoxicilline", "MEDICAMENT", "HAUTE", "Urticaire fictive", fixe(20), "CONFIRMEE");
        allergieEnBase(CENTRE_A, p, "ALG_RECENTE", "LOCAL", "arachide", null, "ALIMENT", "BASSE", null, fixe(60), "SUSPECTEE");
        analyseEnBase(CENTRE_A, p, "ANALYSE_1", fixe(30), "hb_g_dl", "11.5", "ferritine_ng_ml", "300", "kt_v_mensuel", "1.4", "creatinine_mg_dl", "8");
        observationEnBase(CENTRE_A, p, "OBS_1", "LOINC", "718-7", "Hemoglobine fictive", "12.1", "g/dL", null, fixe(40), "FINAL");
        ordonnanceEnBase(CENTRE_A, p, "ORD_1", fixe(45), "SIGNEE", "medecin-fictif", 2);
        abordEnBase(CENTRE_A, p, "ABORD_1", "FAV", "GAUCHE", "Avant-bras", fixe(-60), null, true, "Complication fictive");
        abordEnBase(CENTRE_A, p, "ABORD_2", "KT_TUNNELISE", null, "Jugulaire", fixe(-10), fixe(5), false, null);
        return p;
    }

    @Test
    void le_bundle_complet_reprend_dans_l_ordre_le_patient_les_antecedents_les_allergies_les_analyses_les_observations_les_ordonnances_et_les_abords()
            throws Exception {
        UUID p = patientComplet();
        UUID autrePatient = patientDm8(CENTRE_A, "PAT_AUTRE");
        antecedentEnBase(CENTRE_A, autrePatient, "ANT_AUTRE_PATIENT", null, null, null, "Autre patient", fixe(99), null, "ACTIF", null, null);
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        antecedentEnBase(CENTRE_B, p, "ANT_AUTRE_CENTRE", null, null, null, "Autre centre meme patient", fixe(98), null, "ACTIF", null, null);
        antecedentEnBase(CENTRE_B, patientB, "ANT_B", null, null, null, "Patient du centre B", fixe(97), null, "ACTIF", null, null);
        String avant = empreinteLecteurs();
        Resp r = doGet(fhirApi(p), medecinA());
        JsonNode bundle = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut et type", r.status() + " " + r.contentType());
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 enveloppe", champs(bundle, "resourceType", "type", "total", "timestamp"));
        m.put("04 le nombre d'entrees est le total", bundle.path("entry").size() == bundle.path("total").asInt());
        m.put("05 cles de l'enveloppe", bundle.propertyNames().stream().toList());
        m.put("06 entrees dans l'ordre", identifiants(bundle));
        m.put("07 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("ordre", m);
    }

    @Test
    void les_ressources_detaillees_une_par_famille() throws Exception {
        UUID p = patientComplet();
        JsonNode bundle = exporter(p);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Integer> vus = new LinkedHashMap<>();
        for (JsonNode e : bundle.path("entry")) {
            JsonNode r = e.path("resource");
            String type = r.path("resourceType").asString();
            int rang = vus.merge(type, 1, Integer::sum);
            if (rang == 1) m.put(String.format("%02d %s (premiere du bundle)", m.size() + 1, type), r);
        }
        m.put("90 Observation issue d'un bilan d'analyses (ferritine) : cle d'identifiant composite", bundle.path("entry").get(6).path("resource"));
        m.put("91 MedicationRequest de la seconde ligne de l'ordonnance (sans posologie, voie, duree ni quantite)", bundle.path("entry").get(10).path("resource"));
        arbre("ressources", m);
    }

    @Test
    void un_patient_sans_donnees_donne_un_bundle_d_une_seule_ressource_sans_sexe_ni_naissance_declares() throws Exception {
        UUID p = patientDm8(CENTRE_A, "PAT_A", "M", null);
        UUID complet = patientDm8(CENTRE_A, "PAT_B", "F", fixe(-9000));
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doGet(fhirApi(p), medecinA());
        m.put("01 bundle d'un patient sans naissance ni donnees", reponse(r));
        m.put("02 bundle d'un patient avec sexe et naissance mais sans donnees", lignes(exporter(complet)));
        m.put("03 total", exporter(complet).path("total").asInt());
        arbre("vide", m);
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_n_est_pas_exportable() throws Exception {
        UUID patientA = patientComplet();
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient inconnu", reponse(doGet(fhirApi(idStable("patient", "inconnu")), medecinA())));
        m.put("02 patient du centre B exporte par la session A", reponse(doGet(fhirApi(patientB), medecinA())));
        m.put("03 patient du centre A exporte par la session B", reponse(doGet(fhirApi(patientA), medecinB())));
        m.put("04 session A avec centerId=B", reponse(doGet(fhirApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        m.put("05 session A avec son centerId", reponse(doGet(fhirApi(patientA) + "?centerId=" + CENTRE_A, medecinA())).get("status"));
        m.put("06 MEDECIN sans centre de session", reponse(doGet(fhirApi(patientA), as(null, "MEDECIN"))));
        m.put("07 SUPERADMIN + MEDECIN (session A) vise le patient du centre B", reponse(doGet(fhirApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        arbre("patient_inconnu_ou_autre_centre", m);
    }

    @Test
    void les_codes_des_antecedents_et_des_allergies_sont_recopies_sans_validation_et_les_statuts_sont_traduits() throws Exception {
        UUID p = patientDm8(CENTRE_A, "PAT_A", "F", fixe(-9000));
        antecedentEnBase(CENTRE_A, p, "ANT_CIM", "CIM10", "N18.5", "Maladie renale chronique stade 5", "Libelle libre ignore", fixe(10), null, "ACTIF", "Severe", "Note");
        antecedentEnBase(CENTRE_A, p, "ANT_CIM_SANS_AFFICHAGE", "CIM10", "I10", null, "Libelle libre de repli", fixe(11), fixe(12), "INACTIF", null, null);
        antecedentEnBase(CENTRE_A, p, "ANT_RESOLU", "CIM10", "E11", "Diabete fictif", null, fixe(13), fixe(14), "RESOLU", null, null);
        antecedentEnBase(CENTRE_A, p, "ANT_LOINC", "LOINC", "718-7", "Code LOINC en antecedent", null, fixe(15), null, "ACTIF", null, null);
        antecedentEnBase(CENTRE_A, p, "ANT_ATC", "ATC", "B03XA01", "Code ATC en antecedent", null, fixe(16), null, "ACTIF", null, null);
        antecedentEnBase(CENTRE_A, p, "ANT_LOCAL", "LOCAL", "CODE-LOCAL", null, null, fixe(17), null, "ACTIF", null, null);
        antecedentEnBase(CENTRE_A, p, "ANT_LIBRE", null, null, null, "Antecedent en texte libre", fixe(18), null, "ACTIF", null, "   ");
        allergieEnBase(CENTRE_A, p, "ALG_SUSPECTEE", "ATC", "J01CA04", "Amoxicilline", "MEDICAMENT", "HAUTE", "Urticaire fictive", fixe(20), "SUSPECTEE");
        allergieEnBase(CENTRE_A, p, "ALG_CONFIRMEE", "ATC", "J01CA04", "Amoxicilline", "MEDICAMENT", "BASSE", "", fixe(21), "CONFIRMEE");
        allergieEnBase(CENTRE_A, p, "ALG_REFUTEE", "LOCAL", "arachide", null, "ALIMENT", "HAUTE", "Choc fictif", fixe(22), "REFUTEE");
        allergieEnBase(CENTRE_A, p, "ALG_CIM", "CIM10", "T78.4", "Allergie CIM10", "ENVIRONNEMENT", "BASSE", null, fixe(23), "CONFIRMEE");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doGet(fhirApi(p), medecinA());
        m.put("01 statut", r.status());
        m.put("02 ressources (dans l'ordre du bundle, date decroissante dans chaque famille)", r.status() == 200 ? lignes(corps(r)) : refus(r));
        arbre("codes_antecedents_allergies", m);
    }

    @Test
    void les_statuts_des_observations_des_ordonnances_et_des_abords_et_le_sexe_du_patient_sont_traduits() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] sexes = {"M", "F", "MASCULIN", "FEMININ", "m", " f ", "X", "AUTRE", ""};
        int i = 0;
        for (String sexe : sexes) {
            nettoyerDm8();
            UUID p = patientDm8(CENTRE_A, "PAT_A", sexe, fixe(-9000));
            Resp r = doGet(fhirApi(p), medecinA());
            m.put(String.format("%02d sexe '%s'", ++i, sexe), r.status() == 200 ? v(corps(r).path("entry").get(0).path("resource"), "gender") : refus(r));
        }
        nettoyerDm8();
        UUID p = patientDm8(CENTRE_A, "PAT_B", "M", fixe(-9000));
        observationEnBase(CENTRE_A, p, "OBS_FINAL", "LOINC", "718-7", "Hemoglobine fictive", "12.1", "g/dL", null, fixe(40), "FINAL");
        observationEnBase(CENTRE_A, p, "OBS_PRELIMINAIRE", "LOINC", "2160-0", null, "8", "mg/dL", null, fixe(41), "PRELIMINAIRE");
        observationEnBase(CENTRE_A, p, "OBS_CORRIGE", "LOCAL", "TEXTE", "Resultat textuel", null, null, "Positif fictif", fixe(42), "CORRIGE");
        ordonnanceEnBase(CENTRE_A, p, "ORD_BROUILLON", fixe(50), "BROUILLON", null, 1);
        ordonnanceEnBase(CENTRE_A, p, "ORD_SIGNEE", fixe(51), "SIGNEE", "medecin-fictif", 2);
        ordonnanceEnBase(CENTRE_A, p, "ORD_IMPRIMEE", fixe(52), "IMPRIMEE", "medecin-fictif", 1);
        ordonnanceEnBase(CENTRE_A, p, "ORD_ANNULEE", fixe(53), "ANNULEE", "medecin-fictif", 1);
        ordonnanceEnBase(CENTRE_A, p, "ORD_SANS_LIGNE", fixe(54), "SIGNEE", "medecin-fictif", 0);
        ordonnanceEnBase(CENTRE_A, p, "ORD_ATC", fixe(55), "SIGNEE", "medecin-fictif", 1);
        jdbc.update("UPDATE lignes_ordonnance SET medicament_code_system = 'ATC', medicament_code = 'B03XA01', medicament_code_display = 'Erythropoietine' "
                + "WHERE ordonnance_id = ?", idStable("ordonnance", "ORD_ATC-" + CENTRE_A));
        abordEnBase(CENTRE_A, p, "ABORD_ACTIF", "FAV", "GAUCHE", "Avant-bras", fixe(-60), null, true, "Complication fictive");
        abordEnBase(CENTRE_A, p, "ABORD_TERMINE", "PTFE", "DROIT", null, fixe(-50), fixe(-20), false, null);
        abordEnBase(CENTRE_A, p, "ABORD_SANS_LIEU", "KT_AIGU", null, null, fixe(-40), null, true, "");
        abordEnBase(CENTRE_A, p, "ABORD_LOCALISATION_SEULE", "KT_TUNNELISE", null, "Jugulaire", fixe(-30), null, true, null);
        Resp r = doGet(fhirApi(p), medecinA());
        m.put("10 statut", r.status());
        m.put("11 ressources", r.status() == 200 ? lignes(corps(r)) : refus(r));
        arbre("statuts", m);
    }

    @Test
    void une_seule_ligne_illisible_empeche_tout_l_export() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] cas = {"observation numerique sans unite", "observation de statut inconnu", "antecedent de statut inconnu",
                "antecedent CIM-10 de code mal forme", "allergie de criticite inconnue", "allergie de statut inconnu",
                "allergie de systeme de codage inconnu", "ordonnance de statut inconnu", "ordonnance sans ligne de statut inconnu"};
        int i = 0;
        for (String cas1 : cas) {
            nettoyerDm8();
            UUID p = patientDm8(CENTRE_A, "PAT_A", "M", fixe(-9000));
            antecedentEnBase(CENTRE_A, p, "ANT_SAINE", null, null, null, "Antecedent sain", fixe(1), null, "ACTIF", null, null);
            switch (cas1) {
                case "observation numerique sans unite" -> observationEnBase(CENTRE_A, p, "OBS", "LOINC", "718-7", null, "10", "", null, fixe(2), "FINAL");
                case "observation de statut inconnu" -> observationEnBase(CENTRE_A, p, "OBS", "LOINC", "718-7", null, "10", "g/dL", null, fixe(2), "PERIME");
                case "antecedent de statut inconnu" -> antecedentEnBase(CENTRE_A, p, "ANT", null, null, null, "Antecedent", fixe(2), null, "BIZARRE", null, null);
                case "antecedent CIM-10 de code mal forme" -> antecedentEnBase(CENTRE_A, p, "ANT", "CIM10", "zzz", "x", null, fixe(2), null, "ACTIF", null, null);
                case "allergie de criticite inconnue" -> allergieEnBase(CENTRE_A, p, "ALG", "LOCAL", "x", null, "ALIMENT", "MOYENNE", null, fixe(2), "CONFIRMEE");
                case "allergie de statut inconnu" -> allergieEnBase(CENTRE_A, p, "ALG", "LOCAL", "x", null, "ALIMENT", "BASSE", null, fixe(2), "BIZARRE");
                case "allergie de systeme de codage inconnu" -> allergieEnBase(CENTRE_A, p, "ALG", "XXX", "x", null, "ALIMENT", "BASSE", null, fixe(2), "CONFIRMEE");
                case "ordonnance de statut inconnu" -> ordonnanceEnBase(CENTRE_A, p, "ORD", fixe(2), "BIZARRE", "medecin-fictif", 1);
                default -> ordonnanceEnBase(CENTRE_A, p, "ORD", fixe(2), "BIZARRE", "medecin-fictif", 0);
            }
            m.put(String.format("%02d %s", ++i, cas1), refus(doGet(fhirApi(p), medecinA())));
        }
        arbre("lignes_illisibles", m);
    }

    @Test
    void le_bundle_ne_reprend_que_les_cinq_cents_observations_et_ordonnances_les_plus_recentes() throws Exception {
        UUID p = patientDm8(CENTRE_A, "PAT_A", "M", fixe(-9000));
        for (int i = 1; i <= 501; i++) {
            observationEnBase(CENTRE_A, p, "O" + i, "LOINC", "718-7", null, "10", "g/dL", null, fixe(i), "FINAL");
            ordonnanceEnBase(CENTRE_A, p, "R" + i, fixe(i), "SIGNEE", "medecin-fictif", 1);
        }
        JsonNode bundle = exporter(p);
        List<String> observations = new ArrayList<>();
        List<String> medications = new ArrayList<>();
        bundle.path("entry").forEach(e -> {
            JsonNode r = e.path("resource");
            if ("Observation".equals(r.path("resourceType").asString())) observations.add(r.path("id").asString());
            if ("MedicationRequest".equals(r.path("resourceType").asString())) medications.add(r.path("authoredOn").asString());
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 total du bundle", bundle.path("total").asInt());
        m.put("02 observations exportees (501 en base)", observations.size());
        m.put("03 la plus ancienne observation est ecartee", !observations.contains(idStable("observation", "O1-" + CENTRE_A).toString()));
        m.put("04 la plus recente est conservee", observations.contains(idStable("observation", "O501-" + CENTRE_A).toString()));
        m.put("05 ordonnances exportees (501 en base)", medications.size());
        m.put("06 la plus ancienne ordonnance est ecartee", !medications.contains(fixe(1).toString()));
        m.put("07 la plus recente est conservee", medications.contains(fixe(501).toString()));
        arbre("plafond", m);
    }

    @Test
    void l_export_est_reserve_au_medecin_n_ecrit_rien_et_n_est_pas_journalise() throws Exception {
        UUID p = patientComplet();
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        String avant = empreinteLecteurs();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> profil : profilsDm8().entrySet()) {
            profils.put(profil.getKey(), statutEtCode(doGet(fhirApi(p) + "?centerId=" + CENTRE_A, profil.getValue())));
        }
        profils.put("anonyme", statutEtCode(appel("GET", fhirApi(p), null, null)));
        monterLAudit();
        doGet(fhirApi(p), medecinA());
        doGet(fhirApi(p), infirmierA());
        doGet(fhirApi(patientB), medecinA());
        doGet(fhirApi(idStable("patient", "inconnu")), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 profils", profils);
        m.put("02 aucune ecriture", empreinteLecteurs().equals(avant));
        m.put("03 journal d'audit (ni l'export, ni ses refus ne sont traces)", journalDAudit());
        arbre("droits", m);
    }
}
