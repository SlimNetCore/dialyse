package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalogue des notifications de la Séance (PL13b, ADR 0154) : codes de type, clés et ordre du contenu (celui du JSON historique,
 * une table de hachage remplie dans cet ordre), audience, durabilité. Aucun message n'a changé par rapport à NotificationService.
 */
class CatalogueDesNotificationsDeLaSeanceTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SEANCE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);

    /** La forme historique : une table de hachage remplie dans l'ordre des paires. */
    private static Map<String, String> historique(String... paires) {
        var contenu = new HashMap<String, String>();
        for (int i = 0; i < paires.length; i += 2) contenu.put(paires[i], paires[i + 1]);
        return contenu;
    }

    private static void verifie(DemandeDeNotification demande, String type, List<String> roles, boolean journalisee, Map<String, String> contenu) {
        assertEquals(type, demande.type());
        assertEquals(CENTRE, demande.centre());
        assertEquals(roles, demande.audience().roles());
        assertNull(demande.audience().destinataire());
        assertEquals(journalisee, demande.journalisee());
        assertEquals(contenu, demande.contenu());
        assertEquals(new ArrayList<>(contenu.keySet()), new ArrayList<>(demande.contenu().keySet()), "ordre des cles du JSON");
    }

    @Test
    void une_seance_creee_previent_infirmiers_et_secretaires_sans_journal() {
        var demande = CatalogueDesNotificationsDeLaSeance.seanceCreee(CENTRE, SEANCE, PATIENT, "Dupont", "Jean", JOUR);

        verifie(demande, "SEANCE_CREATED", List.of("INFIRMIER", "SECRETAIRE"), false, historique("seanceId", SEANCE.toString(),
                "patientId", PATIENT.toString(), "patientNom", "Dupont", "patientPrenom", "Jean", "dateSeance", "2026-11-12",
                "targetRoles", "INFIRMIER,SECRETAIRE"));
        assertNull(demande.identifiantDeterministe());
    }

    @Test
    void une_seance_validee_a_le_meme_contenu_sous_son_propre_code() {
        var demande = CatalogueDesNotificationsDeLaSeance.seanceValidee(CENTRE, SEANCE, PATIENT, null, null, null);

        verifie(demande, "SEANCE_VALIDATED", List.of("INFIRMIER", "SECRETAIRE"), false, historique("seanceId", SEANCE.toString(),
                "patientId", PATIENT.toString(), "patientNom", "", "patientPrenom", "", "dateSeance", "",
                "targetRoles", "INFIRMIER,SECRETAIRE"));
    }

    @Test
    void le_volet_paramedical_enregistre_previent_infirmiers_et_secretaires() {
        var demande = CatalogueDesNotificationsDeLaSeance.voletParamedicalEnregistre(CENTRE, SEANCE, PATIENT, "Dupont", "Jean", JOUR);

        verifie(demande, "SEANCE_PARAMEDICAL_SAVED", List.of("INFIRMIER", "SECRETAIRE"), false, historique("seanceId", SEANCE.toString(),
                "patientId", PATIENT.toString(), "patientNom", "Dupont", "patientPrenom", "Jean", "dateSeance", "2026-11-12",
                "targetRoles", "INFIRMIER,SECRETAIRE"));
    }

    @Test
    void les_consommables_modifies_ne_portent_que_la_seance() {
        var demande = CatalogueDesNotificationsDeLaSeance.consommablesModifies(CENTRE, SEANCE);

        verifie(demande, "SEANCE_CONSOMMABLE_CHANGED", List.of("INFIRMIER", "SECRETAIRE"), false,
                historique("seanceId", SEANCE.toString(), "targetRoles", "INFIRMIER,SECRETAIRE"));
    }

    @Test
    void une_seance_supprimee_previent_administrateurs_infirmiers_et_secretaires() {
        var demande = CatalogueDesNotificationsDeLaSeance.seanceSupprimee(CENTRE, SEANCE, PATIENT, JOUR);

        verifie(demande, "SEANCE_SUPPRIMEE", List.of("ADMIN", "INFIRMIER", "SECRETAIRE"), false, historique("seanceId", SEANCE.toString(),
                "patientId", PATIENT.toString(), "dateSeance", "2026-11-12", "targetRoles", "ADMIN,INFIRMIER,SECRETAIRE"));
    }

    @Test
    void une_seance_supprimee_sans_jour_porte_un_jour_vide() {
        var demande = CatalogueDesNotificationsDeLaSeance.seanceSupprimee(CENTRE, SEANCE, PATIENT, null);

        assertEquals("", demande.contenu().get("dateSeance"));
    }

    @Test
    void une_seance_deverrouillee_previent_les_infirmiers_seuls() {
        var demande = CatalogueDesNotificationsDeLaSeance.seanceDeverrouillee(CENTRE, SEANCE, "Dupont", null, JOUR);

        verifie(demande, "SEANCE_DEVERROUILLEE", List.of("INFIRMIER"), false, historique("seanceId", SEANCE.toString(),
                "patientNom", "Dupont", "patientPrenom", "", "dateSeance", "2026-11-12", "targetRoles", "INFIRMIER"));
    }

    @Test
    void une_saisie_de_l_infirmier_previent_le_medecin_en_nommant_l_auteur() {
        var demande = CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(CENTRE, "PARAMEDICAL", PATIENT, "Dupont", "Jean", "inf-01", JOUR);

        verifie(demande, "SAISIE_INFIRMIER", List.of("MEDECIN"), false, historique("saisie", "PARAMEDICAL", "patientId", PATIENT.toString(),
                "patientNom", "Dupont", "patientPrenom", "Jean", "auteur", "inf-01", "date", "2026-11-12", "targetRoles", "MEDECIN"));
    }

    @Test
    void une_saisie_sans_patient_ni_auteur_ni_date_porte_des_textes_vides() {
        var demande = CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(CENTRE, "SEANCE_CREEE", null, null, null, null, null);

        assertEquals("", demande.contenu().get("patientId"));
        assertEquals("", demande.contenu().get("patientNom"));
        assertEquals("", demande.contenu().get("auteur"));
        assertEquals("", demande.contenu().get("date"));
    }

    @Test
    void chaque_type_de_saisie_de_la_seance_est_accepte_et_un_type_inconnu_est_refuse() {
        for (String type : List.of("SEANCE_CREEE", "SEANCE_VALIDEE", "PARAMEDICAL", "CONSOMMABLE_AJOUT", "CONSOMMABLE_MODIF",
                "CONSOMMABLE_RETRAIT", "ANEMIE")) {
            assertEquals(type, CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(CENTRE, type, null, null, null, null, null)
                    .contenu().get("saisie"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(CENTRE, "INCONNU", null, null, null, null, null));
    }

    @Test
    void les_seances_a_regulariser_sont_une_alerte_durable_a_identifiant_deterministe_pour_les_administrateurs() {
        UUID id = CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(CENTRE, JOUR);

        var demande = CatalogueDesNotificationsDeLaSeance.seancesARegulariser(CENTRE, 3, JOUR.minusDays(5), id);

        verifie(demande, "SEANCES_A_REGULARISER", List.of("ADMIN"), true,
                historique("nbSeances", "3", "plusAncienne", "2026-11-07", "targetRoles", "ADMIN"));
        assertEquals(id, demande.identifiantDeterministe());
    }

    @Test
    void les_seances_a_regulariser_sans_plus_ancienne_portent_un_texte_vide() {
        var demande = CatalogueDesNotificationsDeLaSeance.seancesARegulariser(CENTRE, 1, null, UUID.randomUUID());

        assertEquals("", demande.contenu().get("plusAncienne"));
    }

    @Test
    void l_identifiant_du_rappel_depend_du_centre_et_du_jour_et_reste_stable() {
        UUID id = CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(CENTRE, JOUR);

        assertEquals(id, CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(CENTRE, JOUR));
        assertNotEquals(id, CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(CENTRE, JOUR.plusDays(1)));
        assertNotEquals(id, CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(UUID.randomUUID(), JOUR));
        assertEquals(UUID.nameUUIDFromBytes(("SEANCES_A_REGULARISER|" + CENTRE + "|" + JOUR).getBytes(java.nio.charset.StandardCharsets.UTF_8)), id);
    }

    @Test
    void seul_le_rappel_est_durable() {
        assertFalse(CatalogueDesNotificationsDeLaSeance.seanceCreee(CENTRE, SEANCE, PATIENT, "", "", JOUR).journalisee());
        assertTrue(CatalogueDesNotificationsDeLaSeance.seancesARegulariser(CENTRE, 1, null, UUID.randomUUID()).journalisee());
    }

    @Test
    void une_seance_ou_un_patient_absent_leve_une_erreur_comme_avant() {
        assertThrows(NullPointerException.class, () -> CatalogueDesNotificationsDeLaSeance.seanceCreee(CENTRE, null, PATIENT, "", "", JOUR));
        assertThrows(NullPointerException.class, () -> CatalogueDesNotificationsDeLaSeance.seanceCreee(CENTRE, SEANCE, null, "", "", JOUR));
    }
}
