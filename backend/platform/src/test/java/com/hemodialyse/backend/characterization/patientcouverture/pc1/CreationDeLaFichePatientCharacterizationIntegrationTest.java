package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * PC1 : {@code POST /api/v1/patients} (identité, mouvement d'admission, valeurs par défaut, doublons, états et dates, attestation,
 * assuré tiers). Le contrôle de la place (P5), la garde de rôles et les diffusions/audit sont déjà verrouillés par les filets P5/P7 ;
 * ils ne sont pas rejoués ici.
 */
class CreationDeLaFichePatientCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    @Override
    protected String dossier() {
        return "creation";
    }

    @Test
    void une_creation_nominale_rend_l_identifiant_et_ecrit_la_fiche_l_admission_et_l_attestation() throws Exception {
        Resp r = creer("NOUVEAU", ficheMap("Dupont", "PC1-001"), adminA());
        ecriture("reponse_de_la_creation", sansCodeAleatoire(r));
        etatDeLIdentite("fiche_ecrite");
        etatDesMouvements("mouvement_d_admission");
        etatDesAttestations("attestation_ecrite");
    }

    @Test
    void le_code_patient_est_genere_au_format_PAT_et_deux_creations_n_ont_jamais_le_meme_code() throws Exception {
        Resp a = creer("A", ficheMap("Alpha", "PC1-001"), adminA());
        Resp b = creer("B", ficheMap("Beta", "PC1-002"), adminA());
        String codeA = corps(a).path("codePatient").asString();
        String codeB = corps(b).path("codePatient").asString();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format PAT-8 hexadécimaux majuscules A", codeA.matches("PAT-[0-9A-F]{8}"));
        m.put("format PAT-8 hexadécimaux majuscules B", codeB.matches("PAT-[0-9A-F]{8}"));
        m.put("codes distincts", !codeA.equals(codeB));
        m.put("code en base identique à la réponse", jdbc.queryForObject("SELECT code_patient FROM patients WHERE id = ?", String.class,
                UUID.fromString(corps(a).path("id").asString())).equals(codeA));
        arbre("code_patient", m);
    }

    @Test
    void une_fiche_minimale_prend_les_valeurs_par_defaut_etat_permanent_sommeil_faux_zero_enfant() throws Exception {
        Map<String, Object> f = ficheMap("Minimal", "PC1-001");
        for (String cle : List.of("etatPatient", "qualiteAssure")) f.remove(cle);
        Resp r = creer("MINIMAL", f, adminA());
        ecriture("reponse", sansCodeAleatoire(r));
        etatDeLIdentite("fiche_ecrite");
        sql("autres_colonnes", "SELECT jour_lundi, jour_dimanche, sous_kt, epo_enabled, fer_enabled, salle_id, position_id, generateur_id, "
                + "medecin_traitant_id, centre_payeur_id FROM patients WHERE center_id = '" + CENTRE_A + "'");
        etatDesMouvements("mouvement");
    }

    @Test
    void un_champ_booleen_ou_entier_absent_ou_nul_du_corps_donne_une_erreur_interne_500_au_lieu_d_un_400() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> primitifs = new java.util.ArrayList<>(PRIMITIFS_BOOLEENS);
        primitifs.add("nombreEnfants");
        int i = 0;
        for (String champ : primitifs) {
            Map<String, Object> absent = ficheMap("Primitif", "PC1-PRIM-" + (++i));
            absent.remove(champ);
            m.put(champ + " absent", refus(doPost(PATIENTS, corps(absent), adminA())));
            Map<String, Object> nul = ficheMap("Primitif", "PC1-PRIM-" + (++i));
            nul.put(champ, null);
            m.put(champ + " nul", refus(doPost(PATIENTS, corps(nul), adminA())));
        }
        m.put("fiches écrites", nombreDeFiches());
        arbre("champs_primitifs_absents_nouveau_code", m);
    }

    @Test
    void chaque_champ_obligatoire_manquant_ou_vide_refuse_la_creation_sans_rien_ecrire() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Consumer<Map<String, Object>>> cas = new LinkedHashMap<>();
        for (String champ : List.of("nom", "prenom", "sexe", "dateAdmission", "dateNaissance", "numeroAssurance", "centerId")) {
            cas.put(champ + " absent", f -> f.remove(champ));
            cas.put(champ + " nul", f -> f.put(champ, null));
        }
        for (String champ : List.of("nom", "prenom", "sexe", "numeroAssurance")) {
            cas.put(champ + " vide", f -> f.put(champ, ""));
            cas.put(champ + " espaces", f -> f.put(champ, "   "));
        }
        cas.put("attestationDebut absente", f -> f.remove("attestationDebut"));
        cas.put("attestationFin absente", f -> f.remove("attestationFin"));
        cas.put("etatPatient absent, attestation absente (permanent par défaut)", f -> {
            f.remove("etatPatient");
            f.remove("attestationDebut");
            f.remove("attestationFin");
        });
        int i = 0;
        for (Map.Entry<String, Consumer<Map<String, Object>>> c : cas.entrySet()) {
            Map<String, Object> f = ficheMap("Obligatoire", "PC1-OBL-" + (++i));
            c.getValue().accept(f);
            String avant = empreinteDeLaFiche();
            Resp r = doPost(PATIENTS, corps(f), adminA());
            m.put(c.getKey(), refus(r) + (avant.equals(empreinteDeLaFiche()) ? "" : "  [A ECRIT QUELQUE CHOSE]"));
        }
        arbre("champs_obligatoires", m);
    }

    @Test
    void le_numero_d_assurance_est_unique_par_centre_a_l_identique_seulement() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("première création", refus(creer("P1", ficheMap("Premier", "PC1-DOUBLON"), adminA())));
        m.put("même numéro, même centre", refus(doPost(PATIENTS, corps(ficheMap("Second", "PC1-DOUBLON")), adminA())));
        m.put("même numéro, casse différente", refus(doPost(PATIENTS, corps(ficheMap("Troisieme", "pc1-doublon")), adminA())));
        m.put("même numéro avec espace final", refus(doPost(PATIENTS, corps(ficheMap("Quatrieme", "PC1-DOUBLON ")), adminA())));
        Map<String, Object> autreCentre = ficheMap("Cinquieme", "PC1-DOUBLON");
        autreCentre.put("centerId", CENTRE_B.toString());
        m.put("même numéro, autre centre", refus(doPost(PATIENTS, corps(autreCentre), adminB())));
        arbre("unicite_du_numero_d_assurance_nouveau_code", m);
        etatDeLIdentite("fiches_ecrites_nouveau_code");
        sql("nombre_de_mouvements_par_patient_nouveau_code","SELECT p.nom, COUNT(*) AS n FROM mouvement_patient m JOIN patients p ON p.id = m.patient_id "
                + "WHERE m.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') GROUP BY p.nom ORDER BY p.nom");
    }

    @Test
    void deux_patients_de_meme_identite_civile_sont_acceptes_aucun_controle_de_doublon_d_identite() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> a = ficheMap("Homonyme", "PC1-HOM-1");
        Map<String, Object> b = ficheMap("Homonyme", "PC1-HOM-2");
        m.put("première", refus(doPost(PATIENTS, corps(a), adminA())));
        m.put("seconde, mêmes nom, prénom, sexe et naissance", refus(doPost(PATIENTS, corps(b), adminA())));
        Map<String, Object> c = ficheMap("HOMONYME", "PC1-HOM-3");
        m.put("troisième, nom en majuscules", refus(doPost(PATIENTS, corps(c), adminA())));
        arbre("homonymes", m);
        etatDeLIdentite("fiches_ecrites");
    }

    @Test
    void l_etat_et_la_date_d_evenement_a_la_creation_sont_controles_comme_a_la_modification() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {
                {"TRANSFERE", null}, {"DECEDE", null}, {"GREFFE", null}, {"GUERRI", null},
                {"TRANSFERE", "+5"}, {"DECEDE", "+5"}, {"GREFFE", "+5"}, {"GUERRI", "+5"},
                {"TRANSFERE", "-401"}, {"PERMANENT", "-401"}, {"PERMANENT", "-400"}, {"PERMANENT", "+5"},
                {"OCCASIONNEL", null}, {"OCCASIONNEL", "+5"}, {"VACANCIER_LOCAL", "+5"}, {"VACANCIER_ETRANGER", null},
                {"BIDON", null}, {"permanent", null}, {"", null}};
        int i = 0;
        for (String[] c : cas) {
            Map<String, Object> f = ficheMap("Etat" + (++i), "PC1-ETAT-" + i);
            f.put("etatPatient", c[0]);
            if (c[1] != null) f.put("dateEvenementEtat", jour(Integer.parseInt(c[1])).toString());
            m.put("état '" + c[0] + "' date " + (c[1] == null ? "absente" : c[1].startsWith("-") ? "J" + c[1] : "J" + c[1]),
                    refus(doPost(PATIENTS, corps(f), adminA())));
        }
        arbre("etats_a_la_creation_nouveau_code", m);
        etatDeLIdentite("fiches_ecrites_nouveau_code");
        etatDesMouvements("mouvements_d_admission_nouveau_code");
    }

    @Test
    void l_attestation_n_est_exigee_que_hors_vacancier_et_n_est_pas_controlee_dans_ses_dates() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> vacancierSans = ficheMap("VacancierSans", "PC1-ATT-1");
        vacancierSans.put("etatPatient", "VACANCIER_LOCAL");
        vacancierSans.remove("attestationDebut");
        vacancierSans.remove("attestationFin");
        m.put("vacancier sans attestation", refus(doPost(PATIENTS, corps(vacancierSans), adminA())));
        Map<String, Object> vacancierAvec = ficheMap("VacancierAvec", "PC1-ATT-2");
        vacancierAvec.put("etatPatient", "VACANCIER_ETRANGER");
        m.put("vacancier avec attestation", refus(doPost(PATIENTS, corps(vacancierAvec), adminA())));
        Map<String, Object> occasionnelSans = ficheMap("OccasionnelSans", "PC1-ATT-3");
        occasionnelSans.put("etatPatient", "OCCASIONNEL");
        occasionnelSans.remove("attestationFin");
        m.put("occasionnel sans fin d'attestation", refus(doPost(PATIENTS, corps(occasionnelSans), adminA())));
        Map<String, Object> inversee = ficheMap("Inversee", "PC1-ATT-4");
        inversee.put("attestationDebut", jour(5).toString());
        inversee.put("attestationFin", jour(-5).toString());
        m.put("attestation dont la fin précède le début", refus(doPost(PATIENTS, corps(inversee), adminA())));
        Map<String, Object> echue = ficheMap("Echue", "PC1-ATT-5");
        echue.put("attestationDebut", jour(-400).toString());
        echue.put("attestationFin", jour(-7).toString());
        m.put("attestation déjà échue", refus(doPost(PATIENTS, corps(echue), adminA())));
        Map<String, Object> idImposee = ficheMap("IdImposee", "PC1-ATT-6");
        UUID attestation = UUID.nameUUIDFromBytes("pc1-attestation".getBytes());
        aliasNommes.put(attestation.toString(), "<ATTESTATION_IMPOSEE>");
        idImposee.put("attestationId", attestation.toString());
        m.put("identifiant d'attestation imposé", refus(doPost(PATIENTS, corps(idImposee), adminA())));
        arbre("attestation_nouveau_code", m);
        etatDeLIdentite("fiches_ecrites_nouveau_code");
        etatDesAttestations("attestations_ecrites_nouveau_code");
        sql("identifiant_d_attestation", "SELECT id FROM attestation_droit WHERE center_id = '" + CENTRE_A + "' AND id = '" + attestation + "'");
    }

    @Test
    void un_assure_tiers_exige_son_numero_et_cree_l_assure_et_l_affectation_principale() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> sansNumero = ficheMap("Enfant", "PC1-TIERS-1");
        sansNumero.put("qualiteAssure", "ENFANT");
        m.put("enfant sans numéro d'assuré", refus(doPost(PATIENTS, corps(sansNumero), adminA())));
        m.put("fiches écrites après le refus", nombreDeFiches());
        Map<String, Object> avec = ficheMap("Enfant", "PC1-TIERS-2");
        avec.put("qualiteAssure", "ENFANT");
        avec.put("assureNumeroAssurance", "PC1-ASSURE-1");
        avec.put("assureNom", "Parent");
        avec.put("assurePrenom", "Premier");
        avec.put("assureSexe", "F");
        avec.put("assureDateNaissance", jour(-15000).toString());
        m.put("enfant avec assuré", refus(doPost(PATIENTS, corps(avec), adminA())));
        Map<String, Object> illisible = ficheMap("Conjoint", "PC1-TIERS-3");
        illisible.put("qualiteAssure", "CONJOINT");
        illisible.put("assureNumeroAssurance", "PC1-ASSURE-2");
        illisible.put("assureNom", "Conjointe");
        illisible.put("assureDateNaissance", "pas une date");
        m.put("conjoint, date de naissance de l'assuré illisible", refus(doPost(PATIENTS, corps(illisible), adminA())));
        Map<String, Object> inconnue = ficheMap("Ascendant", "PC1-TIERS-4");
        inconnue.put("qualiteAssure", "VOISIN");
        inconnue.put("assureNumeroAssurance", "PC1-ASSURE-3");
        m.put("qualité d'assuré inconnue", refus(doPost(PATIENTS, corps(inconnue), adminA())));
        Map<String, Object> memeAssure = ficheMap("Soeur", "PC1-TIERS-5");
        memeAssure.put("qualiteAssure", "ENFANT");
        memeAssure.put("assureNumeroAssurance", "PC1-ASSURE-1");
        memeAssure.put("assureNom", "Parent modifié");
        m.put("deuxième patient sur le même assuré", refus(doPost(PATIENTS, corps(memeAssure), adminA())));
        arbre("assure_tiers", m);
        etatDeLIdentite("fiches_ecrites");
        sql("assures", "SELECT numero_assurance, center_id, nom, prenom, sexe, date_naissance FROM assure WHERE center_id IN ('" + CENTRE_A
                + "','" + CENTRE_B + "') ORDER BY numero_assurance");
        sql("affectations_principales", "SELECT p.nom, a.numero_assurance, a.is_primary, a.date_debut_affectation, a.date_fin_affectation "
                + "FROM assure_patient a JOIN patients p ON p.id = a.patient_id WHERE a.center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY p.nom, a.numero_assurance");
    }

    @Test
    void les_telephones_et_l_email_sont_valides_et_normalises_les_champs_vides_deviennent_nuls() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] telephones = {"0555123456", "0555 12-34 (56)", "0021355512345", "+213 555 12 34 56", "12345678", "1234567", "1234567890123456",
                "123456789012345", "abc", "05551234x6", "+", "++21355512345", "   ", "", "+(213)555123456"};
        int i = 0;
        for (String t : telephones) {
            for (String champ : List.of("telPersonnel", "telMobile", "telBureau")) {
                Map<String, Object> f = ficheMap("Tel" + (++i), "PC1-TEL-" + i);
                f.put(champ, t);
                Resp r = doPost(PATIENTS, corps(f), adminA());
                String stocke = r.status() == 200
                        ? String.valueOf(jdbc.queryForObject("SELECT " + champ.replaceAll("([A-Z])", "_$1").toLowerCase() + " FROM patients WHERE nom = ?",
                        String.class, "Tel" + i)) : "";
                m.put(champ + " '" + t + "'", refus(r) + (r.status() == 200 ? " -> stocké " + stocke : ""));
            }
        }
        arbre("telephones", m);
        Map<String, Object> courriels = new LinkedHashMap<>();
        String[] emails = {"a@b.co", "Jean.Dupont+x@Exemple.FR", "  a@b.co  ", "a@b", "a@b.c", "@b.co", "a b@c.co", "a@b..co", "a@@b.co", "é@b.co", "", "   "};
        for (String e : emails) {
            Map<String, Object> f = ficheMap("Mail" + (++i), "PC1-MAIL-" + i);
            f.put("email", e);
            Resp r = doPost(PATIENTS, corps(f), adminA());
            String stocke = r.status() == 200 ? String.valueOf(jdbc.queryForObject("SELECT email FROM patients WHERE nom = ?", String.class, "Mail" + i)) : "";
            courriels.put("email '" + e + "'", refus(r) + (r.status() == 200 ? " -> stocké " + stocke : ""));
        }
        arbre("courriels", courriels);
        Map<String, Object> modification = new LinkedHashMap<>();
        UUID id = patientSansPlace("FMT");
        Map<String, Object> f = ficheDuPatient("FMT");
        f.put("telMobile", "123");
        modification.put("modification avec téléphone invalide", refus(modifier(id, f, adminA())));
        f.put("telMobile", "0555123456");
        f.put("email", "pas un courriel");
        modification.put("modification avec courriel invalide", refus(modifier(id, f, adminA())));
        arbre("formats_en_modification", modification);
    }

    @Test
    void les_dates_de_naissance_et_d_admission_ne_sont_controlees_que_pour_leur_presence() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> futureNaissance = ficheMap("NaitDemain", "PC1-DATES-1");
        futureNaissance.put("dateNaissance", jour(5).toString());
        m.put("naissance dans le futur", refus(doPost(PATIENTS, corps(futureNaissance), adminA())));
        Map<String, Object> apresAdmission = ficheMap("NeApresAdmission", "PC1-DATES-2");
        apresAdmission.put("dateNaissance", jour(-300).toString());
        m.put("naissance postérieure à l'admission", refus(doPost(PATIENTS, corps(apresAdmission), adminA())));
        Map<String, Object> admissionFuture = ficheMap("AdmisDemain", "PC1-DATES-3");
        admissionFuture.put("dateAdmission", jour(7).toString());
        m.put("admission dans le futur", refus(doPost(PATIENTS, corps(admissionFuture), adminA())));
        Map<String, Object> illisible = ficheMap("DateIllisible", "PC1-DATES-4");
        illisible.put("dateNaissance", "31/12/1970");
        m.put("naissance au format jj/mm/aaaa", refus(doPost(PATIENTS, corps(illisible), adminA())));
        Map<String, Object> impossible = ficheMap("DateImpossible", "PC1-DATES-5");
        impossible.put("dateAdmission", "2026-02-30");
        m.put("admission le 30 février", refus(doPost(PATIENTS, corps(impossible), adminA())));
        arbre("dates_nouveau_code", m);
        etatDeLIdentite("fiches_ecrites_nouveau_code");
        etatDesMouvements("mouvements_nouveau_code");
    }

    @Test
    void un_corps_illisible_ou_un_type_errone_est_refuse_sans_rien_ecrire() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] corps = {"", "{", "[]", "null", "{}", "{'centerId':'pas-un-uuid'}", "{'nombreEnfants':'beaucoup'}", "{'sousKt':'peut-etre'}"};
        for (String c : corps) {
            m.put("corps " + (c.isEmpty() ? "vide" : c), refus(doPost(PATIENTS, json(c), adminA())));
        }
        m.put("empreinte inchangée", nombreDeFiches() == 0);
        arbre("corps_invalides_nouveau_code", m);
    }

    @Test
    void la_creation_refuse_un_centre_du_corps_different_de_celui_du_jeton_et_accepte_un_centre_absent_ou_non() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> autre = ficheMap("AutreCentre", "PC1-CTR-1");
        autre.put("centerId", CENTRE_B.toString());
        m.put("corps du centre B, jeton du centre A", refus(doPost(PATIENTS, corps(autre), adminA())));
        m.put("empreinte inchangée", nombreDeFiches() == 0);
        Map<String, Object> sansCentre = ficheMap("SansCentre", "PC1-CTR-2");
        sansCentre.remove("centerId");
        m.put("centre absent du corps", refus(doPost(PATIENTS, corps(sansCentre), adminA())));
        m.put("jeton sans centre, corps du centre A", refus(doPost(PATIENTS, corps(ficheMap("JetonSansCentre", "PC1-CTR-3")), as(null, "ADMIN"))));
        m.put("créée dans le centre A", jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ?", Integer.class, CENTRE_A));
        arbre("garde_de_centre", m);
    }

    @Test
    void une_creation_est_visible_aussitot_dans_la_fiche_la_recherche_les_mouvements_et_le_resume() throws Exception {
        patientSansPlace("EXISTANT");
        Resp resumeAvant = doGet(PATIENTS + "/summary?centerId=" + CENTRE_A, adminA());
        Resp r = creer("NOUVEAU", ficheMap("Nouveau", "PC1-LECT-1"), adminA());
        UUID id = UUID.fromString(corps(r).path("id").asString());
        Resp resumeApres = doGet(PATIENTS + "/summary?centerId=" + CENTRE_A, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut de la création", r.status());
        m.put("fiche", lireLaFiche(id, CENTRE_A, adminA()).status());
        m.put("recherche (total)", corps(recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':20}", adminA())).path("total").asInt());
        m.put("mouvements du centre (total)", corps(doGet(MOUVEMENTS, adminA())).path("total").asInt());
        m.put("résumé avant et après identiques (cache non vidé)", resumeAvant.raw().equals(resumeApres.raw()));
        arbre("effets_de_la_creation", m);
    }
}
