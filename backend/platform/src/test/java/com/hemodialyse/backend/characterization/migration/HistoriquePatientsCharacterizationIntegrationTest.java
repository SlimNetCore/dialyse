package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet S6a (ADR 0133), reprise des patients ({@code PatientMigrator}, {@code PatientRepositoryAdapter},
 * {@code AssurePatientRepositoryAdapter}, {@code PatientMapper}). Observation : reponses de
 * {@code POST /api/v1/admin/migration/batches/{lot}/import/patients} et tables {@code patients}, {@code assure_patient},
 * {@code migration_id_map}. Les generateurs sont deja verrouilles par {@code PatientsGenerateursMigrationCharacterizationIntegrationTest}.
 */
class HistoriquePatientsCharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final String ENTETE_COMPLET = "legacyId;codePatient;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;"
            + "civilite;lieuNaissance;situationFamiliale;nombreEnfants;profession;adresse;telPersonnel;telMobile;telBureau;email;"
            + "groupeSanguin;etatPatient;dateEvenementEtat;typePatient;qualiteAssure;centrePayeur;medecinTraitant;salle;creneau;"
            + "transporteurAller;transporteurRetour;joursDialyse;observation";

    /** Instantane reduit aux colonnes que les rejeux font varier. */
    private ObjectNode suivi() {
        return etatCompact(CENTRE_A, List.of("PATIENTS"), "numero_assurance, nom, qualite_assure, assure_numero_assurance, tel_mobile, "
                + "observation, adresse, assure_nom, assure_adresse, assure_tel_mobile");
    }

    private void assures(UUID lot) throws Exception {
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZP900;BENALI;Ahmed;M;;;;;", "A2;ZP901;KACI;Omar;M;;;;;"));
    }

    @Test
    void un_patient_assure_lui_meme_ou_ayant_droit_est_cree_avec_son_affectation_principale_et_rejoue_sans_doublon() throws Exception {
        UUID lot = ouvrirLot(adminA());
        assures(lot);
        String f = fichier(ENTETE_PATIENTS,
                "P1;ZP111;BENALI;Karim;Homme;15/03/1962;02/01/2019;Actif;Fils;ZP900",
                "P2;ZP222;KACI;Nadia;F;1970-05-20;2020-06-01;;Assuré;");
        ObjectNode r = mapper.createObjectNode();
        r.set("1_verification", rendu(importer(lot, "patients", f, true)));
        r.set("1_etat", etat());
        r.set("2_import", rendu(importer(lot, "patients", f, false)));
        r.set("2_etat", etat());
        r.set("3_rejeu", rendu(importer(lot, "patients", f, false)));
        r.set("3_etat", etat());
        golden("patients_assure_lui_meme_et_ayant_droit", r);
    }

    @Test
    void les_references_aux_referentiels_sont_resolues_par_code_ou_nom_et_toutes_les_colonnes_sont_ecrites() throws Exception {
        UUID lot = ouvrirLot(adminA());
        referentiels();
        ObjectNode r = mapper.createObjectNode();
        r.set("1_import_complet", rendu(importer(lot, "patients", fichier(ENTETE_COMPLET,
                "P1;PC-001;ZR111;DUPONT;Jean;M;1950-01-02;2018-05-06;M.;Alger;Marié;3;Enseignant;12 rue X;0550123456;0661123456;"
                        + "0770123456;jean.dupont@example.org;O+;Greffé;2021-02-03;Vacancier;Assuré;cp-01;Mustapha Ouaret;s1;cr1;"
                        + "ambulances rouiba;Ambulances Rouiba;Lun, Mer et Ven;Observation de reprise"), false)));
        r.set("1_etat", etat());
        r.set("2_references_introuvables", rendu(importer(lot, "patients", fichier(
                ENTETE_PATIENTS + ";centrePayeur;medecinTraitant;salle;creneau;transporteurAller;transporteurRetour;generateur",
                "P2;ZR222;MARTIN;Paul;M;1951-01-02;2018-05-06;;;;CP-99;Inconnu Docteur;S9;CR9;Taxi;Taxi2;G-99",
                "P3;ZR333;MARTIN;Luc;M;1952-01-02;2018-05-06;;;;;;SB;;;;"), false)));
        r.set("2_etat", etat());
        golden("patients_references_aux_referentiels", r);
    }

    @Test
    void les_incoherences_du_fichier_et_avec_les_patients_du_centre_sont_refusees_sans_rien_ecrire() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String entete = "legacyId;codePatient;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;dateEvenementEtat;etatPatient;qualiteAssure";
        importerOk(lot, "patients", fichier(entete,
                "P0;C-0;ZC000;DUPONT;Jean;M;1960-01-01;2018-01-01;;;",
                "P00;C-1;ZC001;MARTIN;Anne;F;1961-02-02;2018-01-01;;;"));
        ObjectNode r = mapper.createObjectNode();
        r.set("1_refus", rendu(importer(lot, "patients", fichier(entete,
                "P1;C-5;ZC100;A;B;M;2999-01-01;2018-01-01;;;",
                "P1;C-6;ZC101;A;B;M;1960-01-01;2018-01-01;;;",
                "P4;C-5;ZC102;A;B;M;1960-01-01;2018-01-01;;;",
                "P5;;ZC100;A;B;M;1960-01-01;2018-01-01;;;",
                "P0;C-0;ZC001;DUPONT;Jean;M;1960-01-01;2018-01-01;;;",
                "P7;C-1;ZC103;A;B;M;1960-01-01;2018-01-01;;;",
                "P8;;ZC104;A;B;M;1960-01-01;2018-01-01;2017-01-01;;"), false)));
        r.set("1_etat", etat());
        golden("patients_incoherences_refusees", r);
    }

    @Test
    void les_avertissements_n_empechent_pas_l_import_doublon_possible_rapprochement_par_numero_et_etat_sans_date() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String entete = "legacyId;codePatient;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;dateEvenementEtat;etatPatient;qualiteAssure";
        importerOk(lot, "patients", fichier(entete,
                "P0;C-0;ZC000;DUPONT;Jean;M;1960-01-01;2018-01-01;;;",
                "P00;C-1;ZC001;MARTIN;Anne;F;1961-02-02;2018-01-01;;;"));
        ObjectNode r = mapper.createObjectNode();
        r.set("1_import", rendu(importer(lot, "patients", fichier(entete,
                "P10;;ZC200;DUPONT;Jean;M;1960-01-01;2018-01-01;;;",
                "P11;;ZC001;MARTIN-RENOMMEE;Anne;F;1961-02-02;2018-01-01;;;",
                "P12;;ZC202;LEFEBVRE;Marc;M;1955-05-05;2018-01-01;;Décédé;"), false)));
        r.set("1_etat", etat());
        golden("patients_avertissements_et_rapprochement_par_numero", r);
    }

    @Test
    void les_formats_invalides_et_les_assures_manquants_sont_refuses_ligne_par_ligne() throws Exception {
        UUID lot = ouvrirLot(adminA());
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, created_at) VALUES ('ZX-B', ?, 'DU-CENTRE-B', CURRENT_TIMESTAMP)",
                CENTRE_B);
        viderLesCaches();
        String entete = "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;etatPatient;telMobile;email;nombreEnfants;"
                + "joursDialyse;groupeSanguin";
        String base = ";N;P;M;1950-01-01;2018-01-01;";
        ObjectNode r = mapper.createObjectNode();
        r.set("1_formats", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZF100" + base + "Inconnu;;;;;",
                "P2;ZF101;N;P;M;pas une date;2018-01-01;;;;;;",
                "P3;ZF102" + base + ";12;;;;",
                "P4;ZF103" + base + ";;pas-un-mail;;;",
                "P5;ZF104" + base + ";;;trois;;",
                "P6;ZF105" + base + ";;;;Xyz;",
                "P9;ZF108" + base + ";;;;Lunch;",
                "P7;ZF106" + base + ";;;;;ABCDEFGHIJKLMNOPQRSTU",
                "P8;;;;;;;;;;;;"), false)));
        r.set("2_assures", rendu(importer(lot, "patients", fichier(ENTETE_PATIENTS,
                "P1;ZF200;N;P;M;1950-01-01;2018-01-01;;Enfant;",
                "P2;ZF201;N;P;M;1950-01-01;2018-01-01;;Enfant;ZX-INCONNU",
                "P3;ZF202;N;P;M;1950-01-01;2018-01-01;;Conjoint;ZX-B"), false)));
        r.set("2_etat", etat());
        golden("patients_formats_invalides_et_assures_manquants", r);
    }

    @Test
    void une_valeur_inconnue_se_corrige_par_une_correspondance_et_les_synonymes_et_formats_de_date_sont_reconnus() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String f = fichier(ENTETE_PATIENTS,
                "P1;ZM100;N1;P;Homme;15/03/1962;2/1/2019;D;;",
                "P2;ZM101;N2;P;Femme;15-03-1962;2019-01-02 00:00:00;Chronique;;",
                "P3;ZM102;N3;P;H;15.03.1962;02/01/2019;Décès;;");
        ObjectNode r = mapper.createObjectNode();
        r.set("1_valeur_inconnue", rendu(importer(lot, "patients", f, true)));
        r.set("2_enregistrement", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(),
                "{'column':'etatPatient','source':'d','target':'DECEDE'}")));
        r.set("3_liste", rendu(envoyer(get(BASE + "/value-mappings"), adminA())));
        r.set("4_import", rendu(importer(lot, "patients", f, false)));
        r.set("4_etat", etat());
        r.set("5_suppression", rendu(envoyer(delete(BASE + "/value-mappings").param("column", "etatPatient").param("source", "D"),
                adminA())));
        r.set("6_liste", rendu(envoyer(get(BASE + "/value-mappings"), adminA())));
        golden("patients_correspondance_de_valeur_et_synonymes", r);
    }

    @Test
    void le_rejeu_n_efface_pas_les_valeurs_vides_change_l_assure_principal_et_recree_un_patient_supprime() throws Exception {
        UUID lot = ouvrirLot(adminA());
        assures(lot);
        String entete = "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;qualiteAssure;assureNumeroAssurance;"
                + "telMobile;observation;adresse";
        ObjectNode r = mapper.createObjectNode();
        r.set("1_import", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;Fils;ZP900;0661123456;Note initiale;12 rue X"), false)));
        r.set("1_etat", suivi());
        r.set("2_rejeu_valeurs_vides", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;Fils;ZP900;;;"), false)));
        r.set("2_etat", suivi());
        r.set("3_autre_assure", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;Fils;ZP901;;;"), false)));
        r.set("3_etat", suivi());
        r.set("4_devient_assure_lui_meme", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;Assuré;;;;"), false)));
        r.set("4_etat", suivi());
        jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
        viderLesCaches();
        r.set("5_rejeu_apres_suppression_du_patient", rendu(importer(lot, "patients", fichier(entete,
                "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;Fils;ZP900;;;"), false)));
        r.set("5_etat", suivi());
        golden("patients_rejeu_sans_effacement_et_changement_d_assure", r);
    }

    @Test
    void les_patients_et_codes_des_autres_centres_ne_sont_jamais_rapproches() throws Exception {
        UUID lot = ouvrirLot(adminA());
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, date_naissance, "
                        + "numero_assurance, type_patient, etat_patient, created_at) VALUES (?, ?, 'C-B', 'DU-CENTRE-B', 'Paul', 'M', "
                        + "DATE '2018-01-01', DATE '1960-01-01', 'ZI100', 'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)",
                UUID.fromString("99997800-0000-0000-0000-00000000b001"), CENTRE_B);
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        r.set("1_import_centre_A", rendu(importer(lot, "patients", fichier(
                "legacyId;codePatient;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission",
                "P1;C-B;ZI100;DU-CENTRE-B;Paul;M;1960-01-01;2018-01-01"), false)));
        r.set("2_etat_centre_A", etat(CENTRE_A));
        r.set("2_etat_centre_B", etat(CENTRE_B));
        golden("patients_isolement_des_centres", r);
    }
}
