package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyidentite.ComptesUtilisateursJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.InfirmiersJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDuReferentielDesInfirmiersJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du référentiel des infirmiers contre les adaptateurs JDBC (H2 et PostgreSQL 16 partagent ces tests) : les dépôts passent
 * le contrat des doubles, plus ce qui n'a de sens que sur une vraie base : contrainte d'unicité (409), mot de passe haché, rôle
 * absent, lecture du roulement de P3, verrou du référentiel.
 */
public abstract class InfirmiersJdbcContrat extends InfirmiersContrat {

    private static final PasswordEncoder ENCODEUR = new PasswordEncoder() {
        @Override
        public String encode(CharSequence brut) {
            return "HACHE:" + brut;
        }

        @Override
        public boolean matches(CharSequence brut, String hache) {
            return hache.equals(encode(brut));
        }
    };

    protected abstract JdbcTemplate jdbc();

    protected abstract TransactionTemplate transaction();

    /** Pose les lignes des deux centres de test (la ligne que le verrou du référentiel prend). */
    protected abstract void poserLesCentres();

    @Override
    protected InfirmiersRepository infirmiers() {
        return new InfirmiersJdbc(jdbc());
    }

    @Override
    protected ComptesUtilisateurs comptes() {
        return new ComptesUtilisateursJdbc(jdbc(), ENCODEUR);
    }

    protected VerrouDuReferentielDesInfirmiers verrou() {
        return new VerrouDuReferentielDesInfirmiersJdbc(jdbc());
    }

    private void roleInfirmier() {
        if (jdbc().queryForObject("SELECT COUNT(*) FROM app_role WHERE code = 'INFIRMIER'", Integer.class) == 0) {
            jdbc().update("INSERT INTO app_role (id, code, name) VALUES (?, 'INFIRMIER', 'Infirmier')", UUID.randomUUID());
        }
    }

    @Override
    protected CompteId unCompte(String identifiant, String nomComplet, boolean actif, CentreId... centres) {
        roleInfirmier();
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO app_user (id, username, password_hash, full_name, active) VALUES (?, ?, 'x', ?, ?)", id,
                identifiant, nomComplet, actif);
        jdbc().update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = 'INFIRMIER'", id);
        for (CentreId centre : centres) {
            jdbc().update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", id, centre.valeur());
        }
        return new CompteId(id);
    }

    // ------------------------------------------------------------------ ce qui n'a de sens que sur une vraie base

    @Test
    void should_answer_a_concurrent_write_when_the_same_matricule_is_registered_twice_in_the_same_case() {
        infirmiers().enregistrer(fiche(CENTRE, "M-001", "Amrani", "Sara"));

        assertThatThrownBy(() -> infirmiers().enregistrer(fiche(CENTRE, "M-001", "Benali", "Karim")))
                .isInstanceOf(EcritureConcurrente.class);
    }

    @Test
    void should_read_a_matricule_stored_in_lower_case_by_the_old_code_without_changing_it() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO infirmier (id, center_id, matricule, nom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, 'm-ancien', 'Ancien', 'INFIRMIER', FALSE, TRUE)", id, CENTRE.valeur());

        Infirmier lue = infirmiers().pourEcriture(CENTRE, new InfirmierId(id)).orElseThrow();
        infirmiers().enregistrer(lue.desactiver());

        assertThat(lue.matricule().valeur()).isEqualTo("m-ancien");
        assertThat(jdbc().queryForObject("SELECT matricule FROM infirmier WHERE id = ?", String.class, id)).isEqualTo("m-ancien");
    }

    @Test
    void should_store_the_temporary_password_hashed_and_force_its_change_at_first_login() {
        roleInfirmier();

        ComptesUtilisateurs.Compte cree = comptes().creerCompteInfirmier(CENTRE, IdentifiantDeConnexion.depuisLaSaisie("s.amrani"),
                "Mot2passe!Temp", "Sara Amrani", null);

        assertThat(jdbc().queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, cree.id().valeur()))
                .isEqualTo("HACHE:Mot2passe!Temp");
        assertThat(jdbc().queryForObject("SELECT must_change_password FROM app_user WHERE id = ?", Boolean.class, cree.id().valeur()))
                .isTrue();
        assertThat(jdbc().queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, cree.id().valeur())).isNull();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM app_user_role ur JOIN app_role r ON r.id = ur.role_id "
                + "WHERE ur.user_id = ? AND r.code = 'INFIRMIER'", Integer.class, cree.id().valeur())).isEqualTo(1);
    }

    @Test
    void should_refuse_a_login_created_in_the_meantime_and_a_missing_nurse_role() {
        unCompte("s.amrani", "Existant", true, AUTRE);

        assertThatThrownBy(() -> comptes().creerCompteInfirmier(CENTRE, IdentifiantDeConnexion.depuisLaSaisie("s.amrani"), "x",
                "Sara", null)).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo("COMPTE_IDENTIFIANT_EXISTANT");
        jdbc().update("UPDATE app_role SET code = 'INFIRMIER_ABSENT' WHERE code = 'INFIRMIER'");
        try {
            assertThatThrownBy(() -> comptes().creerCompteInfirmier(CENTRE, IdentifiantDeConnexion.depuisLaSaisie("autre.login"),
                    "x", "Sara", null)).isInstanceOf(IllegalStateException.class);
        } finally {
            jdbc().update("UPDATE app_role SET code = 'INFIRMIER' WHERE code = 'INFIRMIER_ABSENT'");
        }
    }

    @Test
    void should_read_the_rotation_of_the_nurses_by_slot_with_the_days_in_week_order() {
        InfirmierId a = new InfirmierId(UUID.randomUUID());
        UUID salle = UUID.randomUUID();
        UUID matin = UUID.randomUUID();
        UUID affectation = UUID.randomUUID();
        jdbc().update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) "
                + "VALUES (?, ?, ?, ?, ?, 'MARDI,DIMANCHE,LUNDI')", affectation, CENTRE.valeur(), a.valeur(), salle, matin);
        RoulementsJdbc roulements = new RoulementsJdbc(jdbc());

        List<AffectationLue> lues = roulements.deLaListe(CENTRE, List.of(a, new InfirmierId(UUID.randomUUID()))).get(a);

        assertThat(lues).hasSize(1);
        assertThat(lues.getFirst().id()).isEqualTo(affectation);
        assertThat(lues.getFirst().jours()).containsExactly(JourDeLaSemaine.DIMANCHE, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        assertThat(roulements.deLaListe(AUTRE, List.of(a))).isEmpty();
        assertThat(roulements.deLaListe(CENTRE, List.of())).isEmpty();
    }

    @Test
    void should_lock_the_centre_row_without_changing_it_and_ignore_a_centre_without_row() {
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));
        transaction().executeWithoutResult(statut -> verrou().prendre(new CentreId(UUID.randomUUID())));

        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM centers WHERE id IN (?, ?)", Integer.class, CENTRE.valeur(),
                AUTRE.valeur())).isEqualTo(2);
    }

    /**
     * Ordre global des verrous (ADR 0056) : centre, fiches, puis ligne du compte. Une désactivation de fiche (centre, fiche, compte)
     * et la mise à jour du compte par l'administration (fiches, puis compte) se croisent sans interblocage : l'une attend l'autre,
     * les deux aboutissent.
     */
    @Test
    void should_not_deadlock_when_a_nurse_deactivation_and_an_account_update_cross() throws Exception {
        CompteId compte = unCompte("croise", "Croisé", true, CENTRE);
        Infirmier fiche = fiche(CENTRE, "M-X", "Amrani", "Sara").lierCompte(compte, true);
        infirmiers().enregistrer(fiche);
        CountDownLatch ficheVerrouillee = new CountDownLatch(1);
        CountDownLatch deuxiemeEnAttente = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> desactivation = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                infirmiers().pourEcriture(CENTRE, fiche.id());
                ficheVerrouillee.countDown();
                attendre(deuxiemeEnAttente);
                pause(400); // laisse la mise à jour se bloquer sur la fiche verrouillée
                comptes().definirActif(compte, false);
                return null;
            }));
            assertThat(ficheVerrouillee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> miseAJour = pool.submit(() -> transaction().execute(statut -> {
                deuxiemeEnAttente.countDown();
                // l'administration verrouille les fiches du compte avant sa propre ligne app_user
                infirmiers().fichesDuCompteDuCentre(CENTRE, compte).forEach(f -> infirmiers().enregistrer(f.suivreLeCompte(false)));
                comptes().definirActif(compte, false);
                return null;
            }));
            desactivation.get(30, TimeUnit.SECONDS);
            miseAJour.get(30, TimeUnit.SECONDS);
        } finally {
            deuxiemeEnAttente.countDown();
            pool.shutdownNow();
        }
        assertThat(comptes().parIds(List.of(compte)).getFirst().actif()).isFalse();
        assertThat(infirmiers().pourEcriture(CENTRE, fiche.id()).orElseThrow().actif()).isFalse();
    }

    @Test
    void should_lock_the_identifier_creation_in_a_transaction_without_error() {
        transaction().executeWithoutResult(statut -> comptes().verrouillerLesIdentifiants());
    }

    /** Le verrou d'un centre n'empêche jamais un autre centre d'écrire. */
    @Test
    void should_let_another_centre_write_while_a_centre_lock_is_held() throws Exception {
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            transaction().executeWithoutResult(statut -> verrou().prendre(AUTRE));
            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    private static void pause(long millisecondes) {
        try {
            Thread.sleep(millisecondes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
