package com.hemodialyse.backend.characterization.seance.pl5;

import com.hemodialyse.backend.characterization.seance.se7e.Se7eCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * PL5 : filet du cahier de dialyse imprime, observe au point HTTP {@code GET /api/v1/cahier-dialyse/{patientId}/imprimer}
 * (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; centre de la session). Le PDF est decode en texte (PDFBox) : jamais les octets ; la date d'edition est
 * masquee.
 * <p>
 * Complete le filet SE7e (statuts, bornes, tri, surcharge de forfait, refus metier) par : le contenu complet d'un cahier (identite, volets,
 * consommables valorises, prescription, dossier), les cas limites de contenu, l'etanchéite entre centres, les droits par profil, la garde de
 * centre, les parametres illisibles, l'audit. Aucun mock, aucun seam, aucune ecriture caracterisee. DONNEES FICTIVES, dates fixes {@code <D+n>}.
 */
class LeCahierDeDialyseHttpPl5CharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    private static final Timestamp T1 = Timestamp.from(OffsetDateTime.parse("2026-01-01T10:00:00Z").toInstant());
    private static final Timestamp T2 = Timestamp.from(OffsetDateTime.parse("2026-01-01T11:00:00Z").toInstant());

    @Override
    protected String tranche() {
        return "pl5";
    }

    @Override
    protected String dossier() {
        return "cahier";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ? AND article_id IN (SELECT id FROM articles WHERE code LIKE 'PL5-%')", centre);
            jdbc.update("DELETE FROM volet_medical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM dossier_medical_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ? AND code LIKE 'PL5-%'", centre);
        }
        super.nettoyerSe1();
    }

    // ------------------------------------------------------------------ fixtures

    private UUID article(UUID centre, String code, String libelle, String unite) {
        UUID id = UUID.nameUUIDFromBytes(("pl5-article-" + code + "-" + centre).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, active, gere_par_lot, seuil_alerte, stock_quantity) "
                + "VALUES (?,?,?,?,?,TRUE,FALSE,0,0)", id, centre, code, libelle, unite);
        return id;
    }

    private void sortieDeStock(UUID centre, UUID article, UUID seance, String type, String quantite, String prix) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, seance_id, mouvement_type, quantite, prix_unitaire, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, article, seance, type, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix));
    }

    private void voletParamedicalComplet(UUID centre, UUID seance, String avant, String apres, String taAvant, String taApres, Integer duree,
                                         Integer debit, String uf, String anticoagulant, String dialysat, String incidents) {
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, duree_minutes, "
                        + "debit_sang_ml_min, ultrafiltration_ml, anticoagulant, type_dialysat, incidents) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), seance, centre, avant == null ? null : new BigDecimal(avant), apres == null ? null : new BigDecimal(apres), taAvant,
                taApres, duree, debit, uf == null ? null : new BigDecimal(uf), anticoagulant, dialysat, incidents);
    }

    private void voletMedical(UUID centre, UUID seance, String prescription, String tolerance, String examen, String resultats, String ajustements,
                              String conclusion) {
        jdbc.update("INSERT INTO volet_medical (id, seance_id, center_id, prescription, tolerance_seance, examen_clinique, resultats_biologiques, "
                        + "ajustements_therapeutiques, conclusion_medicale) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), seance, centre, prescription,
                tolerance, examen, resultats, ajustements, conclusion);
    }

    private void dossierMedical(UUID centre, UUID patient, String nephropathie, LocalDate miseEnDialyse, String hepB, String hepC, String observation,
                                String conclusion) {
        jdbc.update("INSERT INTO dossier_medical_patient (id, patient_id, center_id, nephropathie_initiale, date_mise_en_dialyse, hepatite_b_statut, "
                        + "hepatite_c_statut, observation_globale, conclusion_medicale) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre,
                nephropathie, miseEnDialyse == null ? null : Date.valueOf(miseEnDialyse), hepB, hepC, observation, conclusion);
    }

    private void prescription(UUID centre, UUID patient, LocalDate jour, Timestamp creation, String poidsSec, UUID epo, Integer epoDose, String epoVoie,
                              Integer epoFreq, String epoUnite, UUID fer, Integer ferDose, String ferVoie, Integer ferFreq, String ferUnite) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, poids_sec_cible_kg, epo_article_id, epo_dose_ui, "
                        + "epo_voie, epo_frequence_valeur, epo_frequence_unite, fer_article_id, fer_dose_mg, fer_voie, fer_frequence_valeur, "
                        + "fer_frequence_unite, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre, Date.valueOf(jour),
                poidsSec == null ? null : new BigDecimal(poidsSec), epo, epoDose, epoVoie, epoFreq, epoUnite, fer, ferDose, ferVoie, ferFreq, ferUnite, creation);
    }

    private static final java.util.regex.Pattern EDITION = java.util.regex.Pattern.compile("Édité le (\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})");
    private static final java.time.format.DateTimeFormatter STAMP = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private java.time.LocalDateTime edition(UUID patient, String tz) throws Exception {
        MockHttpServletResponse r = binaire(api(patient, "?tz=" + tz), infirmierA());
        for (String ligne : texteDuPdf(r.getContentAsByteArray())) {
            java.util.regex.Matcher mm = EDITION.matcher(ligne);
            if (mm.find()) return java.time.LocalDateTime.parse(mm.group(1), STAMP);
        }
        throw new AssertionError("date d'edition absente");
    }

    /** Heures d'ecart (arrondies) entre la date d'edition imprimee pour {@code tz} et celle imprimee pour UTC, deux appels successifs. */
    private String decalageEnHeures(UUID patient, String tz) throws Exception {
        java.time.LocalDateTime utc = edition(patient, "UTC");
        java.time.LocalDateTime autre = edition(patient, tz);
        long heures = Math.round(java.time.Duration.between(utc, autre).toMinutes() / 60.0);
        return (heures >= 0 ? "+" : "") + heures + " h";
    }

    private static String api(UUID patient, String requete) {
        return "/api/v1/cahier-dialyse/" + patient + "/imprimer" + requete;
    }

    /** Statut et en-tetes ; pour un 200, le texte du PDF (toutes les lignes, ou seulement celles de la seance si {@code complet} est faux). */
    private Map<String, Object> cahier(String path, RequestPostProcessor who, boolean complet) throws Exception {
        MockHttpServletResponse r = who == null ? mvc.perform(get(URI.create(path))).andReturn().getResponse() : binaire(path, who);
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.getStatus() != 200) {
            m.putAll(refusOuStatut(r.getStatus(), r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
            return m;
        }
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("contentDisposition", r.getHeader("Content-Disposition"));
        m.put("cacheControl", r.getHeader("Cache-Control"));
        List<String> lignes = texteNormalise(r.getContentAsByteArray());
        m.put("texte", complet ? lignes : lignes.stream().filter(l -> l.contains("Séance d'hémodialyse") || l.startsWith("pages=")).toList());
        return m;
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    void un_cahier_complet_imprime_l_identite_les_volets_les_consommables_valorises_et_la_prescription_en_vigueur() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5N");
        jdbc.update("UPDATE patients SET date_naissance = ?, groupe_sanguin = 'O+', tel_mobile = '0555 00 00 00', sexe = 'F' WHERE id = ?",
                Date.valueOf("1970-05-05"), patient);
        caches.getCacheNames().forEach(n -> java.util.Optional.ofNullable(caches.getCache(n)).ifPresent(org.springframework.cache.Cache::clear));
        dossierMedical(CENTRE_A, patient, "Néphropathie diabétique", LocalDate.of(2024, 3, 5), "VACCINE", "NEGATIF", "Patient stable",
                "Poursuivre le traitement");
        UUID epo = article(CENTRE_A, "PL5-EPO", "Epoetine alfa", "seringue");
        UUID fer = article(CENTRE_A, "PL5-FER", "Fer saccharose", "ampoule");
        prescription(CENTRE_A, patient, fixe(0), T1, "70.00", epo, 4000, "SC", 1, "SEMAINE", fer, 100, "IV", 2, "MOIS");
        prescription(CENTRE_A, patient, fixe(40), T1, "68.50", epo, 6000, "IV", 3, "SEMAINE", null, null, null, null, null);
        UUID s1 = seanceEnBase(CENTRE_A, patient, "N1_COMPLETE", fixe(31), "VALIDEE");
        UUID s2 = seanceEnBase(CENTRE_A, patient, "N2_SANS_VOLET", fixe(45), "FACTUREE");
        voletParamedicalComplet(CENTRE_A, s1, "72.50", "70.00", "13/8", "12/7", 240, 300, "2500", "Héparine", "Bicarbonate", "Crampes en fin de séance");
        voletMedical(CENTRE_A, s1, "Hémodialyse 4h", "Bonne tolérance", "RAS", "Hb 10,5 g/dL", "Augmenter l'EPO", "Séance satisfaisante");
        UUID gants = article(CENTRE_A, "PL5-GANTS", "Gants stériles", "boîte");
        UUID dialyseur = article(CENTRE_A, "PL5-DIALYSEUR", "Dialyseur haute perméabilité", "pièce");
        sortieDeStock(CENTRE_A, gants, s1, "SORTIE", "2", "150.50");
        sortieDeStock(CENTRE_A, gants, s1, "SORTIE", "1", "150.50");
        sortieDeStock(CENTRE_A, dialyseur, s1, "SORTIE", "1.5", "20.00");
        sortieDeStock(CENTRE_A, gants, s1, "ENTREE", "10", "150.50");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 cahier complet, deux seances (infirmier, tz=Africa/Algiers)",
                cahier(api(patient, "?tz=Africa/Algiers"), infirmierA(), true));
        m.put("02 meme cahier lu par une secretaire : meme texte au nom de l'utilisateur pres (tout le volet medical est servi)",
                cahier(api(patient, "?tz=Africa/Algiers"), secretaireA(), true).toString().replace("u-secretaire", "u-infirmier").equals(
                        cahier(api(patient, "?tz=Africa/Algiers"), infirmierA(), true).toString()));
        arbre("complet_nouveau_code", m);
    }

    @Test
    void les_champs_vides_ou_blancs_des_volets_et_du_dossier_s_impriment_avec_un_tiret_et_les_nombres_sans_zero_final() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5V");
        UUID a = seanceEnBase(CENTRE_A, patient, "V1_BLANCS", fixe(31), "SIGNEE");
        UUID b = seanceEnBase(CENTRE_A, patient, "V2_ZEROS", fixe(32), "VALIDEE");
        UUID c = seanceEnBase(CENTRE_A, patient, "V3_TEXTES_LONGS", fixe(33), "VALIDEE");
        UUID d = seanceEnBase(CENTRE_A, patient, "V4_VOLET_VIDE", fixe(34), "VALIDEE");
        voletParamedicalComplet(CENTRE_A, a, null, null, "   ", "", null, null, null, "  ", "", "\t");
        voletParamedicalComplet(CENTRE_A, b, "0.00", "100.00", "0/0", "9/5", 0, 0, "0.50", "Aucun", "Standard", "RAS");
        voletParamedicalComplet(CENTRE_A, c, "70.10", "69.90", "13/8", "12/7", 5, 450, "1200.00", "Héparine <5000> & citrate",
                "Bicarbonate " + "très ".repeat(15) + "long", "Incident ligne 1\nIncident ligne 2 — accents éàù, apostrophe l'été, guillemets \"x\"");
        voletParamedicalComplet(CENTRE_A, d, null, null, null, null, null, null, null, null, null, null);
        voletMedical(CENTRE_A, a, "  ", null, "", "\n", null, "   ");
        voletMedical(CENTRE_A, c, "P" + "x".repeat(250), "Tolérance", "Examen\nsur deux lignes", "Hb 9,8", "Aucun", "Conclusion & suite <à voir>");
        dossierMedical(CENTRE_A, patient, "   ", null, "XYZ", "  ", "O".repeat(300), null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quatre seances : blancs, zeros, textes longs, volet sans valeur", cahier(api(patient, "?tz=UTC"), infirmierA(), true));
        arbre("volets_et_dossier_nouveau_code", m);
    }

    @Test
    void les_consommables_regroupent_les_sorties_par_article_et_prix_et_le_forfait_et_la_prescription_suivent_leurs_regles_de_repli() throws Exception {
        // patient sans prise en charge : pas de forfait ; prescriptions : egalite de jour, posterieure, dose nulle, article absent
        UUID patient = patientSe1(CENTRE_A, "PL5C");
        UUID avantPrescription = seanceEnBase(CENTRE_A, patient, "C1_AVANT_TOUTE_PRESCRIPTION", fixe(10), "VALIDEE");
        UUID memeJour = seanceEnBase(CENTRE_A, patient, "C2_MEME_JOUR_QUE_DEUX_PRESCRIPTIONS", fixe(20), "VALIDEE");
        UUID doseNulle = seanceEnBase(CENTRE_A, patient, "C3_DOSE_NULLE_ET_ARTICLE_ABSENT", fixe(30), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "C4_ARTICLES_ABSENTS_DOSES_POSITIVES", fixe(26), "VALIDEE");
        UUID epo = article(CENTRE_A, "PL5-EPO2", "Epoetine beta", "seringue");
        UUID sansLibelle = article(CENTRE_A, "PL5-SANSLIBELLE", "", "");
        UUID zebre = article(CENTRE_A, "PL5-ZEBRE", "Zebre", "kg");
        UUID abeille = article(CENTRE_A, "PL5-ABEILLE", "Abeille", "pièce");
        prescription(CENTRE_A, patient, fixe(20), T1, "71.00", epo, 2000, "SC", 1, "JOUR", null, null, null, null, null);
        prescription(CENTRE_A, patient, fixe(20), T2, "72.00", epo, 3000, "IV", 2, "HEURE", null, null, null, null, null);
        prescription(CENTRE_A, patient, fixe(25), T1, "73.00", null, 5, "SC", null, "ANNEE", null, 7, "IV", 1, "AUTRE");
        prescription(CENTRE_A, patient, fixe(30), T1, null, epo, 0, "SC", 1, "SEMAINE", null, 0, null, null, null);
        // consommables : tri par code d'article, regroupement par article et prix, prix absent, mouvements d'un autre type ou d'une autre seance
        sortieDeStock(CENTRE_A, zebre, memeJour, "SORTIE", "1", "10.00");
        sortieDeStock(CENTRE_A, abeille, memeJour, "SORTIE", "3", "2.50");
        sortieDeStock(CENTRE_A, abeille, memeJour, "SORTIE", "1", "3.00");
        sortieDeStock(CENTRE_A, abeille, memeJour, "SORTIE", "2", "2.50");
        sortieDeStock(CENTRE_A, sansLibelle, memeJour, "SORTIE", "4", null);
        sortieDeStock(CENTRE_A, zebre, memeJour, "AJUSTEMENT", "9", "10.00");
        sortieDeStock(CENTRE_A, zebre, avantPrescription, "SORTIE", "0.25", "1000.00");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cahier sans prise en charge, trois seances", cahier(api(patient, "?tz=UTC"), infirmierA(), true));
        arbre("consommables_prescription_forfait_absent_nouveau_code", m);
    }

    @Test
    void un_cahier_n_expose_aucune_donnee_d_un_autre_centre_meme_rattachee_a_la_seance_ou_au_patient() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5E");
        UUID s = seanceEnBase(CENTRE_A, patient, "E1", fixe(31), "VALIDEE");
        UUID articleA = article(CENTRE_A, "PL5-LOCAL", "Article du centre A", "boîte");
        UUID articleB = article(CENTRE_B, "PL5-LOCAL", "Article du centre B", "boîte");
        sortieDeStock(CENTRE_A, articleA, s, "SORTIE", "1", "5.00");
        sortieDeStock(CENTRE_B, articleB, s, "SORTIE", "7", "99.00");
        voletParamedicalComplet(CENTRE_B, s, "99.00", "98.00", "19/9", "19/9", 99, 99, "9999", "Autre centre", "Autre centre", "Autre centre");
        voletMedical(CENTRE_B, s, "Autre centre", "Autre centre", "Autre centre", "Autre centre", "Autre centre", "Autre centre");
        dossierMedical(CENTRE_B, patient, "Autre centre", LocalDate.of(2000, 1, 1), "PORTEUR", "PORTEUR", "Autre centre", "Autre centre");
        prescription(CENTRE_B, patient, fixe(0), T1, "99.00", articleB, 9999, "SC", 1, "JOUR", null, null, null, null, null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seance du centre A avec volets, dossier, prescription et sorties de stock rattaches au centre B : seules celles du centre A comptent",
                cahier(api(patient, "?tz=UTC"), infirmierA(), true));
        arbre("etancheite_des_centres", m);
    }

    @Test
    void le_cahier_est_ouvert_a_quatre_roles_les_autres_profils_et_l_anonyme_sont_refuses() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5D");
        seanceEnBase(CENTRE_A, patient, "D1", fixe(31), "VALIDEE");
        String avant = empreinteDesSeances();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            Map<String, String> routes = new LinkedHashMap<>();
            routes.put("01 imprimer", statutEtCode(doGet(api(patient, "?tz=UTC"), p.getValue())));
            profils.put(p.getKey(), regrouper(routes));
        }
        profils.put("anonyme", statutEtCode(doGetAnonymous(api(patient, "?tz=UTC"))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 profils", profils);
        m.put("02 aucune ecriture", empreinteDesSeances().equals(avant));
        arbre("droits_par_profil", m);
    }

    @Test
    void le_centre_est_celui_de_la_session_et_un_centerId_etranger_ou_illisible_est_refuse() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5G");
        seanceEnBase(CENTRE_A, patient, "G1", fixe(31), "VALIDEE");
        UUID patientB = patientCouvert(CENTRE_B, "PL5GB");
        seanceEnBase(CENTRE_B, patientB, "G2", fixe(31), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centerId absent (centre de session)", cahier(api(patient, "?tz=UTC"), infirmierA(), false));
        m.put("02 centerId = centre de session", cahier(api(patient, "?tz=UTC&centerId=" + CENTRE_A), infirmierA(), false));
        m.put("03 centerId = autre centre que celui de la session", cahier(api(patient, "?tz=UTC&centerId=" + CENTRE_B), infirmierA(), false));
        m.put("04 centerId inconnu", cahier(api(patient, "?tz=UTC&centerId=" + idStable("centre", "inconnu")), infirmierA(), false));
        m.put("05 centerId illisible", cahier(api(patient, "?tz=UTC&centerId=abc"), infirmierA(), false));
        m.put("06 centerId vide", cahier(api(patient, "?tz=UTC&centerId="), infirmierA(), false));
        m.put("07 patient du centre B, session A, centerId absent", cahier(api(patientB, "?tz=UTC"), infirmierA(), false));
        m.put("08 patient du centre B, session A, centerId=B", cahier(api(patientB, "?tz=UTC&centerId=" + CENTRE_B), infirmierA(), false));
        m.put("09 patient du centre B, session B", cahier(api(patientB, "?tz=UTC"), as(CENTRE_B, "MEDECIN"), false));
        m.put("10 patient du centre A, session B", cahier(api(patient, "?tz=UTC"), as(CENTRE_B, "MEDECIN"), false));
        m.put("11 ADMIN sans centre de session, centerId=A", cahier(api(patient, "?tz=UTC&centerId=" + CENTRE_A), as(null, "ADMIN"), false));
        m.put("12 ADMIN sans centre de session, centerId absent", cahier(api(patient, "?tz=UTC"), as(null, "ADMIN"), false));
        m.put("13 SUPERADMIN + ADMIN (session A) vise le centre B", cahier(api(patientB, "?tz=UTC&centerId=" + CENTRE_B), as(CENTRE_A, "SUPERADMIN", "ADMIN"),
                false));
        arbre("garde_de_centre_nouveau_code", m);
    }

    @Test
    void les_parametres_illisibles_manquants_ou_incoherents_sont_refuses_et_un_fuseau_invalide_retombe_sur_utc() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5P");
        seanceEnBase(CENTRE_A, patient, "P1", fixe(31), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "P2", fixe(40), "VALIDEE");
        UUID sans = patientCouvert(CENTRE_A, "PL5PS");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patientId illisible", cahier("/api/v1/cahier-dialyse/pas-un-uuid/imprimer", infirmierA(), false));
        m.put("02 patientId inconnu", cahier(api(idStable("patient", "inconnu"), ""), infirmierA(), false));
        m.put("03 patient sans seance", cahier(api(sans, ""), infirmierA(), false));
        m.put("04 from illisible", cahier(api(patient, "?from=pas-une-date"), infirmierA(), false));
        m.put("05 from au format jj/mm/aaaa", cahier(api(patient, "?from=31/01/2026"), infirmierA(), false));
        m.put("06 to : jour qui n'existe pas (30 fevrier)", cahier(api(patient, "?to=2026-02-30"), infirmierA(), false));
        m.put("07 from vide", cahier(api(patient, "?from="), infirmierA(), false));
        m.put("08 from posterieur a to", cahier(api(patient, "?from=" + fixe(40) + "&to=" + fixe(31)), infirmierA(), false));
        m.put("09 from seul (D+40)", cahier(api(patient, "?from=" + fixe(40)), infirmierA(), false));
        m.put("10 to seul (D+31)", cahier(api(patient, "?to=" + fixe(31)), infirmierA(), false));
        m.put("11 from=to=D+31", cahier(api(patient, "?from=" + fixe(31) + "&to=" + fixe(31)), infirmierA(), false));
        m.put("12 from en 1800 et to en 3500 (les bornes par defaut ne valent que si les parametres sont absents)",
                cahier(api(patient, "?from=1800-01-01&to=3500-01-01"), infirmierA(), false));
        m.put("13 from en l'an 3000 : aucune seance", cahier(api(patient, "?from=3000-01-01"), infirmierA(), false));
        Map<String, Object> reference = cahier(api(patient, "?tz=UTC"), infirmierA(), true);
        Map<String, Object> fuseaux = new LinkedHashMap<>();
        for (String tz : List.of("", "%20", "Nulle/Part", "Africa/Algiers", "Asia/Tokyo", "%20Asia/Tokyo%20", "+01:00", "%20UTC%20", "GMT", "europe/paris",
                "invalide")) {
            fuseaux.put("tz='" + tz + "'", decalageEnHeures(patient, tz));
        }
        m.put("14 fuseau : decalage en heures de la date d'edition par rapport a UTC (arrondi a l'heure, l'heure de l'appel n'est pas figee)", fuseaux);
        Map<String, Object> sansTz = cahier(api(patient, ""), infirmierA(), true);
        m.put("15 sans tz : meme texte que tz=UTC", sansTz.equals(reference));
        m.put("16 autre methode HTTP (POST)", statutEtCode(doPost(api(patient, ""), "{}", infirmierA())));
        arbre("parametres_nouveau_code", m);
    }

    @Test
    void l_impression_du_cahier_n_est_pas_journalisee_ni_ecrite() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PL5A");
        seanceEnBase(CENTRE_A, patient, "A1", fixe(31), "VALIDEE");
        monterLAudit();
        String avant = empreinteDesSeances();
        cahier(api(patient, "?tz=UTC"), infirmierA(), false);
        cahier(api(patient, "?tz=UTC"), secretaireA(), false);
        cahier(api(patient, "?from=pas-une-date"), infirmierA(), false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        m.put("aucune ecriture sur les seances", empreinteDesSeances().equals(avant));
        arbre("audit_nouveau_code", m);
    }
}
