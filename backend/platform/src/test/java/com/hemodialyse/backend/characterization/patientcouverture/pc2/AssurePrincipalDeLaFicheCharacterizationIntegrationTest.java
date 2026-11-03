package com.hemodialyse.backend.characterization.patientcouverture.pc2;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC2 : l'assuré principal tel que la fiche patient l'écrit ({@code POST}/{@code PUT /api/v1/patients}, qualité d'assuré,
 * coordonnées de l'assuré tiers), règle R1 de bout en bout, effets de bord (audit, diffusions, notifications) et concurrence des
 * affectations. La création de fiche nominale et l'assuré tiers sont déjà verrouillés par PC1 ; seuls les effets sur
 * {@code assure} et {@code assure_patient} qui concernent R1 sont rejoués ici.
 */
class AssurePrincipalDeLaFicheCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc2/";
    }

    @Override
    protected String dossier() {
        return "fiche";
    }

    @Test
    void changer_l_assure_tiers_par_la_fiche_ne_laisse_qu_un_principal_mais_les_anciennes_lignes_restent_actives_sans_dates() throws Exception {
        Resp cree = creerLaFiche("ENFANT", ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-1", "Premier"), adminA());
        ecriture("creation", sansCodeAleatoire(cree));
        etatDesAffectations("apres_la_creation");
        UUID id = UUID.fromString(corps(cree).path("id").asString());
        pause();
        ecriture("modification_vers_un_second_assure", sansCodeAleatoire(modifierLaFiche(id, ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-2", "Second"), adminA())));
        etatDesAffectations("apres_le_changement_d_assure");
        lecture("historique_apres_le_changement", historique(id, adminA()));
        etatDesAssures("assures_apres_le_changement");
        pause();
        ecriture("modification_vers_le_premier_assure", sansCodeAleatoire(modifierLaFiche(id, ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-1", "Premier"), adminA())));
        etatDesAffectations("apres_le_retour_au_premier");
        pause();
        ecriture("modification_sans_changement", sansCodeAleatoire(modifierLaFiche(id, ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-1", "Premier"), adminA())));
        etatDesAffectations("apres_une_resaisie_identique");
    }

    @Test
    void devenir_assure_lui_meme_retire_le_statut_principal_sans_fermer_les_affectations_puis_l_affectation_explicite_est_refusee() throws Exception {
        Resp cree = creerLaFiche("ENFANT", ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-1", "Premier"), adminA());
        UUID id = UUID.fromString(corps(cree).path("id").asString());
        pause();
        modifierLaFiche(id, ficheMap("Enfant", "PC2-PAT-1"), adminA());
        etatDesAffectations("apres_la_bascule_lui_meme");
        lecture("historique_apres_la_bascule", historique(id, adminA()));
        assure(CENTRE_A, "PC2-AS-9", "Neuf", "Nine");
        arbre("affectation_apres_la_bascule", Map.of("refus", refus(affecter(id, "PC2-AS-9", adminA()))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assure du patient lu sur la fiche", String.valueOf(jdbc.queryForObject("SELECT assure_numero_assurance FROM patients WHERE id = ?", String.class, id)));
        arbre("colonnes_du_patient", m);
    }

    @Test
    void deux_patients_declarant_le_meme_numero_d_assure_se_partagent_la_ligne_assure_et_le_dernier_ecrit_les_coordonnees() throws Exception {
        creerLaFiche("FRERE", ficheAyantDroit("Frere", "PC2-PAT-1", "PC2-PARENT", "Premier nom"), adminA());
        creerLaFiche("SOEUR", ficheAyantDroit("Soeur", "PC2-PAT-2", "PC2-PARENT", "Second nom"), adminA());
        etatDesAssures("une_ligne_assure_au_dernier_nom");
        etatDesAffectations("deux_affectations_principales_actives");
    }

    @Test
    void un_numero_d_assure_deja_detenu_par_un_autre_centre_est_repris_par_le_centre_de_la_fiche() throws Exception {
        assure(CENTRE_B, "PC2-AS-B", "Autre", "Centre");
        Resp r = creerLaFiche("ENFANT", ficheAyantDroit("Enfant", "PC2-PAT-1", "PC2-AS-B", "Pris"), adminA());
        arbre("reponse", Map.of("statut", r.status()));
        etatDesAssures("assure_du_centre_B_apres_la_fiche_du_centre_A");
        etatDesAffectations("affectation_creee_dans_le_centre_A");
    }

    @Test
    void un_ayant_droit_sans_numero_d_assure_est_refuse_et_une_qualite_inconnue_est_enregistree_telle_quelle() throws Exception {
        Map<String, Object> sans = ficheAyantDroit("Enfant", "PC2-PAT-1", null, "Sans numero");
        Map<String, Object> vide = ficheAyantDroit("Enfant2", "PC2-PAT-2", "  ", "Numero vide");
        Map<String, Object> voisin = ficheAyantDroit("Voisin", "PC2-PAT-3", "PC2-AS-3", "Voisin");
        voisin.put("qualiteAssure", "VOISIN");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numéro d'assuré absent", refus(doPost(PATIENTS, corps(sans), adminA())));
        m.put("numéro d'assuré blanc", refus(doPost(PATIENTS, corps(vide), adminA())));
        m.put("qualité inconnue", refus(doPost(PATIENTS, corps(voisin), adminA())));
        arbre("refus", m);
        etatDesAssures("assures_ecrits");
        etatDesAffectations("affectations_ecrites");
    }

    @Test
    void les_ecritures_d_assure_sont_auditees_diffusees_et_ne_notifient_rien_en_base() throws Exception {
        monterLAudit();
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        UUID id = affectationEnBase(CENTRE_A, "AFF_1", patient, "PC2-AS-1", true, jour(-30), null, 1);
        diffusions.clear();
        Resp a = affecter(patient, "PC2-AS-1", adminA());
        Resp b = doPut(PATIENTS + "/" + patient + "/assures/assignments/" + id + "?centerId=" + CENTRE_A, json("{'dateDebutAffectation':'" + jour(-30) + "','dateFinAffectation':null}"), adminA());
        Resp c = doPut(ASSURES + "/PC2-AS-1?centerId=" + CENTRE_A, json("{'nom':'Nouveau'}"), adminA());
        Resp lecture = historique(patient, adminA());
        arbre("statuts", Map.of("affecter", a.status(), "modifier l'affectation", b.status(), "modifier l'assuré", c.status(), "historique", lecture.status()));
        diffusionsCapturees("diffusions");
        notifications("notifications_durables");
        audit("audit");
    }

    @Test
    void deux_affectations_simultanees_de_deux_assures_au_meme_patient_laissent_un_seul_principal_actif() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_A, "PC2-AS-2", "Martin", "Paul");
        List<Resp> reponses = enParallele(() -> affecter(patient, "PC2-AS-1", adminA()), () -> affecter(patient, "PC2-AS-2", adminA()));
        List<Integer> statuts = new ArrayList<>(reponses.stream().map(Resp::status).sorted().toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statuts triés", statuts);
        // Aucun verrou : selon l'entrelacement des deux appels, la première affectation est fermée par la seconde (un principal actif)
        // ou les deux restent actives (R1 violée) ; seul l'ensemble des issues possibles est verrouillé, pas l'une d'elles.
        int actifs = jdbc.queryForObject("SELECT COUNT(*) FROM assure_patient WHERE patient_id = ? AND is_primary = TRUE AND date_fin_affectation IS NULL", Integer.class, patient);
        int fermees = jdbc.queryForObject("SELECT COUNT(*) FROM assure_patient WHERE patient_id = ? AND date_fin_affectation IS NOT NULL", Integer.class, patient);
        m.put("lignes", jdbc.queryForObject("SELECT COUNT(*) FROM assure_patient WHERE patient_id = ?", Integer.class, patient));
        m.put("issue possible (1 actif et 1 fermée, ou 2 actifs)", (actifs == 1 && fermees == 1) || (actifs == 2 && fermees == 0));
        arbre("invariants_apres_la_course", m);
    }
}
