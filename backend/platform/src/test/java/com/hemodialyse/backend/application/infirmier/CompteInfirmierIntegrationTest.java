package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.application.infirmier.CompteInfirmierService.CompteCree;
import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService.MonPlanning;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SituationPersonnelle;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relation infirmier ↔ compte utilisateur sur base réelle : liaison, création de compte depuis la fiche, synchronisation
 * de l'état, isolation par centre et « mon planning » (planning personnel, absences déclarées par l'infirmier).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CompteInfirmierIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99998200-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99998200-0000-0000-0000-00000000000b");
    private static final UUID SALLE = UUID.fromString("99998200-0000-0000-0000-0000000000a1");
    private static final UUID MATIN = UUID.fromString("99998200-0000-0000-0000-0000000000c1");
    private static final UUID SOCIETE = UUID.fromString("51000001-0000-0000-0000-000000000001");
    private static final String PREFIXE = "it-cpt-";
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);
    private static final LocalDate LUNDI = DIMANCHE.plusDays(1);

    @Autowired
    private InfirmierService infirmiers;
    @Autowired
    private CompteInfirmierService comptes;
    @Autowired
    private AffectationInfirmierService affectations;
    @Autowired
    private MonPlanningInfirmierService monPlanning;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private static String code(BusinessException e) {
        return e.getCode();
    }

    @BeforeEach
    void seed() {
        cleanup();
        // app_user_center référence centers : les deux centres de test doivent exister
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'CP-C1', 'Centre test', ?, TRUE)",
                CENTRE, SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'CP-C2', 'Autre centre test', ?, TRUE)",
                AUTRE_CENTRE, SOCIETE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'CP-S1', 'Salle test')", SALLE, CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'CP1', 'Matin')", MATIN, CENTRE);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("infirmier_absence", "infirmier_affectation", "infirmier")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        }
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE ?)", PREFIXE + "%");
        jdbc.update("DELETE FROM app_user_center WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE ?)", PREFIXE + "%");
        jdbc.update("DELETE FROM app_user WHERE username LIKE ?", PREFIXE + "%");
        jdbc.update("DELETE FROM position_creneau WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM salle WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    private UUID fiche(UUID centre, String matricule) {
        return infirmiers.creer(centre, matricule, "Amrani", "Sara", null, QualificationInfirmier.INFIRMIER, false)
                .infirmier().id();
    }

    /**
     * Compte existant doté du rôle INFIRMIER et rattaché au centre.
     */
    private UUID compteExistant(UUID centre, String username, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active, created_at) "
                        + "VALUES (?, ?, 'x', NULL, ?, TRUE, ?)", id, username, "Compte " + username,
                OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = ?", id, role);
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", id, centre);
        return id;
    }

    private boolean compteActif(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM app_user WHERE id = ?", Boolean.class, userId));
    }

    @Test
    void creating_an_account_from_the_file_links_it_with_the_nurse_role_and_a_hashed_temporary_password() {
        UUID fiche = fiche(CENTRE, "M1");

        CompteCree cree = comptes.creerEtLier(CENTRE, fiche, PREFIXE + "amrani", "sara@example.dz");

        CompteRef compte = cree.infirmier().compte();
        assertEquals(PREFIXE + "amrani", compte.username());
        assertEquals("Sara Amrani", compte.nomComplet());
        assertEquals(compte.id(), cree.infirmier().infirmier().userId());
        assertEquals(GenerateurMotDePasseTemporaire.LONGUEUR, cree.motDePasseTemporaire().length());
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, compte.id());
        assertNotEquals(cree.motDePasseTemporaire(), hash);
        assertTrue(passwordEncoder.matches(cree.motDePasseTemporaire(), hash));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT must_change_password FROM app_user WHERE id = ?", Boolean.class, compte.id()), "le mot de passe temporaire doit être remplacé à la première connexion");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_role ur JOIN app_role r ON r.id = ur.role_id "
                + "WHERE ur.user_id = ? AND r.code = 'INFIRMIER'", Integer.class, compte.id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_center WHERE user_id = ? AND center_id = ?",
                Integer.class, compte.id(), CENTRE));
        assertEquals(compte.username(), infirmiers.lister(CENTRE, 0, 20).items().get(0).compte().username());
    }

    @Test
    void account_creation_is_refused_for_an_invalid_or_existing_login_a_linked_or_inactive_file() {
        UUID fiche = fiche(CENTRE, "M1");

        assertEquals("COMPTE_IDENTIFIANT_INVALIDE", code(assertThrows(BusinessException.class,
                () -> comptes.creerEtLier(CENTRE, fiche, "a b", null))));
        compteExistant(CENTRE, PREFIXE + "pris", "INFIRMIER");
        assertEquals("COMPTE_IDENTIFIANT_EXISTANT", code(assertThrows(BusinessException.class,
                () -> comptes.creerEtLier(CENTRE, fiche, PREFIXE + "PRIS", null))));

        comptes.creerEtLier(CENTRE, fiche, PREFIXE + "ok", null);
        assertEquals("COMPTE_DEJA_LIE", code(assertThrows(BusinessException.class,
                () -> comptes.creerEtLier(CENTRE, fiche, PREFIXE + "autre", null))));

        UUID inactive = fiche(CENTRE, "M2");
        infirmiers.desactiver(CENTRE, inactive);
        assertEquals("COMPTE_ETAT_INCOHERENT", code(assertThrows(BusinessException.class,
                () -> comptes.creerEtLier(CENTRE, inactive, PREFIXE + "inactive", null))));
    }

    @Test
    void an_existing_nurse_account_of_the_center_can_be_linked_once_and_listed_until_then() {
        UUID fiche = fiche(CENTRE, "M1");
        UUID autreFiche = fiche(CENTRE, "M2");
        UUID compte = compteExistant(CENTRE, PREFIXE + "libre", "INFIRMIER");
        compteExistant(CENTRE, PREFIXE + "secretaire", "SECRETAIRE");
        compteExistant(AUTRE_CENTRE, PREFIXE + "etranger", "INFIRMIER");

        PagedResult<CompteRef> avant = comptes.comptesLiables(CENTRE, 0, 20);
        assertEquals(List.of(PREFIXE + "libre"), avant.items().stream().map(CompteRef::username).toList());

        InfirmierDetail lie = comptes.lier(CENTRE, fiche, compte);
        assertEquals(compte, lie.infirmier().userId());
        assertEquals(PREFIXE + "libre", lie.compte().username());
        assertTrue(comptes.comptesLiables(CENTRE, 0, 20).items().isEmpty());

        assertEquals("COMPTE_DEJA_UTILISE", code(assertThrows(BusinessException.class,
                () -> comptes.lier(CENTRE, autreFiche, compte))));
        assertEquals("COMPTE_DEJA_LIE", code(assertThrows(BusinessException.class,
                () -> comptes.lier(CENTRE, fiche, compte))));
    }

    @Test
    void accounts_of_another_center_or_without_the_nurse_role_cannot_be_linked() {
        UUID fiche = fiche(CENTRE, "M1");
        UUID etranger = compteExistant(AUTRE_CENTRE, PREFIXE + "etranger", "INFIRMIER");
        UUID secretaire = compteExistant(CENTRE, PREFIXE + "secretaire", "SECRETAIRE");

        assertEquals("COMPTE_INTROUVABLE", code(assertThrows(BusinessException.class,
                () -> comptes.lier(CENTRE, fiche, etranger))));
        assertEquals("COMPTE_INTROUVABLE", code(assertThrows(BusinessException.class,
                () -> comptes.lier(CENTRE, fiche, secretaire))));
        assertEquals("INFIRMIER_INTROUVABLE", code(assertThrows(BusinessException.class,
                () -> comptes.lier(AUTRE_CENTRE, fiche, etranger))));
    }

    @Test
    void a_link_between_an_active_account_and_an_inactive_file_is_refused_and_unlinking_keeps_both() {
        UUID fiche = fiche(CENTRE, "M1");
        UUID compte = compteExistant(CENTRE, PREFIXE + "actif", "INFIRMIER");
        infirmiers.desactiver(CENTRE, fiche);

        assertEquals("COMPTE_ETAT_INCOHERENT", code(assertThrows(BusinessException.class,
                () -> comptes.lier(CENTRE, fiche, compte))));

        infirmiers.reactiver(CENTRE, fiche);
        comptes.lier(CENTRE, fiche, compte);
        InfirmierDetail delie = comptes.delier(CENTRE, fiche);
        assertNull(delie.infirmier().userId());
        assertNull(delie.compte());
        assertTrue(compteActif(compte));
    }

    @Test
    void deactivating_the_file_deactivates_the_account_and_deactivating_the_account_deactivates_the_file() {
        UUID fiche = fiche(CENTRE, "M1");
        UUID compte = comptes.creerEtLier(CENTRE, fiche, PREFIXE + "sync", null).infirmier().compte().id();

        infirmiers.desactiver(CENTRE, fiche);
        assertFalse(compteActif(compte));
        infirmiers.reactiver(CENTRE, fiche);
        assertTrue(compteActif(compte));

        comptes.surChangementEtatCompte(compte, false);
        assertFalse(infirmiers.lister(CENTRE, 0, 20).items().get(0).infirmier().actif());
        comptes.surChangementEtatCompte(compte, true);
        assertTrue(infirmiers.lister(CENTRE, 0, 20).items().get(0).infirmier().actif());
    }

    @Test
    void deleting_the_account_keeps_the_file_without_account() {
        UUID fiche = fiche(CENTRE, "M1");
        UUID compte = comptes.creerEtLier(CENTRE, fiche, PREFIXE + "supprime", null).infirmier().compte().id();

        comptes.surSuppressionCompte(compte);

        InfirmierDetail detail = infirmiers.lister(CENTRE, 0, 20).items().get(0);
        assertNull(detail.infirmier().userId());
        assertNull(detail.compte());
    }

    @Test
    void my_planning_lists_my_own_slots_only_and_requires_a_linked_account() {
        UUID moi = fiche(CENTRE, "M1");
        UUID autre = fiche(CENTRE, "M2");
        UUID mesComptes = comptes.creerEtLier(CENTRE, moi, PREFIXE + "moi", null).infirmier().compte().id();
        affectations.ajouter(CENTRE, moi, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        affectations.ajouter(CENTRE, autre, SALLE, MATIN, EnumSet.of(JourSemaine.MARDI));

        MonPlanning planning = monPlanning.planning(CENTRE, mesComptes, DIMANCHE.plusDays(3));

        assertEquals(DIMANCHE, planning.debut());
        assertEquals(1, planning.mesCreneaux().size());
        assertEquals(LUNDI, planning.mesCreneaux().get(0).date());
        assertEquals(SituationPersonnelle.PREVU, planning.mesCreneaux().get(0).situation());
        assertEquals("Sara Amrani", planning.infirmier().infirmier().nomComplet());

        UUID sansFiche = compteExistant(CENTRE, PREFIXE + "sans-fiche", "INFIRMIER");
        assertEquals("INFIRMIER_NON_LIE", code(assertThrows(BusinessException.class,
                () -> monPlanning.mesAbsences(CENTRE, sansFiche, 0, 20))));
        assertEquals("INFIRMIER_NON_LIE", code(assertThrows(BusinessException.class,
                () -> monPlanning.planning(AUTRE_CENTRE, mesComptes, LUNDI))));
    }

    @Test
    void a_nurse_declares_future_absences_which_uncover_his_slot_and_can_cancel_only_those_not_started() {
        UUID moi = fiche(CENTRE, "M1");
        UUID mesComptes = comptes.creerEtLier(CENTRE, moi, PREFIXE + "abs", null).infirmier().compte().id();
        affectations.ajouter(CENTRE, moi, SALLE, MATIN, EnumSet.of(JourSemaine.LUNDI));
        LocalDate aujourdhui = LUNDI.minusDays(3);

        AbsenceInfirmier absence = monPlanning.declarer(CENTRE, mesComptes, aujourdhui, LUNDI, LUNDI, TypeAbsence.MALADIE, "Grippe");

        assertEquals(moi, absence.infirmierId());
        assertEquals(SituationPersonnelle.ABSENT,
                monPlanning.planning(CENTRE, mesComptes, LUNDI).mesCreneaux().get(0).situation());
        assertEquals(1, monPlanning.mesAbsences(CENTRE, mesComptes, 0, 20).total());

        assertEquals("ABSENCE_PASSEE", code(assertThrows(BusinessException.class, () -> monPlanning.declarer(
                CENTRE, mesComptes, aujourdhui, aujourdhui.minusDays(5), aujourdhui.minusDays(1), TypeAbsence.CONGE, null))));
        assertEquals("ABSENCE_NON_ANNULABLE", code(assertThrows(BusinessException.class,
                () -> monPlanning.annuler(CENTRE, mesComptes, LUNDI, absence.id()))));

        monPlanning.annuler(CENTRE, mesComptes, aujourdhui, absence.id());
        assertEquals(0, monPlanning.mesAbsences(CENTRE, mesComptes, 0, 20).total());
    }

    @Test
    void a_nurse_cannot_cancel_the_absence_of_a_colleague() {
        UUID moi = fiche(CENTRE, "M1");
        UUID collegue = fiche(CENTRE, "M2");
        UUID mesComptes = comptes.creerEtLier(CENTRE, moi, PREFIXE + "moi2", null).infirmier().compte().id();
        UUID sesComptes = comptes.creerEtLier(CENTRE, collegue, PREFIXE + "lui", null).infirmier().compte().id();
        LocalDate aujourdhui = LUNDI.minusDays(3);
        AbsenceInfirmier sienne = monPlanning.declarer(CENTRE, sesComptes, aujourdhui, LUNDI, LUNDI, TypeAbsence.CONGE, null);

        assertEquals("ABSENCE_INTROUVABLE", code(assertThrows(BusinessException.class,
                () -> monPlanning.annuler(CENTRE, mesComptes, aujourdhui, sienne.id()))));
        assertEquals(0, monPlanning.mesAbsences(CENTRE, mesComptes, 0, 20).total());
        assertEquals(1, monPlanning.mesAbsences(CENTRE, sesComptes, 0, 20).total());
    }
}
