package com.hemodialyse.backend.characterization.dossiermedical.dm3;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM3, lot 1 (création) : {@code POST /api/v1/patients/{id}/demandes-examen}. Données fictives, dates fixes nommées.
 */
class LaDemandeDExamenCreationCharacterizationIntegrationTest extends ExamensCharacterizationSupport {

    private static final String DEUX_LIGNES = "[{\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"codeDisplay\":\"Hemoglobine fictive\","
            + "\"libelle\":\"Hemoglobine\",\"commentaire\":\"A jeun\"},{\"libelle\":\"Examen sans code\"}]";

    @Override
    protected String dossier() {
        return "creation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm3(CENTRE_A, "PAT_A");
        patientB = patientDm3(CENTRE_B, "PAT_B");
    }

    @Test
    void une_demande_creee_est_a_l_etat_DEMANDE_avec_ses_lignes_et_sans_conclusion() throws Exception {
        deuxCentres();
        Resp r = doPost(demandesApi(patientA), corpsDemande(CENTRE_A, MEDECIN_FICTIF, fixe(3), "BIOLOGIE", true, "Controle fictif", DEUX_LIGNES),
                as(CENTRE_A, "MEDECIN"));
        aliasNommes.put(idDe(r), "<DEM>");
        ecriture("reponse", r);
        etatDesDemandes("demandes");
        etatDesLignes("lignes");
    }

    @Test
    void les_quatre_categories_sont_acceptees_et_toute_autre_valeur_est_refusee() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        for (String categorie : new String[]{"BIOLOGIE", "IMAGERIE", "FONCTIONNEL", "ANAPATH", "biologie", "RADIO", ""}) {
            cas.put("categorie '" + categorie + "'", statutEtCode(doPost(demandesApi(patientA),
                    corpsDemande(CENTRE_A, MEDECIN_FICTIF, fixe(1), categorie.isEmpty() ? "" : categorie, false, null, ligneSimple()),
                    as(CENTRE_A, "MEDECIN"))));
        }
        cas.put("categorie absente", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, MEDECIN_FICTIF, fixe(1), null, false, null, ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("categorie nulle", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"categorie\":null,\"lignes\":" + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"))));
        arbre("categories", cas);
        etatDesDemandes("demandes");
    }

    @Test
    void la_date_absente_vaut_le_jour_de_la_jvm_et_le_lendemain_est_refuse_sans_dependre_du_jour_du_centre() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp sansDate = doPost(demandesApi(patientA), corpsDemande(CENTRE_A, null, null, "BIOLOGIE", false, null, ligneSimple()),
                as(CENTRE_A, "MEDECIN"));
        // Le jour par defaut est lu sur l'horloge de la JVM (LocalDate.now()) : on controle qu'il s'agit d'un jour plausible, puis on le masque.
        Set<String> plausibles = new java.util.HashSet<>(List.of(LocalDate.now().toString(),
                LocalDate.now(ZoneId.of("Africa/Algiers")).toString(), LocalDate.now(ZoneId.of("UTC")).toString()));
        assertThat(plausibles).contains(corps(sansDate).path("dateDemande").asString());
        cas.put("sans date", Map.of("statut", sansDate.status(), "dateDemande", "<DATE_DU_JOUR>", "etat", corps(sansDate).path("statut").asString(),
                "prescripteurId", String.valueOf(corps(sansDate).path("prescripteurId"))));
        cas.put("aujourd'hui (jour de la JVM)", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, relatif(0)),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("demain (jour de la JVM + 1)", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, relatif(1)),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("hier", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, relatif(-1)), as(CENTRE_A, "MEDECIN"))));
        cas.put("dans trente ans", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, LocalDate.of(2056, 1, 1)),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("tres ancienne (1900)", statutEtCode(doPost(demandesApi(patientA), demandeOrdinaire(CENTRE_A, LocalDate.of(1900, 1, 1)),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("date illisible", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"demain\",\"categorie\":\"BIOLOGIE\",\"lignes\":" + ligneSimple() + "}",
                as(CENTRE_A, "MEDECIN"))));
        cas.put("date impossible (30 fevrier)", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"2026-02-30\",\"categorie\":\"BIOLOGIE\",\"lignes\":" + ligneSimple() + "}",
                as(CENTRE_A, "MEDECIN"))));
        arbre("dates", cas);
        // Les lignes d'etat sont triees par date : seules les dates nommees y figurent (la date du jour par defaut est masquee ci-dessus).
        assertThat(nombreDeDemandes()).isEqualTo(4);
    }

    @Test
    void les_lignes_invalides_sont_refusees_sans_rien_enregistrer() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        String[][] lignes = {
                {"liste de lignes vide", "[]"},
                {"lignes absentes", null},
                {"lignes nulles", "null"},
                {"ni code ni libelle", "[{\"commentaire\":\"x\"}]"},
                {"libelle blanc sans code", "[{\"libelle\":\"  \"}]"},
                {"code sans systeme", "[{\"code\":\"718-7\"}]"},
                {"systeme inconnu", "[{\"codeSystem\":\"INCONNU\",\"code\":\"718-7\"}]"},
                {"systeme en minuscules", "[{\"codeSystem\":\"loinc\",\"code\":\"718-7\"}]"},
                {"LOINC mal forme", "[{\"codeSystem\":\"LOINC\",\"code\":\"XYZ\"}]"},
                {"LOINC sans tiret", "[{\"codeSystem\":\"LOINC\",\"code\":\"7187\"}]"},
                {"CIM10 mal forme", "[{\"codeSystem\":\"CIM10\",\"code\":\"718-7\"}]"},
                {"code blanc avec libelle (le code est ignore)", "[{\"code\":\" \",\"libelle\":\"X\"}]"},
                {"code vide avec systeme et libelle (le systeme est ignore)", "[{\"codeSystem\":\"LOINC\",\"code\":\"\",\"libelle\":\"X\"}]"},
                {"ligne nulle", "[null]"},
                {"une ligne valide puis une invalide", "[{\"libelle\":\"Valide\"},{\"commentaire\":\"sans rien\"}]"},
        };
        for (String[] l : lignes) {
            cas.put(l[0], statutEtCode(doPost(demandesApi(patientA), corpsDemande(CENTRE_A, "m", fixe(1), "BIOLOGIE", false, null, l[1]),
                    as(CENTRE_A, "MEDECIN"))));
        }
        arbre("erreurs", cas);
        // Seules les deux lignes acceptees ("code blanc avec libelle", "code vide avec systeme et libelle") ecrivent quelque chose.
        etatDesDemandes("demandes");
        etatDesLignes("lignes");
    }

    @Test
    void le_systeme_de_codage_d_une_ligne_n_est_pas_limite_a_LOINC_et_le_code_est_normalise_en_majuscules() throws Exception {
        deuxCentres();
        String lignes = "[{\"codeSystem\":\"CIM10\",\"code\":\"n18.5\",\"codeDisplay\":\"  Maladie renale fictive  \"},"
                + "{\"codeSystem\":\"ATC\",\"code\":\"b03xa02\",\"libelle\":\"Medicament en examen\"},"
                + "{\"codeSystem\":\"LOCAL\",\"code\":\" cr-1 \",\"libelle\":\"Code local\"},"
                + "{\"codeSystem\":\"LOINC\",\"code\":\" 718-7 \"}]";
        Resp r = doPost(demandesApi(patientA), corpsDemande(CENTRE_A, "m", fixe(1), "IMAGERIE", false, null, lignes), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("lignes", demandeLisible(corps(r)).get("lignes"));
        arbre("reponse", m);
        etatDesLignes("lignes");
    }

    @Test
    void les_tailles_maximales_des_colonnes_sont_refusees_par_la_base_en_500_et_rien_n_est_enregistre() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("motif de 500 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "m", fixe(1), "BIOLOGIE", false, "x".repeat(500), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("motif de 501 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "m", fixe(1), "BIOLOGIE", false, "x".repeat(501), ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("prescripteur de 100 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "p".repeat(100), fixe(2), "BIOLOGIE", false, null, ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("prescripteur de 101 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "p".repeat(101), fixe(2), "BIOLOGIE", false, null, ligneSimple()), as(CENTRE_A, "MEDECIN"))));
        cas.put("libelle de ligne de 256 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "m", fixe(3), "BIOLOGIE", false, null, "[{\"libelle\":\"" + "l".repeat(256) + "\"}]"), as(CENTRE_A, "MEDECIN"))));
        cas.put("commentaire de ligne de 501 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "m", fixe(4), "BIOLOGIE", false, null, "[{\"libelle\":\"x\",\"commentaire\":\"" + "c".repeat(501) + "\"}]"),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("code LOCAL de 21 caracteres", statutEtCode(doPost(demandesApi(patientA),
                corpsDemande(CENTRE_A, "m", fixe(5), "BIOLOGIE", false, null, "[{\"codeSystem\":\"LOCAL\",\"code\":\"" + "k".repeat(21) + "\"}]"),
                as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        // La transaction est annulee : la demande dont une ligne depasse n'est pas enregistree (seuls les cas a 200 le sont).
        assertThat(nombreDeDemandes()).isEqualTo(2);
        assertThat(nombreDeLignes()).isEqualTo(2);
    }

    @Test
    void les_champs_facultatifs_ont_des_valeurs_par_defaut_et_le_prescripteur_n_est_pas_lie_au_compte() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("sans urgent (booleen primitif requis)", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"" + fixe(1) + "\",\"categorie\":\"ANAPATH\",\"lignes\":" + ligneSimple() + "}",
                as(CENTRE_A, "MEDECIN"))));
        Resp minimal = doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"" + fixe(1) + "\",\"categorie\":\"ANAPATH\",\"urgent\":false,\"lignes\":"
                        + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"));
        cas.put("sans motif ni prescripteur", Map.of("statut", statutEtCode(minimal), "motif", String.valueOf(texte(corps(minimal), "motif")),
                "prescripteurId", String.valueOf(texte(corps(minimal), "prescripteurId")), "urgent", corps(minimal).path("urgent").asBoolean()));
        cas.put("urgent nul", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"" + fixe(1) + "\",\"categorie\":\"ANAPATH\",\"urgent\":null,\"lignes\":"
                        + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"))));
        cas.put("urgent texte", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"dateDemande\":\"" + fixe(1) + "\",\"categorie\":\"ANAPATH\",\"urgent\":\"oui\",\"lignes\":"
                        + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"))));
        Resp autre = doPost(demandesApi(patientA), corpsDemande(CENTRE_A, "n-importe-quel-texte", fixe(2), "BIOLOGIE", true, null, ligneSimple()),
                as(CENTRE_A, "MEDECIN"));
        cas.put("prescripteur quelconque (pas le compte connecte)", Map.of("statut", autre.status(), "prescripteurId",
                corps(autre).path("prescripteurId").asString()));
        arbre("reponses", cas);
        etatDesDemandes("demandes");
    }

    @Test
    void les_corps_et_identifiants_illisibles_sont_refuses_sans_rien_enregistrer() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps vide", statutEtCode(doPost(demandesApi(patientA), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("corps mal forme", statutEtCode(doPost(demandesApi(patientA), "{pas du json", as(CENTRE_A, "MEDECIN"))));
        cas.put("corps objet vide", statutEtCode(doPost(demandesApi(patientA), "{}", as(CENTRE_A, "MEDECIN"))));
        cas.put("centre illisible", statutEtCode(doPost(demandesApi(patientA),
                "{\"centerId\":\"pas-un-uuid\",\"categorie\":\"BIOLOGIE\",\"lignes\":" + ligneSimple() + "}", as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant de patient illisible", statutEtCode(doPost(PATIENTS_API + "/pas-un-uuid/demandes-examen",
                demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"))));
        cas.put("corps tableau", statutEtCode(doPost(demandesApi(patientA), "[]", as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        assertThat(nombreDeDemandes()).isZero();
    }

    @Test
    void la_demande_peut_viser_un_patient_inconnu_ou_d_un_autre_centre_et_se_rattache_au_centre_de_la_session() throws Exception {
        deuxCentres();
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp inconnu = doPost(demandesApi(idStable("patient", "INCONNU")), demandeOrdinaire(CENTRE_A, fixe(1)), as(CENTRE_A, "MEDECIN"));
        cas.put("patient inconnu", inconnu.status());
        Resp dautre = doPost(demandesApi(patientB), demandeOrdinaire(CENTRE_A, fixe(2)), as(CENTRE_A, "MEDECIN"));
        cas.put("patient du centre B, session du centre A", dautre.status());
        arbre("reponses", cas);
        aliasNommes.put(idStable("patient", "INCONNU").toString(), "<PAT_INCONNU>");
        etatDesDemandes("demandes");
    }

    @Test
    void deux_envois_identiques_creent_deux_demandes_distinctes() throws Exception {
        deuxCentres();
        String corps = corpsDemande(CENTRE_A, MEDECIN_FICTIF, fixe(1), "BIOLOGIE", false, "Bilan fictif", ligneSimple());
        Resp un = doPost(demandesApi(patientA), corps, as(CENTRE_A, "MEDECIN"));
        Resp deux = doPost(demandesApi(patientA), corps, as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("premier", un.status());
        m.put("second", deux.status());
        m.put("identifiants distincts", !idDe(un).equals(idDe(deux)));
        m.put("lignes distinctes", !corps(un).path("lignes").get(0).path("id").asString()
                .equals(corps(deux).path("lignes").get(0).path("id").asString()));
        arbre("rejeu", m);
        assertThat(nombreDeDemandes()).isEqualTo(2);
        assertThat(nombreDeLignes()).isEqualTo(2);
    }
}
