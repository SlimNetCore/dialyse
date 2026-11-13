package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogue des notifications de la Planification (RG-NOT, ADR 0155) : types, contenu et son ordre, audience, durabilité. Sans date du jour. */
class CatalogueDesNotificationsDeLaPlanificationTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID INFIRMIER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COMPTE = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SALLE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CRENEAU = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 27);
    private static final LocalDate FIN = LocalDate.of(2026, 10, 29);

    /** La forme historique : table de hachage remplie dans l'ordre des paires (même JSON). */
    private static HashMap<String, String> contenu(String... paires) {
        var contenu = new HashMap<String, String>();
        for (int i = 0; i < paires.length; i += 2) {
            contenu.put(paires[i], paires[i + 1]);
        }
        return contenu;
    }

    private static void verifie(DemandeDeNotification demande, String type, HashMap<String, String> attendu, List<String> roles,
                                String destinataire, UUID identifiant, boolean journalisee) {
        assertEquals(CENTRE, demande.centre());
        assertEquals(type, demande.type());
        assertInstanceOf(HashMap.class, demande.contenu());
        assertEquals(attendu, demande.contenu());
        assertEquals(List.copyOf(attendu.keySet()), List.copyOf(demande.contenu().keySet()), "ordre des clés");
        assertEquals(roles, demande.audience().roles());
        assertEquals(destinataire, demande.audience().destinataire());
        assertEquals(identifiant, demande.identifiantDeterministe());
        assertEquals(journalisee, demande.journalisee());
    }

    @Test
    void sous_effectif_est_durable_pour_l_administration_le_secretariat_et_les_medecins() {
        verifie(CatalogueDesNotificationsDeLaPlanification.sousEffectif(CENTRE, 3, JOUR, ID), "INFIRMIER_SOUS_EFFECTIF",
                contenu("nbCreneaux", "3", "premiereDate", "2026-10-27", "targetRoles", "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, ID, true);
    }

    @Test
    void sureffectif_est_durable_pour_l_administrateur() {
        verifie(CatalogueDesNotificationsDeLaPlanification.sureffectif(CENTRE, 3, 4, 12, JOUR, ID), "INFIRMIER_SUREFFECTIF",
                contenu("nbCreneaux", "3", "nbVacations", "4", "heures", "12", "premiereDate", "2026-10-27", "targetRoles", "ADMIN"),
                List.of("ADMIN"), null, ID, true);
    }

    @Test
    void le_roulement_modifie_vise_le_compte_de_l_infirmier_et_l_administration() {
        verifie(CatalogueDesNotificationsDeLaPlanification.roulementModifie(CENTRE, INFIRMIER, COMPTE, EXECUTION, ID),
                "INFIRMIER_ROULEMENT_MODIFIE",
                contenu("infirmierId", INFIRMIER.toString(), "runId", EXECUTION.toString(), "targetRoles", "ADMIN", "targetUserId",
                        COMPTE.toString()),
                List.of("ADMIN"), COMPTE.toString(), ID, true);
    }

    @Test
    void le_roulement_modifie_sans_compte_ne_previent_que_l_administration() {
        verifie(CatalogueDesNotificationsDeLaPlanification.roulementModifie(CENTRE, INFIRMIER, null, EXECUTION, ID),
                "INFIRMIER_ROULEMENT_MODIFIE",
                contenu("infirmierId", INFIRMIER.toString(), "runId", EXECUTION.toString(), "targetRoles", "ADMIN"),
                List.of("ADMIN"), null, ID, true);
    }

    @Test
    void le_remplacement_affecte_vise_le_compte_de_l_infirmier_et_l_administration() {
        verifie(CatalogueDesNotificationsDeLaPlanification.remplacementAffecte(CENTRE, INFIRMIER, COMPTE, JOUR, SALLE, CRENEAU, EXECUTION, ID),
                "INFIRMIER_REMPLACEMENT_AFFECTE",
                contenu("infirmierId", INFIRMIER.toString(), "date", "2026-10-27", "salleId", SALLE.toString(), "creneauId",
                        CRENEAU.toString(), "runId", EXECUTION.toString(), "targetRoles", "ADMIN", "targetUserId", COMPTE.toString()),
                List.of("ADMIN"), COMPTE.toString(), ID, true);
    }

    @Test
    void l_absence_enregistree_previent_toujours_le_medecin_et_l_administrateur_quand_la_saisie_vient_du_secretariat() {
        verifie(CatalogueDesNotificationsDeLaPlanification.absenceEnregistree(CENTRE, "Sara Amrani", JOUR, FIN, true),
                "INFIRMIER_ABSENCE_ENREGISTREE",
                contenu("infirmier", "Sara Amrani", "debut", "2026-10-27", "fin", "2026-10-29", "targetRoles", "ADMIN,MEDECIN"),
                List.of("ADMIN", "MEDECIN"), null, null, true);
        verifie(CatalogueDesNotificationsDeLaPlanification.absenceEnregistree(CENTRE, "Sara Amrani", JOUR, FIN, false),
                "INFIRMIER_ABSENCE_ENREGISTREE",
                contenu("infirmier", "Sara Amrani", "debut", "2026-10-27", "fin", "2026-10-29", "targetRoles", "MEDECIN"),
                List.of("MEDECIN"), null, null, true);
    }

    @Test
    void l_absence_declaree_par_l_infirmier_previent_administration_secretariat_et_medecins() {
        verifie(CatalogueDesNotificationsDeLaPlanification.absenceDeclaree(CENTRE, "Sara Amrani", JOUR, FIN), "INFIRMIER_ABSENCE_DECLAREE",
                contenu("infirmier", "Sara Amrani", "debut", "2026-10-27", "fin", "2026-10-29", "targetRoles", "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, null, true);
    }

    @Test
    void l_absence_retiree_previent_les_roles_donnes() {
        verifie(CatalogueDesNotificationsDeLaPlanification.absenceRetiree(CENTRE, "Sara Amrani", JOUR, FIN, "ADMIN,SECRETAIRE,MEDECIN"),
                "INFIRMIER_ABSENCE_RETIREE",
                contenu("infirmier", "Sara Amrani", "debut", "2026-10-27", "fin", "2026-10-29", "targetRoles", "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, null, true);
        verifie(CatalogueDesNotificationsDeLaPlanification.absenceRetiree(CENTRE, "Sara Amrani", JOUR, FIN, "MEDECIN"),
                "INFIRMIER_ABSENCE_RETIREE",
                contenu("infirmier", "Sara Amrani", "debut", "2026-10-27", "fin", "2026-10-29", "targetRoles", "MEDECIN"),
                List.of("MEDECIN"), null, null, true);
    }

    @Test
    void la_liberation_en_echec_est_durable_pour_l_administrateur() {
        verifie(CatalogueDesNotificationsDeLaPlanification.liberationEnEchec(CENTRE, JOUR, 2, ID), "LIBERATION_PLACES_EN_ECHEC",
                contenu("nbPatients", "2", "jour", "2026-10-27", "targetRoles", "ADMIN"), List.of("ADMIN"), null, ID, true);
    }

    @Test
    void la_proposition_d_optimisation_est_durable_pour_l_administrateur() {
        verifie(CatalogueDesNotificationsDeLaPlanification.propositionASignaler(CENTRE, EXECUTION, "COUVERTURE", "SOUS_EFFECTIF", 3),
                "OPTIMISATION_PROPOSITION",
                contenu("runId", EXECUTION.toString(), "perimetre", "COUVERTURE", "motif", "SOUS_EFFECTIF", "valeur", "3", "targetRoles",
                        "ADMIN"),
                List.of("ADMIN"), null, null, true);
    }

    @Test
    void le_bilan_de_la_nuit_est_durable_pour_l_administrateur() {
        verifie(CatalogueDesNotificationsDeLaPlanification.bilanDeLaNuit(CENTRE, "RIEN", 0, 1), "REPLANIFICATION_NOCTURNE",
                contenu("issue", "RIEN", "propositions", "0", "echecs", "1", "targetRoles", "ADMIN"), List.of("ADMIN"), null, null, true);
    }

    @Test
    void les_seances_deplacees_previennent_le_medecin_et_le_secretariat() {
        verifie(CatalogueDesNotificationsDeLaPlanification.seancesDeplacees(CENTRE, "PATIENTS", 4, 2), "SEANCES_DEPLACEES",
                contenu("perimetre", "PATIENTS", "nbPatients", "4", "nbSeancesTemporaires", "2", "targetRoles", "MEDECIN,SECRETAIRE"),
                List.of("MEDECIN", "SECRETAIRE"), null, null, true);
    }

    @Test
    void le_generateur_indisponible_cite_les_patients_deja_choisis_et_le_total_a_part() {
        verifie(CatalogueDesNotificationsDeLaPlanification.generateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", 7,
                        List.of("BENALI Karim", "DUPONT Jean"), ID),
                "GENERATEUR_INDISPONIBLE",
                contenu("generateur", "A-G1", "statut", "HORS_SERVICE", "nbPatients", "7", "patients", "BENALI Karim, DUPONT Jean",
                        "targetRoles", "ADMIN,SECRETAIRE"),
                List.of("ADMIN", "SECRETAIRE"), null, ID, true);
        verifie(CatalogueDesNotificationsDeLaPlanification.generateurIndisponible(CENTRE, "A-G2", "EN_MAINTENANCE", 0, List.of(), ID),
                "GENERATEUR_INDISPONIBLE",
                contenu("generateur", "A-G2", "statut", "EN_MAINTENANCE", "nbPatients", "0", "patients", "", "targetRoles",
                        "ADMIN,SECRETAIRE"),
                List.of("ADMIN", "SECRETAIRE"), null, ID, true);
    }

    @Test
    void l_isolement_impossible_vise_administration_secretariat_et_medecins() {
        verifie(CatalogueDesNotificationsDeLaPlanification.isolementImpossible(CENTRE, PATIENT, "DUPONT Jean", ID), "ISOLEMENT_IMPOSSIBLE",
                contenu("patientId", PATIENT.toString(), "patientNom", "DUPONT Jean", "targetRoles", "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, ID, true);
    }

    @Test
    void le_patient_replace_en_isolement_n_a_pas_d_identifiant_deterministe() {
        verifie(CatalogueDesNotificationsDeLaPlanification.patientReplaceEnIsolement(CENTRE, PATIENT, "DUPONT Jean", "ISO-1"),
                "PATIENT_REPLACE_ISOLEMENT",
                contenu("patientId", PATIENT.toString(), "patientNom", "DUPONT Jean", "salle", "ISO-1", "targetRoles",
                        "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, null, true);
    }

    @Test
    void le_retour_en_salle_ordinaire_propose_est_durable() {
        verifie(CatalogueDesNotificationsDeLaPlanification.retourEnSalleOrdinairePropose(CENTRE, PATIENT, "DUPONT Jean", "A-2", ID),
                "RETOUR_SALLE_ORDINAIRE_PROPOSE",
                contenu("patientId", PATIENT.toString(), "patientNom", "DUPONT Jean", "salle", "A-2", "targetRoles",
                        "ADMIN,SECRETAIRE,MEDECIN"),
                List.of("ADMIN", "SECRETAIRE", "MEDECIN"), null, ID, true);
    }

    @Test
    void les_identifiants_deterministes_sont_stables_et_distincts_par_type_centre_sujet_et_jour() {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant("T", CENTRE, PATIENT, JOUR);
        assertEquals(id, CatalogueDesNotificationsDeLaPlanification.identifiant("T", CENTRE, PATIENT, JOUR));
        assertTrue(!id.equals(CatalogueDesNotificationsDeLaPlanification.identifiant("T", CENTRE, PATIENT, JOUR.plusDays(1))));
        assertTrue(!id.equals(CatalogueDesNotificationsDeLaPlanification.identifiant("U", CENTRE, PATIENT, JOUR)));
        assertEquals(UUID.nameUUIDFromBytes(("T|" + CENTRE + "|" + PATIENT + "|" + JOUR).getBytes(java.nio.charset.StandardCharsets.UTF_8)), id);
        assertEquals(UUID.nameUUIDFromBytes(("T|" + CENTRE + "|" + JOUR).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                CatalogueDesNotificationsDeLaPlanification.identifiant("T", CENTRE, JOUR));
        assertEquals(UUID.nameUUIDFromBytes("cle".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                CatalogueDesNotificationsDeLaPlanification.identifiant("cle"));
    }

    @Test
    void une_date_ou_un_identifiant_absent_fait_echouer_la_composition_comme_avant() {
        assertThrows(NullPointerException.class, () -> CatalogueDesNotificationsDeLaPlanification.sousEffectif(CENTRE, 1, null, ID));
        assertThrows(NullPointerException.class,
                () -> CatalogueDesNotificationsDeLaPlanification.isolementImpossible(CENTRE, null, "X", ID));
        assertNull(CatalogueDesNotificationsDeLaPlanification.propositionASignaler(CENTRE, EXECUTION, null, "M", 1).contenu().get("perimetre"));
    }
}
