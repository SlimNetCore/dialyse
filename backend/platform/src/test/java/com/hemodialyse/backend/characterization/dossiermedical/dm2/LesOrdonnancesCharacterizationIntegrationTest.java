package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM2, lot 1 (ordonnances) : {@code /api/v1/patients/{id}/ordonnances} (liste, création, signer, imprimer, annuler). Données fictives.
 * Les dates sont des dates fixes nommées {@code <D+n>} : aucune dépendance au jour d'exécution (sauf la date par défaut, masquée).
 */
class LesOrdonnancesCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final String DEUX_LIGNES = "[{\"codeSystem\":\"ATC\",\"code\":\"b03xa02\",\"codeDisplay\":\"Darbepoetine fictive\","
            + "\"libelle\":\"Epoetine fictive\",\"posologie\":\"1 injection par semaine\",\"voie\":\"SC\",\"dureeJours\":28,\"quantite\":4,"
            + "\"instructions\":\"A conserver au froid\"},"
            + "{\"libelle\":\"Paracetamol fictif\",\"posologie\":\"1 comprime si douleur\"}]";

    @Override
    protected String dossier() {
        return "ordonnances";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
    }

    private String idDe(Resp r) {
        return corps(r).path("id").asString();
    }

    // ------------------------------------------------------------------ création

    @Test
    void une_ordonnance_creee_est_un_brouillon_sans_numero_avec_ses_lignes_dans_l_ordre_saisi() throws Exception {
        deuxCentres();
        Resp r = doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "medecin-fictif", fixe(3), DEUX_LIGNES), as(CENTRE_A, "MEDECIN"));
        ecriture("reponse", r);
        etatDesOrdonnances("ordonnances");
        etatDesLignes("lignes");
    }

    @Test
    void la_date_absente_vaut_le_jour_d_enregistrement_et_le_medecin_peut_etre_absent_ou_quelconque() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp sansDate = doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, null, null, ligneSimple()), as(CENTRE_A, "MEDECIN"));
        // Le jour par défaut est lu sur l'horloge de la JVM (LocalDate.now()) : on contrôle qu'il s'agit d'un jour plausible, puis on le masque.
        Set<String> plausibles = new java.util.HashSet<>(List.of(LocalDate.now().toString(),
                LocalDate.now(ZoneId.of("Africa/Algiers")).toString(), LocalDate.now(ZoneId.of("UTC")).toString()));
        assertThat(plausibles).contains(corps(sansDate).path("datePrescription").asString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", sansDate.status());
        m.put("medecinId", corps(sansDate).path("medecinId").isNull() ? null : corps(sansDate).path("medecinId").asString());
        m.put("datePrescription", "<DATE_DU_JOUR>");
        m.put("etat", corps(sansDate).path("statut").asString());
        cas.put("sans date ni medecin", m);
        Resp autreMedecin = doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "n-importe-quel-texte", fixe(1), ligneSimple()),
                as(CENTRE_A, "MEDECIN"));
        cas.put("medecinId quelconque (pas le compte connecte)", Map.of("statut", autreMedecin.status(), "medecinId",
                corps(autreMedecin).path("medecinId").asString()));
        arbre("reponses", cas);
    }

    @Test
    void les_lignes_invalides_sont_refusees_sans_rien_enregistrer() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        String[][] lignes = {
                {"liste de lignes vide", "[]"},
                {"lignes absentes", null},
                {"ni code ni libelle", "[{\"posologie\":\"1 par jour\"}]"},
                {"libelle blanc sans code", "[{\"libelle\":\"  \",\"posologie\":\"1 par jour\"}]"},
                {"posologie absente", "[{\"libelle\":\"X\"}]"},
                {"posologie blanche", "[{\"libelle\":\"X\",\"posologie\":\" \"}]"},
                {"duree nulle", "[{\"libelle\":\"X\",\"posologie\":\"1\",\"dureeJours\":0}]"},
                {"duree negative", "[{\"libelle\":\"X\",\"posologie\":\"1\",\"dureeJours\":-3}]"},
                {"quantite nulle", "[{\"libelle\":\"X\",\"posologie\":\"1\",\"quantite\":0}]"},
                {"quantite negative", "[{\"libelle\":\"X\",\"posologie\":\"1\",\"quantite\":-1}]"},
                {"code sans systeme", "[{\"code\":\"B03XA02\",\"posologie\":\"1\"}]"},
                {"systeme inconnu", "[{\"codeSystem\":\"INCONNU\",\"code\":\"B03XA02\",\"posologie\":\"1\"}]"},
                {"code ATC mal forme", "[{\"codeSystem\":\"ATC\",\"code\":\"XYZ\",\"posologie\":\"1\"}]"},
                {"code blanc avec libelle (le code est ignore)", "[{\"code\":\" \",\"libelle\":\"X\",\"posologie\":\"1\"}]"},
                {"ligne nulle", "[null]"},
        };
        for (String[] l : lignes) {
            cas.put(l[0], statutEtCode(doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "m", fixe(1), l[1]), as(CENTRE_A, "MEDECIN"))));
        }
        cas.put("corps vide", statutEtCode(doPost(ordonnancesApi(patientA), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("corps mal forme", statutEtCode(doPost(ordonnancesApi(patientA), "{pas du json", as(CENTRE_A, "MEDECIN"))));
        cas.put("date illisible", statutEtCode(doPost(ordonnancesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"datePrescription\":\"demain\",\"lignes\":" + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant de patient illisible", statutEtCode(doPost(PATIENTS_API + "/pas-un-uuid/ordonnances",
                corpsOrdonnance(CENTRE_A, "m", fixe(1), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        // Les lignes acceptees ("code blanc avec libelle") sont les seules ecritures : une seule ordonnance en base.
        etatDesOrdonnances("ordonnances");
    }

    @Test
    void l_ordonnance_peut_viser_un_patient_inconnu_ou_d_un_autre_centre() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp inconnu = doPost(ordonnancesApi(idStable("patient", "INCONNU")), corpsOrdonnance(CENTRE_A, "m", fixe(1), ligneSimple()),
                as(CENTRE_A, "MEDECIN"));
        cas.put("patient inconnu", inconnu.status());
        Resp dautre = doPost(ordonnancesApi(patientB), corpsOrdonnance(CENTRE_A, "m", fixe(2), ligneSimple()), as(CENTRE_A, "MEDECIN"));
        cas.put("patient du centre B, session du centre A", dautre.status());
        arbre("reponses", cas);
        etatDesOrdonnances("ordonnances");
    }

    // ------------------------------------------------------------------ cycle de statut

    @Test
    void la_signature_attribue_un_numero_sequentiel_par_centre_et_pose_la_date_de_signature() throws Exception {
        deuxCentres();
        List<Object> numeros = new ArrayList<>();
        UUID a1 = UUID.fromString(idDe(doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "m", fixe(1), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        UUID a2 = UUID.fromString(idDe(doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "m", fixe(2), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        UUID b1 = UUID.fromString(idDe(doPost(ordonnancesApi(patientB), corpsOrdonnance(CENTRE_B, "m", fixe(1), ligneSimple()), as(CENTRE_B, "MEDECIN"))));
        aliasNommes.put(a1.toString(), "<ORD_A1>");
        aliasNommes.put(a2.toString(), "<ORD_A2>");
        aliasNommes.put(b1.toString(), "<ORD_B1>");
        // La seconde est signee avant la premiere : le numero suit l'ordre des signatures, pas celui des creations.
        Resp s2 = doPut(ordonnancesApi(patientA) + "/" + a2 + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        Resp s1 = doPut(ordonnancesApi(patientA) + "/" + a1 + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        Resp sb = doPut(ordonnancesApi(patientB) + "/" + b1 + "/signer", corpsCentre(CENTRE_B), as(CENTRE_B, "MEDECIN"));
        numeros.add(Map.of("ORD_A2 signee en premier", corps(s2).path("numero").asString() + " / " + corps(s2).path("statut").asString()));
        numeros.add(Map.of("ORD_A1 signee en second", corps(s1).path("numero").asString()));
        numeros.add(Map.of("ORD_B1 (autre centre : sa propre sequence)", corps(sb).path("numero").asString()));
        arbre("numeros", numeros);
        ecriture("reponse_signature", s1);
        etatDesOrdonnances("ordonnances");
        sql("compteurs", "SELECT center_id, cle, prefixe, dernier_compteur FROM app_settings WHERE cle = 'SEQ_ORD' AND center_id IN ('"
                + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id");
    }

    @Test
    void les_transitions_de_statut_depuis_chaque_etat() throws Exception {
        deuxCentres();
        Map<String, Object> matrice = new LinkedHashMap<>();
        int jour = 0;
        for (String depart : List.of("BROUILLON", "SIGNEE", "IMPRIMEE", "ANNULEE")) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            for (String action : List.of("signer", "imprimer", "annuler")) {
                nettoyerDm2();
                deuxCentres();
                UUID o = ordonnance(CENTRE_A, patientA, "ORD", fixe(++jour % 5), depart, "BROUILLON".equals(depart) ? null : "ORD-00042");
                Resp r = doPut(ordonnancesApi(patientA) + "/" + o + "/" + action, corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
                Map<String, Object> cas = new LinkedHashMap<>();
                cas.put("reponse", statutEtCode(r));
                if (r.status() == 200) {
                    cas.put("statut", corps(r).path("statut").asString());
                    cas.put("numero", corps(r).path("numero").isNull() ? null : corps(r).path("numero").asString());
                    cas.put("signee", !corps(r).path("signedAt").isNull());
                }
                cas.put("statut en base", jdbc.queryForObject("SELECT statut FROM ordonnances WHERE id = ?", String.class, o));
                ligne.put(action, cas);
            }
            matrice.put("depuis " + depart, ligne);
        }
        arbre("matrice", matrice);
    }

    @Test
    void un_refus_de_transition_est_sans_effet_sur_l_ordonnance_mais_consomme_un_numero_de_la_sequence() throws Exception {
        deuxCentres();
        UUID annulee = ordonnance(CENTRE_A, patientA, "ANNULEE", fixe(1), "ANNULEE", null);
        UUID brouillon = ordonnance(CENTRE_A, patientA, "BROUILLON", fixe(2), "BROUILLON", null);
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp refus = doPut(ordonnancesApi(patientA) + "/" + annulee + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        cas.put("signature d'une annulee", statutEtCode(refus));
        cas.put("compteur apres le refus", compteur(CENTRE_A));
        Resp ok = doPut(ordonnancesApi(patientA) + "/" + brouillon + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        cas.put("signature du brouillon ensuite : numero attribue", corps(ok).path("numero").asString());
        arbre("numerotation", cas);
        etatDesOrdonnances("ordonnances");
    }

    @Test
    void rejouer_une_transition_deja_faite_est_refuse_et_ne_change_rien() throws Exception {
        deuxCentres();
        UUID o = UUID.fromString(idDe(doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "m", fixe(1), DEUX_LIGNES), as(CENTRE_A, "MEDECIN"))));
        aliasNommes.put(o.toString(), "<ORD>");
        assertThat(doPut(ordonnancesApi(patientA) + "/" + o + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN")).status()).isEqualTo(200);
        String avant = empreinteDeLOrdonnance(o);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("signer une seconde fois", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        assertThat(empreinteDeLOrdonnance(o)).isEqualTo(avant);
        cas.put("imprimer", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/imprimer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("imprimer une seconde fois (la reimpression n'est pas une transition)",
                statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/imprimer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("annuler", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/annuler", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("annuler une seconde fois", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/annuler", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        arbre("rejeux", cas);
        etatDesOrdonnances("ordonnances");
        etatDesLignes("lignes");
    }

    private String empreinteDeLOrdonnance(UUID o) {
        return jdbc.queryForList("SELECT statut, numero, signed_at, updated_at FROM ordonnances WHERE id = ?", o).toString()
                + jdbc.queryForList("SELECT COUNT(*) FROM lignes_ordonnance WHERE ordonnance_id = ?", o);
    }

    private String compteur(UUID centre) {
        List<Long> l = jdbc.queryForList("SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = 'SEQ_ORD'", Long.class, centre);
        return l.isEmpty() ? "(aucune ligne)" : String.valueOf(l.get(0));
    }

    @Test
    void l_ordonnance_est_cherchee_par_id_patient_et_centre_sinon_400_et_les_routes_de_modification_n_existent_pas() throws Exception {
        deuxCentres();
        UUID oa = ordonnance(CENTRE_A, patientA, "ORD_A", fixe(1), "BROUILLON", null);
        UUID ob = ordonnance(CENTRE_B, patientB, "ORD_B", fixe(1), "BROUILLON", null);
        Map<String, String> cas = new LinkedHashMap<>();
        UUID inconnue = idStable("ordonnance", "INCONNUE");
        cas.put("ordonnance inconnue", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + inconnue + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("temoin : ordonnance du patient A signee sous le patient A", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + oa + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        UUID autrePatient = patientDm2(CENTRE_A, "PAT_A2");
        cas.put("ordonnance du patient A lue sous le patient A2", statutEtCode(doPut(ordonnancesApi(autrePatient) + "/" + oa + "/annuler", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("ordonnance du centre B signee par le centre A", statutEtCode(doPut(ordonnancesApi(patientB) + "/" + ob + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition avec le centre B (session A)", statutEtCode(doPut(ordonnancesApi(patientB) + "/" + ob + "/signer", corpsCentre(CENTRE_B), as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition sans centre (centre de la session)", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + oa + "/imprimer", "{}", as(CENTRE_A, "MEDECIN"))));
        cas.put("corps de transition absent", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + oa + "/imprimer", "", as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant d'ordonnance illisible", statutEtCode(doPut(ordonnancesApi(patientA) + "/pas-un-uuid/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        cas.put("PUT de modification du contenu (route inexistante)", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + oa, ordonnanceBrut(), as(CENTRE_A, "MEDECIN"))));
        cas.put("DELETE (route inexistante)", statutEtCode(doDelete(ordonnancesApi(patientA) + "/" + oa, as(CENTRE_A, "MEDECIN"))));
        cas.put("GET d'une ordonnance seule (route inexistante)", statutEtCode(doGet(ordonnancesApi(patientA) + "/" + oa, as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        etatDesOrdonnances("ordonnances");
    }

    private String ordonnanceBrut() {
        return corpsOrdonnance(CENTRE_A, "m", fixe(1), ligneSimple());
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void la_liste_est_triee_du_plus_recent_au_plus_ancien_et_paginee() throws Exception {
        deuxCentres();
        UUID autre = patientDm2(CENTRE_A, "PAT_A2");
        ordonnance(CENTRE_A, patientA, "ORD_1", fixe(1), "BROUILLON", null);
        ordonnance(CENTRE_A, patientA, "ORD_2", fixe(4), "SIGNEE", "ORD-00001");
        ordonnance(CENTRE_A, patientA, "ORD_3", fixe(2), "IMPRIMEE", "ORD-00002");
        ordonnance(CENTRE_A, patientA, "ORD_4", fixe(5), "ANNULEE", "ORD-00003");
        ordonnance(CENTRE_A, patientA, "ORD_5", fixe(3), "BROUILLON", null);
        ordonnance(CENTRE_A, autre, "ORD_AUTRE", fixe(6), "BROUILLON", null);
        Map<String, Object> pages = new LinkedHashMap<>();
        for (int page = 0; page <= 3; page++) {
            pages.put("page " + page, page(patientA, "page=" + page + "&size=2", as(CENTRE_A, "MEDECIN")));
        }
        arbre("pages", pages);
        Map<String, Object> bornes = new LinkedHashMap<>();
        for (String parametres : new String[]{"", "size=0", "size=-1", "page=-1", "size=1000", "size=abc", "page=abc", "centerId=pas-un-uuid"}) {
            Resp r = doGet(ordonnancesApi(patientA) + (parametres.isEmpty() ? "" : "?" + parametres), as(CENTRE_A, "MEDECIN"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", statutEtCode(r));
            if (r.status() == 200) {
                JsonNode c = corps(r);
                m.put("items", c.path("items").size());
                m.put("total", c.path("total").asInt());
                m.put("page", c.path("page").asInt());
                m.put("size", c.path("size").asInt());
            }
            bornes.put(parametres.isEmpty() ? "(defauts)" : parametres, m);
        }
        arbre("bornes", bornes);
    }

    private Map<String, Object> page(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        Resp r = doGet(ordonnancesApi(patient) + "?" + parametres, who);
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("total", c.path("total").asInt());
        m.put("page", c.path("page").asInt());
        m.put("size", c.path("size").asInt());
        List<Object> items = new ArrayList<>();
        c.path("items").forEach(i -> items.add(ordonnanceLisible(i)));
        m.put("items", items);
        return m;
    }

    @Test
    void la_liste_rend_les_lignes_le_numero_et_les_dates_de_chaque_ordonnance() throws Exception {
        deuxCentres();
        UUID o = UUID.fromString(idDe(doPost(ordonnancesApi(patientA), corpsOrdonnance(CENTRE_A, "m", fixe(1), DEUX_LIGNES), as(CENTRE_A, "MEDECIN"))));
        aliasNommes.put(o.toString(), "<ORD>");
        doPut(ordonnancesApi(patientA) + "/" + o + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        Resp liste = doGet(ordonnancesApi(patientA) + "?centerId=" + CENTRE_A, as(CENTRE_A, "ADMIN"));
        JsonNode item = corps(liste).path("items").get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", liste.status());
        m.put("cacheControl", liste.cacheControl());
        m.put("ordonnance", ordonnanceLisible(item));
        List<Object> lignes = new ArrayList<>();
        item.path("lignes").forEach(l -> {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("codeSystem", l.path("codeSystem").isNull() ? null : l.path("codeSystem").asString());
            ligne.put("code", l.path("code").isNull() ? null : l.path("code").asString());
            ligne.put("codeDisplay", l.path("codeDisplay").isNull() ? null : l.path("codeDisplay").asString());
            ligne.put("libelle", l.path("libelle").isNull() ? null : l.path("libelle").asString());
            ligne.put("dureeJours", l.path("dureeJours").isNull() ? null : l.path("dureeJours").asInt());
            ligne.put("quantite", l.path("quantite").isNull() ? null : l.path("quantite").asInt());
            ligne.put("instructions", l.path("instructions").isNull() ? null : l.path("instructions").asString());
            lignes.add(ligne);
        });
        lignes.sort((x, y) -> String.valueOf(((Map<?, ?>) x).get("libelle")).compareTo(String.valueOf(((Map<?, ?>) y).get("libelle"))));
        m.put("lignes detaillees (triees)", lignes);
        arbre("liste", m);
    }

    // ------------------------------------------------------------------ droits et cloisonnement

    @Test
    void les_droits_par_profil_sur_les_cinq_routes() throws Exception {
        deuxCentres();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            nettoyerDm2();
            deuxCentres();
            UUID o = ordonnance(CENTRE_A, patientA, "ORD_DROITS", fixe(1), "BROUILLON", null);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(ordonnancesApi(patientA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(ordonnancesApi(patientA), ordonnanceBrut(), p.getValue())));
            ligne.put("signer", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/signer", corpsCentre(CENTRE_A), p.getValue())));
            ligne.put("imprimer", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/imprimer", corpsCentre(CENTRE_A), p.getValue())));
            ligne.put("annuler", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + o + "/annuler", corpsCentre(CENTRE_A), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        Map<String, Object> anonyme = new LinkedHashMap<>();
        UUID o = patientDm2(CENTRE_A, "PAT_ANON");
        anonyme.put("liste", statutEtCode(doGetAnonymous(ordonnancesApi(o))));
        anonyme.put("creation", statutEtCode(doPostAnonyme(ordonnancesApi(o), ordonnanceBrut())));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    private Resp doPostAnonyme(String path, String corps) throws Exception {
        return appel("POST", path, corps, null);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session() throws Exception {
        deuxCentres();
        ordonnance(CENTRE_B, patientB, "ORD_B", fixe(1), "SIGNEE", "ORD-00007");
        UUID oa = ordonnance(CENTRE_A, patientA, "ORD_A", fixe(2), "BROUILLON", null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("liste du patient B par le centre A (centre de la session)", page(patientB, "size=5", as(CENTRE_A, "MEDECIN")));
        cas.put("liste avec centerId du centre B (session A)", statutEtCode(doGet(ordonnancesApi(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("liste du patient A par le centre B (centre de la session)", page(patientA, "size=5", as(CENTRE_B, "MEDECIN")));
        cas.put("creation avec le centre B dans le corps (session A)", statutEtCode(doPost(ordonnancesApi(patientA),
                corpsOrdonnance(CENTRE_B, "m", fixe(1), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(ordonnancesApi(patientA),
                corpsOrdonnance(null, "m", fixe(1), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session", statutEtCode(doGet(ordonnancesApi(patientA), as(null, "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(ordonnancesApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN signe au centre B une ordonnance du centre A", statutEtCode(doPut(ordonnancesApi(patientA) + "/" + oa + "/signer",
                corpsCentre(CENTRE_B), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        arbre("cloisonnement", cas);
        etatDesOrdonnances("ordonnances");
    }

    // ------------------------------------------------------------------ effets de bord

    @Test
    void les_ecritures_sont_journalisees_sans_notification_ni_diffusion() throws Exception {
        deuxCentres();
        monterLAudit();
        Resp c = doPost(ordonnancesApi(patientA), ordonnanceBrut(), as(CENTRE_A, "MEDECIN"));
        String id = idDe(c);
        doPut(ordonnancesApi(patientA) + "/" + id + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(ordonnancesApi(patientA) + "/" + id + "/imprimer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(ordonnancesApi(patientA) + "/" + id + "/annuler", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doPut(ordonnancesApi(patientA) + "/" + id + "/annuler", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN"));
        doGet(ordonnancesApi(patientA), as(CENTRE_A, "MEDECIN"));
        aliasNommes.put(id, "<ORD>");
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void deux_signatures_simultanees_de_deux_ordonnances_recoivent_deux_numeros_distincts() throws Exception {
        deuxCentres();
        UUID o1 = ordonnance(CENTRE_A, patientA, "ORD_1", fixe(1), "BROUILLON", null);
        UUID o2 = ordonnance(CENTRE_A, patientA, "ORD_2", fixe(2), "BROUILLON", null);
        List<Resp> reponses = enParallele(
                () -> doPut(ordonnancesApi(patientA) + "/" + o1 + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN")),
                () -> doPut(ordonnancesApi(patientA) + "/" + o2 + "/signer", corpsCentre(CENTRE_A), as(CENTRE_A, "MEDECIN")));
        // Invariants seulement (la concurrence n'est pas verrouillee en golden) : les signatures abouties ont des numeros distincts.
        List<String> numeros = new ArrayList<>();
        for (Resp r : reponses) {
            assertThat(r.status()).isIn(200, 409, 500);
            if (r.status() == 200) numeros.add(corps(r).path("numero").asString());
        }
        assertThat(numeros).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT numero) FROM ordonnances WHERE center_id = ? AND numero IS NOT NULL",
                Integer.class, CENTRE_A)).isEqualTo(numeros.size());
    }
}
