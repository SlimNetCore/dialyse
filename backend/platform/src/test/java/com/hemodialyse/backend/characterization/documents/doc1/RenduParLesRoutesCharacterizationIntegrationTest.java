package com.hemodialyse.backend.characterization.documents.doc1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DOC1 : le rendu par {@code POST /api/v1/documents/print} (modele actif du centre + type) et
 * {@code POST /api/v1/documents/print/{modeleId}}. L'ordonnance (DM2) et la synthese de facturation (facturation/pilotage) ont
 * deja leur filet ; ici les documents generiques, le papier en-tete dans le rendu, les formats, les refus et les droits.
 */
class RenduParLesRoutesCharacterizationIntegrationTest extends Doc1Support {

    private static final String LISTE_PATIENTS = "reports/liste_patients.jrxml";

    @Test
    void la_liste_des_patients_d_un_centre_vide_ou_sans_coordonnees_s_imprime_en_pdf_tableur_et_html() throws Exception {
        modele(CENTRE_E, "LISTE_PATIENTS", LISTE_PATIENTS);
        modele(CENTRE_F, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("centre E (un patient, societe sans coordonnees) PDF (format du modele)", vue(imprimer(corps(CENTRE_E, "LISTE_PATIENTS", null, null), adminD())));
        cas.put("centre E EXCEL", vue(imprimer(corps(CENTRE_E, "LISTE_PATIENTS", "EXCEL", null), adminD())));
        cas.put("centre E HTML", vue(imprimer(corps(CENTRE_E, "LISTE_PATIENTS", "HTML", null), adminD())));
        cas.put("centre F (inconnu de l'annuaire, aucun patient) PDF", vue(imprimer(corps(CENTRE_F, "LISTE_PATIENTS", null, null), adminD())));
        cas.put("centre F EXCEL", vue(imprimer(corps(CENTRE_F, "LISTE_PATIENTS", "EXCEL", null), adminD())));
        golden("liste_patients_petits_centres", cas);
    }

    @Test
    void la_liste_des_patients_riche_porte_l_identite_du_centre_et_de_la_societe() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("PDF", vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", null, null), adminD())));
        cas.put("EXCEL", vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL", null), adminD())));
        golden("liste_patients_riche", cas);
    }

    @Test
    void les_parametres_d_identite_envoyes_par_le_client_sont_ecartes() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, String> usurpation = new LinkedHashMap<>();
        usurpation.put("SOCIETE_NOM", "Usurpation SA");
        usurpation.put("centre_nom", "Faux Centre");
        usurpation.put("SOCIETE_PIED_PAGE", "Pied falsifie");
        Map<String, Object> cas = new LinkedHashMap<>();
        var avec = vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL", usurpation), adminD()));
        var sans = vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL", null), adminD()));
        cas.put("rendu avec parametres usurpes identique au rendu sans", avec.equals(sans));
        cas.put("le rendu contient le texte usurpe", avec.toString().contains("Usurpation") || avec.toString().contains("Faux Centre")
                || avec.toString().contains("Pied falsifie"));
        cas.put("le rendu contient la societe du centre", avec.toString().contains("Societe Papier"));
        golden("identite_usurpee", cas);
    }

    @Test
    void le_parametre_center_id_du_client_remplace_celui_du_serveur_sur_la_route_generique() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("corps centre D, params CENTER_ID = centre E (tableur)", vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL",
                Map.of("CENTER_ID", CENTRE_E.toString())), adminD())));
        cas.put("corps centre D, params CENTER_ID = centre inconnu (tableur)", vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL",
                Map.of("CENTER_ID", CENTRE_F.toString())), adminD())));
        cas.put("corps centre D, params center_id minuscule (tableur) : ignore", vue(imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL",
                Map.of("center_id", CENTRE_E.toString())), adminD())).toString().contains("MARTIN"));
        golden("center_id_du_client", cas);
    }

    @Test
    void la_fiche_patient_et_l_attestation_s_impriment_pour_un_patient_du_centre() throws Exception {
        modele(CENTRE_D, "FICHE_PATIENT", "reports/fiche_patient.jrxml");
        modele(CENTRE_D, "ATTESTATION", "reports/attestation.jrxml");
        modele(CENTRE_D, "LISTE_ATTESTATIONS", "reports/liste_attestations.jrxml");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("FICHE_PATIENT pdf", vue(imprimer(corps(CENTRE_D, "FICHE_PATIENT", null, Map.of("patientId", PATIENT_1.toString())), adminD())));
        cas.put("FICHE_PATIENT tableur", vue(imprimer(corps(CENTRE_D, "FICHE_PATIENT", "EXCEL", Map.of("patientId", PATIENT_1.toString())), adminD())));
        cas.put("ATTESTATION par patientId pdf", vue(imprimer(corps(CENTRE_D, "ATTESTATION", null, Map.of("patientId", PATIENT_1.toString())), adminD())));
        cas.put("ATTESTATION par attestationId pdf", vue(imprimer(corps(CENTRE_D, "ATTESTATION", null,
                Map.of("attestationId", ATTESTATION_1.toString())), adminD())));
        cas.put("LISTE_ATTESTATIONS tableur", vue(imprimer(corps(CENTRE_D, "LISTE_ATTESTATIONS", "EXCEL", null), adminD())));
        golden("fiche_et_attestations", cas);
    }

    @Test
    void les_documents_d_un_patient_inconnu_ou_d_un_autre_centre_sont_rendus_sans_erreur() throws Exception {
        modele(CENTRE_D, "FICHE_PATIENT", "reports/fiche_patient.jrxml");
        modele(CENTRE_D, "ATTESTATION", "reports/attestation.jrxml");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("FICHE_PATIENT patient inconnu (tableur)", vue(imprimer(corps(CENTRE_D, "FICHE_PATIENT", "EXCEL",
                Map.of("patientId", "99998150-0000-0000-0000-0000000009ff")), adminD())));
        cas.put("FICHE_PATIENT patient du centre E demande au centre D (tableur)", vue(imprimer(corps(CENTRE_D, "FICHE_PATIENT", "EXCEL",
                Map.of("patientId", PATIENT_E.toString())), adminD())));
        cas.put("FICHE_PATIENT patientId illisible", refus(imprimer(corps(CENTRE_D, "FICHE_PATIENT", "EXCEL",
                Map.of("patientId", "pas-un-uuid")), adminD())));
        cas.put("ATTESTATION patient du centre E (tableur)", vue(imprimer(corps(CENTRE_D, "ATTESTATION", "EXCEL",
                Map.of("patientId", PATIENT_E.toString())), adminD())));
        golden("patient_inconnu_ou_autre_centre", cas);
    }

    @Test
    void la_liste_et_la_fiche_des_prises_en_charge_sans_donnee_s_impriment() throws Exception {
        modele(CENTRE_D, "LISTE_PEC", "reports/liste_pec.jrxml");
        modele(CENTRE_D, "PEC", "reports/prise_en_charge.jrxml");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("LISTE_PEC tableur", vue(imprimer(corps(CENTRE_D, "LISTE_PEC", "EXCEL", null), adminD())));
        cas.put("LISTE_PEC pdf", vue(imprimer(corps(CENTRE_D, "LISTE_PEC", null, null), adminD())));
        cas.put("PEC d'un patient sans prise en charge (pdf)", vue(imprimer(corps(CENTRE_D, "PEC", null,
                Map.of("patientId", PATIENT_1.toString())), adminD())));
        golden("prises_en_charge_vides", cas);
    }

    @Test
    void le_type_est_insensible_a_la_casse_et_aux_espaces_et_le_format_inconnu_donne_un_pdf() throws Exception {
        modele(CENTRE_E, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String format : List.of("pdf", "PDF", "excel", "XLS", "xlsx", "HTML", "html", "DOCX", "")) {
            Map<String, Object> v = vue(imprimer(corps(CENTRE_E, " liste_patients ", format, null), adminD()));
            cas.put("format [" + format + "]", Map.of("statut", v.get("statut"), "contentType", String.valueOf(v.get("contentType")),
                    "disposition", String.valueOf(v.get("contentDisposition"))));
        }
        golden("formats_et_casse", cas);
    }

    @Test
    void les_requetes_incompletes_ou_illisibles_sont_refusees_en_texte_brut_ou_en_500() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("centerId absent", refus(imprimer(corps(null, "LISTE_PATIENTS", null, null), adminD())));
        cas.put("typeDocument absent", refus(imprimer(corps(CENTRE_D, null, null, null), adminD())));
        cas.put("typeDocument blanc", refus(imprimer(corps(CENTRE_D, "   ", null, null), adminD())));
        cas.put("type sans modele pour le centre", refus(imprimer(corps(CENTRE_D, "INCONNU", null, null), adminD())));
        cas.put("type connu sans modele pour le centre F", refus(imprimer(corps(CENTRE_F, "LISTE_PATIENTS", null, null), adminD())));
        cas.put("modele desactive", refus(imprimerApresDesactivation()));
        cas.put("FICHE_PATIENT sans patientId", refus(imprimer(corps(CENTRE_D, "FICHE_PATIENT", null, null), adminD())));
        cas.put("FICHE_PATIENT patientId blanc", refus(imprimer(corps(CENTRE_D, "FICHE_PATIENT", null, Map.of("patientId", "  ")), adminD())));
        cas.put("ATTESTATION sans identifiant", refus(imprimer(corps(CENTRE_D, "ATTESTATION", null, null), adminD())));
        cas.put("PEC sans identifiant", refus(imprimer(corps(CENTRE_D, "PEC", null, null), adminD())));
        cas.put("ORDONNANCE sans ordonnanceId (admin)", refus(imprimer(corps(CENTRE_D, "ORDONNANCE", null, null), adminD())));
        cas.put("SYNTHESE sans periode (admin)", refus(imprimer(corps(CENTRE_D, "SYNTHESE_FACTURATION_MENSUELLE", null, null), adminD())));
        cas.put("corps absent", refus(envoyer("/api/v1/documents/print", null, adminD())));
        cas.put("json casse", refus(envoyer("/api/v1/documents/print", "{\"centerId\":", adminD())));
        cas.put("centerId illisible", refus(envoyer("/api/v1/documents/print", "{\"centerId\":\"x\",\"typeDocument\":\"LISTE_PATIENTS\"}", adminD())));
        cas.put("params de mauvais type", refus(envoyer("/api/v1/documents/print", "{\"centerId\":\"" + CENTRE_D
                + "\",\"typeDocument\":\"LISTE_PATIENTS\",\"params\":[1]}", adminD())));
        golden("requetes_refusees", cas);
    }

    private Resp imprimerApresDesactivation() throws Exception {
        modele(CENTRE_E, "LISTE_PATIENTS", LISTE_PATIENTS, "PDF", false);
        return imprimer(corps(CENTRE_E, "LISTE_PATIENTS", null, null), adminD());
    }

    @Test
    void un_gabarit_introuvable_ou_un_chemin_hors_liste_donne_un_500_en_texte() throws Exception {
        modele(CENTRE_D, "CUSTOM", "reports/inexistant.jrxml");
        modele(CENTRE_E, "CUSTOM", "../../application.properties");
        Map<String, Object> cas = new LinkedHashMap<>();
        Resp a = imprimer(corps(CENTRE_D, "CUSTOM", null, null), adminD());
        cas.put("gabarit introuvable : statut / type / prefixe", List.of(a.status, String.valueOf(a.type()),
                a.texte().length() > 28 ? a.texte().substring(0, 28) : a.texte()));
        Resp b = imprimer(corps(CENTRE_E, "CUSTOM", null, null), adminD());
        cas.put("chemin hors liste : statut / type / prefixe", List.of(b.status, String.valueOf(b.type()),
                b.texte().length() > 28 ? b.texte().substring(0, 28) : b.texte()));
        golden("gabarit_introuvable", cas);
    }

    @Test
    void les_droits_d_impression_generique_ne_dependent_que_de_la_session_pas_du_profil() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        Map<String, Object> profils = new LinkedHashMap<>();
        Map<String, RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN", adminD());
        qui.put("MEDECIN", qui(CENTRE_D, USER_MED, "MEDECIN"));
        qui.put("SECRETAIRE", qui(CENTRE_D, USER_MED, "SECRETAIRE"));
        qui.put("INFIRMIER", qui(CENTRE_D, USER_MED, "INFIRMIER"));
        qui.put("DIRECTION", qui(CENTRE_D, USER_MED, "DIRECTION"));
        qui.put("SUPERADMIN seul", qui(CENTRE_D, USER_MED, "SUPERADMIN"));
        qui.put("COMPTABLE (role inconnu)", qui(CENTRE_D, USER_MED, "COMPTABLE"));
        qui.put("aucun role", qui(CENTRE_D, USER_MED));
        qui.put("ADMIN sans centre de session", qui(null, USER_ADMIN, "ADMIN"));
        for (var p : qui.entrySet()) {
            Resp r = imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL", null), p.getValue());
            profils.put(p.getKey(), List.of(r.status, String.valueOf(r.type()), r.status == 200 ? "xlsx" : r.texte().length() > 60
                    ? masquerIds(r.texte().substring(0, 60)) : masquerIds(r.texte())));
        }
        Resp anonyme = imprimer(corps(CENTRE_D, "LISTE_PATIENTS", "EXCEL", null), null);
        profils.put("anonyme", List.of(anonyme.status, String.valueOf(anonyme.type())));
        golden("profils", profils);
    }

    @Test
    void le_centre_du_corps_n_est_pas_compare_a_celui_de_la_session_pour_les_documents_generiques() throws Exception {
        modele(CENTRE_E, "LISTE_PATIENTS", LISTE_PATIENTS);
        modele(CENTRE_E, "FICHE_PATIENT", "reports/fiche_patient.jrxml");
        RequestPostProcessor sessionD = qui(CENTRE_D, USER_MED, "MEDECIN");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("session D, liste du centre E", vue(imprimer(corps(CENTRE_E, "LISTE_PATIENTS", "EXCEL", null), sessionD)));
        cas.put("session D, fiche d'un patient du centre E", vue(imprimer(corps(CENTRE_E, "FICHE_PATIENT", "EXCEL",
                Map.of("patientId", PATIENT_E.toString())), sessionD)));
        golden("centre_du_corps_non_garde", cas);
    }

    @Test
    void l_impression_par_identifiant_de_modele_est_limitee_au_centre_du_corps() throws Exception {
        UUID dPatients = modele(CENTRE_D, "LISTE_PATIENTS", LISTE_PATIENTS);
        UUID ePatients = modele(CENTRE_E, "LISTE_PATIENTS", LISTE_PATIENTS, "EXCEL", true);
        UUID dFiche = modele(CENTRE_D, "FICHE_PATIENT", "reports/fiche_patient.jrxml");
        UUID dInexistant = modele(CENTRE_D, "CUSTOM", "reports/inexistant.jrxml");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("modele du centre (pdf)", vue(imprimerParModele(dPatients, corps(CENTRE_D, null, null, null), adminD())));
        cas.put("modele du centre, tableur impose", vue(imprimerParModele(dPatients, corps(CENTRE_D, null, "EXCEL", null), adminD())));
        cas.put("modele du centre E (format du modele : tableur) demande par le centre D", refus(imprimerParModele(ePatients,
                corps(CENTRE_D, null, null, null), adminD())));
        cas.put("modele du centre E demande avec le centre E par la session D", vue(imprimerParModele(ePatients,
                corps(CENTRE_E, null, null, null), adminD())));
        cas.put("modele inconnu", refus(imprimerParModele(UUID.fromString("99998150-0000-0000-0000-0000000009ee"),
                corps(CENTRE_D, null, null, null), adminD())));
        cas.put("centerId absent", refus(imprimerParModele(dPatients, "{\"params\":{}}", adminD())));
        cas.put("typeDocument ignore (modele de liste, type fiche demande)", vue(imprimerParModele(dPatients,
                corps(CENTRE_D, "FICHE_PATIENT", "EXCEL", null), adminD())));
        cas.put("fiche patient par modele sans patientId (aucune validation par type)", vue(imprimerParModele(dFiche,
                corps(CENTRE_D, null, "EXCEL", null), adminD())));
        Resp r = imprimerParModele(dInexistant, corps(CENTRE_D, null, null, null), adminD());
        cas.put("gabarit introuvable : statut / prefixe", List.of(r.status, r.texte().length() > 12 ? r.texte().substring(0, 12) : r.texte()));
        cas.put("identifiant de modele illisible dans le chemin", refus(envoyer("/api/v1/documents/print/pas-un-uuid",
                corps(CENTRE_D, null, null, null), adminD())));
        golden("impression_par_modele", cas);
    }
}
