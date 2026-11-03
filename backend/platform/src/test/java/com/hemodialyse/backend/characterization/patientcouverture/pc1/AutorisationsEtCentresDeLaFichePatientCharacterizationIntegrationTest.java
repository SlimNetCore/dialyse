package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * PC1 : droits d'accès (11 profils + anonyme) des lectures de la fiche et des écritures d'assuré, et garde de centre des lectures
 * (le centre demandé par le client est-il comparé à celui de la session ?). La matrice POST/PUT de la fiche patient (liste blanche de
 * rôles, garde de centre du corps) est verrouillée par {@code AutorisationsDuPlacementCharacterizationIntegrationTest} (P5).
 */
class AutorisationsEtCentresDeLaFichePatientCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    @Override
    protected String dossier() {
        return "autorisations";
    }

    private void assure(String numero, UUID centre) {
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, created_at) VALUES (?,?,?,?,?,?)", numero, centre,
                "Assure " + numero, "Prenom", "M", OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void matrice(String name, Function<RequestPostProcessor, Resp> appel) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            m.put(p.getKey(), statutEtCode(appel.apply(p.getValue())));
        }
        // rôles réels hors des onze profils (revue M1 : leurs droits sont ceux du legacy) et rôle personnalisé
        m.put("PHARMACIEN", statutEtCode(appel.apply(as(CENTRE_A, "PHARMACIEN"))));
        m.put("DIRECTEUR_ADMINISTRATIF", statutEtCode(appel.apply(as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF"))));
        m.put("role personnalise (CHEF_DE_SERVICE)", statutEtCode(appel.apply(as(CENTRE_A, "CHEF_DE_SERVICE"))));
        arbre(name, m);
    }

    private Resp sansExceptionAnonyme(java.util.concurrent.Callable<Resp> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void les_lectures_de_la_fiche_sont_ouvertes_a_tout_compte_de_centre_et_fermees_a_la_direction_et_au_superadmin() throws Exception {
        UUID id = patientSansPlace("P1");
        assure("PC1-AS-1", CENTRE_A);
        String c = "{'centerId':'" + CENTRE_A + "','page':0,'size':10}";
        Map<String, Function<RequestPostProcessor, Resp>> routes = new LinkedHashMap<>();
        routes.put("GET fiche", p -> sansExceptionAnonyme(() -> doGet(PATIENTS + "/" + id + "?centerId=" + CENTRE_A, p)));
        routes.put("POST search", p -> sansExceptionAnonyme(() -> doPost(PATIENTS + "/search", json(c), p)));
        routes.put("GET summary", p -> sansExceptionAnonyme(() -> doGet(PATIENTS + "/summary?centerId=" + CENTRE_A, p)));
        routes.put("GET summary/details", p -> sansExceptionAnonyme(() -> doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_A, p)));
        routes.put("GET assures", p -> sansExceptionAnonyme(() -> doGet(ASSURES + "?centerId=" + CENTRE_A, p)));
        routes.put("GET historique des assurés", p -> sansExceptionAnonyme(() -> doGet(PATIENTS + "/" + id + "/assures/history?centerId=" + CENTRE_A, p)));
        for (Map.Entry<String, Function<RequestPostProcessor, Resp>> r : routes.entrySet()) {
            matrice("matrice_" + r.getKey().replace(' ', '_').replace("/", "_").replace("é", "e") + "_nouveau_code", r.getValue());
            arbre("anonyme_" + r.getKey().replace(' ', '_').replace("/", "_").replace("é", "e"),
                    Map.of("anonyme", statutEtCode(r.getKey().equals("POST search")
                            ? doPostAnonyme(PATIENTS + "/search", json(c))
                            : doGetAnonymous(PATIENTS + "/" + (r.getKey().equals("GET fiche") ? id + "?centerId=" + CENTRE_A
                            : r.getKey().equals("GET summary") ? "summary?centerId=" + CENTRE_A
                            : r.getKey().equals("GET summary/details") ? "summary/details?centerId=" + CENTRE_A
                            : r.getKey().equals("GET assures") ? "assures?centerId=" + CENTRE_A
                            : id + "/assures/history?centerId=" + CENTRE_A)))));
        }
    }

    private Resp doPostAnonyme(String path, String corps) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(java.net.URI.create(path))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps));
    }

    @Test
    void les_ecritures_d_assure_sont_refusees_au_medecin_seul_et_ouvertes_aux_autres_profils_de_centre() throws Exception {
        UUID id = patientSansPlace("P1");
        jdbc.update("UPDATE patients SET qualite_assure = 'ENFANT' WHERE id = ?", id);
        assure("PC1-AS-1", CENTRE_A);
        String assure = json("{'nom':'N','prenom':'P','sexe':'M','telPersonnel':null,'adresse':'rue','groupeSanguin':null}");
        String affectation = json("{'dateDebutAffectation':'" + jour(-5) + "','dateFinAffectation':null}");
        UUID inconnue = UUID.nameUUIDFromBytes("affectation-inconnue".getBytes());
        matrice("matrice_affecter_un_assure_nouveau_code", p -> sansExceptionAnonyme(() -> doPost(PATIENTS + "/" + id + "/assures/PC1-AS-1/affecter?centerId=" + CENTRE_A, "", p)));
        matrice("matrice_modifier_une_affectation_nouveau_code", p -> sansExceptionAnonyme(() -> doPut(PATIENTS + "/" + id + "/assures/assignments/" + inconnue
                + "?centerId=" + CENTRE_A, affectation, p)));
        matrice("matrice_modifier_un_assure_nouveau_code", p -> sansExceptionAnonyme(() -> doPut(ASSURES + "/PC1-AS-1?centerId=" + CENTRE_A, assure, p)));
        arbre("anonyme", Map.of(
                "affecter", statutEtCode(doPostAnonyme(PATIENTS + "/" + id + "/assures/PC1-AS-1/affecter?centerId=" + CENTRE_A, "")),
                "modifier un assuré", statutEtCode(doPutAnonyme(ASSURES + "/PC1-AS-1?centerId=" + CENTRE_A, assure))));
    }

    private Resp doPutAnonyme(String path, String corps) throws Exception {
        return doPutAnonymous(path, corps);
    }

    @Test
    void les_lectures_de_la_fiche_croient_le_centre_demande_par_le_client_sans_le_comparer_a_la_session() throws Exception {
        UUID chezA = patientSansPlace("A1");
        UUID chezB = patientPlace(CENTRE_B, "B1", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        String rechercheB = "{'centerId':'" + CENTRE_B + "','page':0,'size':10}";
        Resp r = recherche(rechercheB, adminA());
        m.put("recherche du centre B par l'ADMIN du centre A", r.status() + " total=" + (r.status() == 200 ? corps(r).path("total").asInt() : -1));
        m.put("recherche du centre B par le médecin du centre A", recherche(rechercheB, as(CENTRE_A, "MEDECIN")).status());
        m.put("recherche sans centre de session (ADMIN sans centre)", recherche(rechercheB, as(null, "ADMIN")).status());
        m.put("fiche du centre B lue par l'ADMIN du centre A", lireLaFiche(chezB, CENTRE_B, adminA()).status());
        m.put("fiche du centre B lue sans centre de session", lireLaFiche(chezB, CENTRE_B, as(null, "ADMIN")).status());
        m.put("résumé du centre B par l'ADMIN du centre A", corps(doGet(PATIENTS + "/summary?centerId=" + CENTRE_B, adminA())).path("totalPatients").asInt());
        m.put("détail du résumé du centre B par l'ADMIN du centre A", corps(doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_B, adminA())).path("total").asInt());
        m.put("historique des assurés du patient B demandé avec le centre B", doGet(PATIENTS + "/" + chezB + "/assures/history?centerId=" + CENTRE_B, adminA()).status());
        arbre("lectures_sans_garde_nouveau_code", m);
        Map<String, Object> ecritures = new LinkedHashMap<>();
        assure("PC1-AS-A", CENTRE_A);
        assure("PC1-AS-B", CENTRE_B);
        String assure = json("{'nom':'Renomme','prenom':'P','sexe':'M','telPersonnel':null,'adresse':'rue','groupeSanguin':null}");
        ecritures.put("assuré du centre B modifié avec centerId=A", refus(doPut(ASSURES + "/PC1-AS-B?centerId=" + CENTRE_A, assure, adminA())));
        ecritures.put("assuré du centre A modifié avec centerId=A par l'ADMIN du centre B", refus(doPut(ASSURES + "/PC1-AS-A?centerId=" + CENTRE_A, assure, adminB())));
        ecritures.put("assuré du centre B modifié avec centerId=B par l'ADMIN du centre A", refus(doPut(ASSURES + "/PC1-AS-B?centerId=" + CENTRE_B, assure, adminA())));
        jdbc.update("UPDATE patients SET qualite_assure = 'ENFANT' WHERE id IN (?, ?)", chezA, chezB);
        ecritures.put("affecter l'assuré A au patient B avec centerId=B par l'ADMIN du centre A",
                refus(doPost(PATIENTS + "/" + chezB + "/assures/PC1-AS-A/affecter?centerId=" + CENTRE_B, "", adminA())));
        ecritures.put("affecter l'assuré B au patient A avec centerId=A", refus(doPost(PATIENTS + "/" + chezA + "/assures/PC1-AS-B/affecter?centerId=" + CENTRE_A, "", adminA())));
        arbre("ecritures_d_assure_sans_garde_nouveau_code", ecritures);
        sql("assures_apres_nouveau_code","SELECT numero_assurance, center_id, nom FROM assure WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY numero_assurance");
        sql("affectations_apres_nouveau_code","SELECT patient_id, numero_assurance, center_id, is_primary FROM assure_patient WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') ORDER BY patient_id, numero_assurance");
    }

    @Test
    void un_compte_de_centre_ecrit_dans_son_centre_et_la_garde_de_la_fiche_rend_403_sur_le_centre_du_corps() throws Exception {
        UUID a = patientSansPlace("A1");
        Map<String, Object> corpsB = ficheDuPatient("A1");
        corpsB.put("centerId", CENTRE_B.toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("PUT d'un patient du centre A avec le centre B dans le corps (ADMIN du centre A)", refus(modifier(a, corpsB, adminA())));
        m.put("PUT d'un patient du centre A avec le centre A dans le corps (ADMIN du centre B)", refus(modifier(a, ficheDuPatient("A1"), adminB())));
        m.put("POST dans le centre B par l'ADMIN du centre A", refus(doPost(PATIENTS, corps(corpsB), adminA())));
        arbre("garde_de_la_fiche", m);
        etatDeLIdentite("fiches");
    }
}
