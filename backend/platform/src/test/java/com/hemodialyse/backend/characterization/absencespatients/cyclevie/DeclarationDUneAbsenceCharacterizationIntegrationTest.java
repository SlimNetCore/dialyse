package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice absences-patients-a2-cycle-de-vie, {@code POST /api/v1/absences-patients} : declaration d'une absence (ADMIN, SECRETAIRE,
 * INFIRMIER). Ordre des controles : corps valide, centre de la session, motif lisible, patient du centre, absence existante
 * (meme annulee), periode facturee, seance realisee, date future, delai de 60 jours, commentaire. Jours relatifs au jour UTC
 * d'execution (alias {@code <J-n>}).
 */
class DeclarationDUneAbsenceCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "declaration";
    }

    @Test
    void declarer_sans_motif_cree_une_absence_a_qualifier_valorisee_au_forfait_de_la_prise_en_charge() throws Exception {
        Resp r = declarer(admin(), P_AMRANI, jour(4), null, null);
        ObjectNode o = mapper.createObjectNode();
        o.set("reponse", resume(r));
        o.set("base", etat(CENTRE_A));
        goldenNode("sans_motif", o);
    }

    @Test
    void declarer_avec_un_motif_qualifie_d_emblee_et_trace_le_declarant_comme_qualificateur() throws Exception {
        Resp r = declarer(secretaire(), P_AMRANI, jour(4), "MALADIE", "grippe");
        ObjectNode o = mapper.createObjectNode();
        o.set("reponse", resume(r));
        o.set("base", etat(CENTRE_A));
        goldenNode("avec_motif", o);
    }

    @Test
    void chacun_des_neuf_motifs_donne_son_statut_justifiee_ou_non_justifiee() throws Exception {
        ArrayNode lignes = mapper.createArrayNode();
        String[] motifs = {"HOSPITALISATION", "MALADIE", "VOYAGE", "TRANSPORT", "FAMILIAL", "REFUS_PATIENT", "DECES", "AUTRE",
                "NON_JUSTIFIEE"};
        for (int i = 0; i < motifs.length; i++) {
            Resp r = declarer(admin(), P_AMRANI, jour(i + 1), motifs[i], "AUTRE".equals(motifs[i]) ? "precision" : null);
            ObjectNode l = mapper.createObjectNode();
            l.put("motif", motifs[i]);
            l.put("status", r.status());
            l.put("statut", json(r).path("statut").asString());
            lignes.add(l);
        }
        goldenNode("neuf_motifs", lignes);
    }

    @Test
    void le_commentaire_est_rogne_et_conserve_meme_sans_motif() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("motif_et_commentaire_a_rogner", refus(declarer(admin(), P_AMRANI, jour(1), "MALADIE", "  fievre  ")));
        o.set("motif_et_commentaire_blanc", refus(declarer(admin(), P_AMRANI, jour(2), "MALADIE", "   ")));
        o.set("commentaire_sans_motif", refus(declarer(admin(), P_AMRANI, jour(3), null, "visite a domicile")));
        o.set("autre_sans_commentaire", refus(declarer(admin(), P_AMRANI, jour(4), "AUTRE", null)));
        o.set("autre_commentaire_blanc", refus(declarer(admin(), P_AMRANI, jour(5), "AUTRE", "   ")));
        o.set("autre_avec_commentaire", refus(declarer(admin(), P_AMRANI, jour(6), "AUTRE", "demenagement")));
        o.set("base", etat(CENTRE_A));
        goldenNode("commentaire_nouveau_code", o);
    }

    @Test
    void un_commentaire_de_1000_caracteres_passe_et_un_de_1001_est_refuse_en_400() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("mille", refus(declarer(admin(), P_AMRANI, jour(1), "MALADIE", "x".repeat(1000))));
        o.set("mille_un", refus(declarer(admin(), P_AMRANI, jour(2), "MALADIE", "x".repeat(1001))));
        ObjectNode compte = o.putObject("lignes_en_base");
        compte.put("total", compter(CENTRE_A));
        goldenNode("commentaire_trop_long_nouveau_code", o);
    }

    @Test
    void la_date_de_la_seance_manquee_est_bornee_entre_soixante_jours_et_aujourd_hui() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("demain", refus(declarer(admin(), P_AMRANI, jour(-1), null, null)));
        o.set("aujourd_hui", refus(declarer(admin(), P_AMRANI, jour(0), null, null)));
        o.set("il_y_a_60_jours", refus(declarer(admin(), P_AMRANI, jour(60), null, null)));
        o.set("il_y_a_61_jours", refus(declarer(admin(), P_AMRANI, jour(61), null, null)));
        o.set("il_y_a_400_jours", refus(declarer(admin(), P_AMRANI, jour(400), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("bornes_de_date", o);
    }

    @Test
    void les_refus_complets_gardent_le_code_metier_le_titre_et_le_message() throws Exception {
        golden("refus_date_future", declarer(admin(), P_AMRANI, jour(-1), null, null));
        golden("refus_delai_depasse", declarer(admin(), P_AMRANI, jour(61), null, null));
        golden("refus_patient_introuvable", declarer(admin(), UUID.randomUUID(), jour(2), null, null));
        golden("refus_autre_commentaire_requis", declarer(admin(), P_AMRANI, jour(2), "AUTRE", null));
    }

    @Test
    void seule_une_seance_realisee_le_jour_de_l_absence_la_refuse() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        String[] statuts = {"CREE", "ABSENT", "BROUILLON", "VALIDEE", "SIGNEE", "FACTUREE"};
        for (int i = 0; i < statuts.length; i++) {
            seance(P_AMRANI, CENTRE_A, jour(i + 1), statuts[i]);
            o.set("seance_" + statuts[i], refus(declarer(admin(), P_AMRANI, jour(i + 1), null, null)));
        }
        seance(P_BELKACEM, CENTRE_A, jour(8), "VALIDEE");
        o.set("seance_d_un_autre_patient_validee", refus(declarer(admin(), P_AMRANI, jour(8), null, null)));
        seance(P_CENTRE_B, CENTRE_B, jour(9), "VALIDEE");
        o.set("seance_d_un_autre_centre_validee", refus(declarer(admin(), P_AMRANI, jour(9), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("seance_realisee", o);
    }

    @Test
    void une_seance_realisee_un_autre_jour_ne_gene_pas_la_declaration() throws Exception {
        seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("veille", refus(declarer(admin(), P_AMRANI, jour(4), null, null)));
        o.set("lendemain", refus(declarer(admin(), P_AMRANI, jour(2), null, null)));
        goldenNode("seance_realisee_un_autre_jour", o);
    }

    @Test
    void une_absence_par_patient_et_par_jour_meme_annulee() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        Resp premiere = declarer(admin(), P_AMRANI, jour(4), "MALADIE", null);
        o.set("premiere", refus(premiere));
        o.set("deuxieme_meme_jour", refus(declarer(admin(), P_AMRANI, jour(4), null, null)));
        o.set("autre_patient_meme_jour", refus(declarer(admin(), P_BELKACEM, jour(4), null, null)));
        o.set("meme_patient_autre_jour", refus(declarer(admin(), P_AMRANI, jour(5), null, null)));
        UUID id = idCree(premiere);
        o.set("annulation_de_la_premiere", refus(annuler(admin(), id, "erreur de saisie")));
        o.set("redeclaration_apres_annulation", refus(declarer(admin(), P_AMRANI, jour(4), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("unicite_par_patient_et_par_jour", o);
    }

    @Test
    void une_absence_existante_prime_sur_le_delai_depasse_et_sur_la_date_future() throws Exception {
        abs(1, P_AMRANI, jour(70)).declaree().statut("JUSTIFIEE").motif("MALADIE").commentaire("ancienne").poser();
        abs(2, P_BELKACEM, jour(-2)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("existante_il_y_a_70_jours", refus(declarer(admin(), P_AMRANI, jour(70), null, null)));
        o.set("existante_dans_deux_jours", refus(declarer(admin(), P_BELKACEM, jour(-2), null, null)));
        goldenNode("precedence_absence_existante", o);
    }

    @Test
    void ordre_des_controles_patient_existante_periode_facturee_seance_realisee_puis_date() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        // patient inconnu et date future : le patient est controle en premier
        o.set("patient_inconnu_et_date_future", refus(declarer(admin(), UUID.randomUUID(), jour(-3), null, null)));
        // patient du centre B declare depuis le centre A : introuvable
        o.set("patient_du_centre_B", refus(declarer(admin(), P_CENTRE_B, jour(2), null, null)));
        // periode facturee et seance realisee le meme jour : la periode facturee est controlee avant la seance
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(10), jour(6));
        seance(P_AMRANI, CENTRE_A, jour(8), "VALIDEE");
        o.set("periode_facturee_et_seance_realisee", refus(declarer(admin(), P_AMRANI, jour(8), null, null)));
        // seance realisee il y a 70 jours : la seance est controlee avant le delai
        seance(P_BELKACEM, CENTRE_A, jour(70), "VALIDEE");
        o.set("seance_realisee_il_y_a_70_jours", refus(declarer(admin(), P_BELKACEM, jour(70), null, null)));
        // seance realisee demain : la seance est controlee avant la date future
        seance(P_CHIKHI, CENTRE_A, jour(-1), "VALIDEE");
        o.set("seance_realisee_demain", refus(declarer(admin(), P_CHIKHI, jour(-1), null, null)));
        // periode facturee et date au-dela du delai
        facture(CENTRE_A, P_DAHMANI, "FAC-2", jour(75), jour(65));
        o.set("periode_facturee_il_y_a_70_jours", refus(declarer(admin(), P_DAHMANI, jour(70), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("ordre_des_controles", o);
    }

    @Test
    void les_bornes_de_la_periode_facturee_sont_incluses_et_les_autres_patients_ne_sont_pas_bloques() throws Exception {
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(20), jour(10));
        facture(CENTRE_B, P_AMRANI, "FAC-B", jour(20), jour(10));
        ObjectNode o = mapper.createObjectNode();
        o.set("veille_du_debut", refus(declarer(admin(), P_AMRANI, jour(21), null, null)));
        o.set("premier_jour", refus(declarer(admin(), P_AMRANI, jour(20), null, null)));
        o.set("dernier_jour", refus(declarer(admin(), P_AMRANI, jour(10), null, null)));
        o.set("lendemain_de_la_fin", refus(declarer(admin(), P_AMRANI, jour(9), null, null)));
        o.set("autre_patient_meme_periode", refus(declarer(admin(), P_BELKACEM, jour(15), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("periode_facturee_bornes", o);
    }

    @Test
    void corps_invalide_motif_illisible_et_centre_demande() throws Exception {
        String ok = "'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(3) + "'";
        ObjectNode o = mapper.createObjectNode();
        o.set("patient_absent", refus(doPost(ABSENCES, json("{'dateSeance':'" + jour(3) + "'}"), admin())));
        o.set("date_absente", refus(doPost(ABSENCES, json("{'patientId':'" + P_AMRANI + "'}"), admin())));
        o.set("corps_vide", refus(doPost(ABSENCES, "{}", admin())));
        o.set("json_illisible", refus(doPost(ABSENCES, "{pas du json", admin())));
        o.set("date_illisible", refus(doPost(ABSENCES, json("{'patientId':'" + P_AMRANI + "','dateSeance':'3 mars'}"), admin())));
        o.set("patient_non_uuid", refus(doPost(ABSENCES, json("{'patientId':'abc','dateSeance':'" + jour(3) + "'}"), admin())));
        o.set("motif_inconnu", refus(doPost(ABSENCES, json("{" + ok + ",'motif':'PARESSE'}"), admin())));
        o.set("motif_en_minuscules", refus(doPost(ABSENCES, json("{" + ok + ",'motif':'maladie'}"), admin())));
        o.set("motif_vide", refus(doPost(ABSENCES, json("{" + ok + ",'motif':''}"), admin())));
        o.set("motif_blanc", refus(doPost(ABSENCES, json("{" + ok + ",'motif':' '}"), admin())));
        o.set("motif_nul", refus(doPost(ABSENCES, json("{" + ok + ",'motif':null}"), admin())));
        o.set("champ_inconnu_ignore", refus(doPost(ABSENCES, json("{" + ok + ",'extra':1}"), admin())));
        o.set("base", etat(CENTRE_A));
        goldenNode("corps_invalide_nouveau_code", o);
    }

    @Test
    void sans_type_de_contenu_json_la_declaration_est_refusee_en_415() throws Exception {
        var r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ABSENCES)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN).content("x").with(admin())).andReturn().getResponse();
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.getStatus());
        goldenNode("type_de_contenu_nouveau_code", o);
        assertThat(compter(CENTRE_A)).isZero();
    }

    @Test
    void le_patient_du_centre_B_n_est_pas_declarable_depuis_le_centre_A_et_inversement_le_centre_B_a_sa_propre_valeur() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("patient_B_par_admin_A", refus(declarer(admin(), P_CENTRE_B, jour(2), null, null)));
        Resp b = declarer(adminB(), P_CENTRE_B, jour(2), "MALADIE", null);
        o.set("patient_B_par_admin_B", resume(b));
        o.set("base_A", etat(CENTRE_A));
        o.set("base_B", etat(CENTRE_B));
        goldenNode("isolation_des_centres", o);
    }

    @Test
    void la_declaration_diffuse_une_saisie_infirmier_au_medecin_du_centre() throws Exception {
        declarer(infirmier(), P_AMRANI, jour(3), null, null);
        declarer(admin(), P_AMRANI, jour(-1), null, null);
        ObjectNode o = mapper.createObjectNode();
        o.set("diffusions", diffusionsCapturees());
        goldenNode("diffusion_temps_reel", o);
    }

    @Test
    void deux_declarations_simultanees_le_meme_jour_ne_creent_qu_une_ligne() throws Exception {
        int manches = 5;
        int vainqueurs = 0;
        int ligneUnique = 0;
        List<String> autres = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < manches; i++) {
                LocalDate jour = jour(2 + i);
                CountDownLatch depart = new CountDownLatch(1);
                List<Future<Resp>> futurs = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    futurs.add(pool.submit(() -> {
                        depart.await();
                        return doPost(ABSENCES, json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour + "','motif':'MALADIE'}"),
                                admin());
                    }));
                }
                depart.countDown();
                int crees = 0;
                for (Future<Resp> f : futurs) {
                    Resp r = f.get(60, TimeUnit.SECONDS);
                    if (r.status() == 201) crees++;
                    else autres.add(r.status() + ":" + json(r).path("code").asString(""));
                }
                if (crees == 1) vainqueurs++;
                Integer lignes = jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND date_seance = ?",
                        Integer.class, CENTRE_A, java.sql.Date.valueOf(jour));
                if (lignes != null && lignes == 1) ligneUnique++;
            }
        } finally {
            pool.shutdownNow();
        }
        // Golden : invariants stables seulement. La reponse du perdant depend de la course : ABSENCE_EXISTANTE (422) quand il
        // lit apres l'insertion du vainqueur, ABSENCE_ECRITURE_CONCURRENTE (409) quand la contrainte unique l'arrete (KB-ABS-11
        // corrige en A2 : plus d'erreur technique 500).
        System.out.println("[A2-COURSE] reponses du perdant sur " + manches + " manches : " + autres);
        assertThat(autres).allSatisfy(a -> assertThat(a).isIn("422:ABSENCE_EXISTANTE", "409:ABSENCE_ECRITURE_CONCURRENTE"));
        ObjectNode o = mapper.createObjectNode();
        o.put("manches", manches);
        o.put("manches_avec_exactement_une_creation_201", vainqueurs);
        o.put("manches_avec_exactement_une_ligne_en_base", ligneUnique);
        o.put("reponses_du_perdant_dans_l_ensemble_autorise", autres.size());
        goldenNode("declaration_simultanee", o);
    }
}
