package com.hemodialyse.backend.contexts.planification.infrastructure;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SeriesDuCentre;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.IsolementDesSallesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SallesEtCreneauxDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.CreneauxDuCentrePublies;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.LectureDesRessourcesPubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.SallesDuCentrePubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.SeriesDuCentrePubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.VueDesSallesPubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ParametresDuPlanningJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filet des cas limites de la lecture des salles et des créneaux d'Organisation par la Planification (O4 lot D, ADR 0110), posé VERT sur
 * les lecteurs SQL d'origine avant leur migration : salle fermée, salle inconnue ou d'un autre centre, isolement marqué dans la
 * transaction de l'appelant (annulée ou validée), créneaux triés par code. Les mêmes assertions passent après la migration : seule la
 * construction des lecteurs change.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-lecture-organisation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LecturesDOrganisationDansLaPlanificationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997806-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99997806-0000-0000-0000-0000000000a2"));
    private static final SalleId OUVERTE = new SalleId(UUID.fromString("99997806-0000-0000-0000-0000000000b1"));
    private static final SalleId FERMEE = new SalleId(UUID.fromString("99997806-0000-0000-0000-0000000000b2"));
    private static final SalleId SANS_CAPACITE = new SalleId(UUID.fromString("99997806-0000-0000-0000-0000000000b3"));
    private static final SalleId D_UN_AUTRE_CENTRE = new SalleId(UUID.fromString("99997806-0000-0000-0000-0000000000b4"));
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 27);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final GenerateursDuCentre aucunGenerateur = centre -> List.of();

    @BeforeEach
    void preparer() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, salles_isolement TEXT, patients_par_infirmier INTEGER, "
                + "patients_par_poste_serie INTEGER, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, isolement VARCHAR(3) NOT NULL DEFAULT 'NON', capacite INTEGER, "
                + "ouverte BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_holiday (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE NOT NULL, "
                + "label VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_closure_day (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "day_date DATE NOT NULL, reason VARCHAR(255))");
        for (String table : new String[]{"planning_parametres", "salle", "position_creneau"}) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
        }
        salle(OUVERTE, CENTRE, "S1", "Salle A", "NON", 3, true);
        salle(FERMEE, CENTRE, "S2", "Salle fermée", "OUI", 2, false);
        salle(SANS_CAPACITE, CENTRE, "S3", "Salle sans capacité", "NON", null, true);
        salle(D_UN_AUTRE_CENTRE, AUTRE, "S1", "Salle d'ailleurs", "OUI", 5, true);
    }

    private void salle(SalleId id, CentreId centre, String code, String nom, String isolement, Integer capacite, boolean ouverte) {
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement, capacite, ouverte) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id.valeur(), centre.valeur(), code, nom, isolement, capacite, ouverte);
    }

    private void creneau(UUID id, CentreId centre, String code, String libelle) {
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre.valeur(), code, libelle);
    }

    private String isolementEnBase(SalleId salle) {
        return jdbc.queryForObject("SELECT isolement FROM salle WHERE id = ?", String.class, salle.valeur());
    }

    // ------------------------------------------------------------------ construction des lecteurs (seul point qui change à la migration)

    private SallesEtCreneauxDuCentreJdbc organisation() {
        return new SallesEtCreneauxDuCentreJdbc(jdbc);
    }

    private SallesDuCentre salles() {
        return new SallesDuCentrePubliees(organisation(), new IsolementDesSallesJdbc(jdbc));
    }

    private CreneauxDuCentre creneaux() {
        return new CreneauxDuCentrePublies(organisation());
    }

    private SeriesDuCentre series() {
        return new SeriesDuCentrePubliees(organisation());
    }

    private VueDesSallesPubliees vue() {
        return new VueDesSallesPubliees(organisation(), aucunGenerateur);
    }

    private LectureDesRessourcesPubliees ressources() {
        return new LectureDesRessourcesPubliees(jdbc, organisation(), aucunGenerateur, new ParametresDuPlanningJdbc(jdbc, salles()));
    }

    // ------------------------------------------------------------------ salles

    @Test
    void should_keep_a_closed_room_everywhere_the_planning_reads_rooms() {
        assertThat(salles().toutes(CENTRE)).containsExactlyInAnyOrder(OUVERTE, FERMEE, SANS_CAPACITE);
        assertThat(salles().sallesDIsolement(CENTRE)).containsExactly(FERMEE);
        assertThat(ressources().duCentre(CENTRE, JOUR).salles()).extracting(RessourcesDuCentre.Salle::nom)
                .containsExactly("Salle A", "Salle fermée", "Salle sans capacité");
        VueDesSalles.Page page = vue().lister(CENTRE, new Pagination(0, 20));
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(VueDesSalles.SalleVue::code).containsExactly("S1", "S2", "S3");
    }

    @Test
    void should_read_a_room_only_in_its_own_centre_with_a_missing_capacity_as_null() {
        assertThat(vue().parIdentifiant(CENTRE, FERMEE.valeur())).get().satisfies(s -> {
            assertThat(s.nom()).isEqualTo("Salle fermée");
            assertThat(s.isolement()).isTrue();
            assertThat(s.capacite()).isEqualTo(2);
        });
        assertThat(vue().parIdentifiant(CENTRE, SANS_CAPACITE.valeur())).get().satisfies(s -> assertThat(s.capacite()).isNull());
        assertThat(vue().parIdentifiant(AUTRE, FERMEE.valeur())).isEmpty();
        assertThat(vue().parIdentifiant(CENTRE, D_UN_AUTRE_CENTRE.valeur())).isEmpty();
        assertThat(vue().parIdentifiant(CENTRE, UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_page_the_rooms_by_code_and_answer_nothing_beyond_the_last_page_but_the_total() {
        assertThat(vue().lister(CENTRE, new Pagination(1, 2)).items()).extracting(VueDesSalles.SalleVue::code).containsExactly("S3");
        VueDesSalles.Page apres = vue().lister(CENTRE, new Pagination(5, 2));
        assertThat(apres.items()).isEmpty();
        assertThat(apres.total()).isEqualTo(3);
        assertThat(vue().lister(new CentreId(UUID.randomUUID()), new Pagination(0, 20)).total()).isZero();
    }

    // ------------------------------------------------------------------ isolement : écriture d'Organisation commandée par la Planification

    @Test
    void should_ignore_unknown_rooms_and_rooms_of_another_centre_when_marking_the_isolation_rooms() {
        salles().marquerIsolement(CENTRE, Set.of(OUVERTE, new SalleId(UUID.randomUUID()), D_UN_AUTRE_CENTRE));

        assertThat(salles().sallesDIsolement(CENTRE)).containsExactly(OUVERTE);
        assertThat(isolementEnBase(FERMEE)).isEqualTo("NON");
        assertThat(isolementEnBase(D_UN_AUTRE_CENTRE)).as("la salle de l'autre centre n'est pas touchée").isEqualTo("OUI");
    }

    @Test
    void should_clear_every_isolation_room_of_the_centre_only_when_none_is_given() {
        salles().marquerIsolement(CENTRE, Set.of());

        assertThat(salles().sallesDIsolement(CENTRE)).isEmpty();
        assertThat(salles().sallesDIsolement(AUTRE)).containsExactly(D_UN_AUTRE_CENTRE);
    }

    @Test
    void should_touch_nothing_when_the_centre_is_unknown() {
        CentreId inconnu = new CentreId(UUID.randomUUID());

        salles().marquerIsolement(inconnu, Set.of(OUVERTE, D_UN_AUTRE_CENTRE));

        assertThat(isolementEnBase(OUVERTE)).isEqualTo("NON");
        assertThat(isolementEnBase(FERMEE)).isEqualTo("OUI");
        assertThat(isolementEnBase(D_UN_AUTRE_CENTRE)).isEqualTo("OUI");
    }

    @Test
    void should_undo_the_isolation_marks_with_the_transaction_of_the_caller() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(statut -> {
            salles().marquerIsolement(CENTRE, Set.of(OUVERTE));
            assertThat(salles().sallesDIsolement(CENTRE)).as("visible dans la transaction").containsExactly(OUVERTE);
            statut.setRollbackOnly();
        });

        assertThat(salles().sallesDIsolement(CENTRE)).as("annulé avec la transaction de l'appelant").containsExactly(FERMEE);
    }

    @Test
    void should_keep_the_isolation_marks_when_the_transaction_of_the_caller_commits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(statut -> salles().marquerIsolement(CENTRE, Set.of(OUVERTE)));

        assertThat(salles().sallesDIsolement(CENTRE)).containsExactly(OUVERTE);
    }

    // ------------------------------------------------------------------ créneaux et séries

    @Test
    void should_sort_the_slots_by_code_number_them_from_one_and_count_the_series_of_the_centre_only() {
        UUID soir = UUID.randomUUID();
        UUID matin = UUID.randomUUID();
        UUID ailleurs = UUID.randomUUID();
        creneau(soir, CENTRE, "P2", "Soir");
        creneau(matin, CENTRE, "P1", "Matin");
        creneau(ailleurs, AUTRE, "P1", "Autre centre");

        assertThat(ressources().duCentre(CENTRE, JOUR).creneaux()).extracting(RessourcesDuCentre.Creneau::libelle, RessourcesDuCentre.Creneau::ordre)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Matin", 1), org.assertj.core.groups.Tuple.tuple("Soir", 2));
        assertThat(series().nombre(CENTRE)).isEqualTo(2);
        assertThat(series().nombre(new CentreId(UUID.randomUUID()))).isZero();
        assertThat(creneaux().contient(CENTRE, new CreneauId(matin))).isTrue();
        assertThat(creneaux().contient(CENTRE, new CreneauId(ailleurs))).as("créneau d'un autre centre").isFalse();
        assertThat(creneaux().contient(CENTRE, new CreneauId(UUID.randomUUID()))).as("créneau inconnu").isFalse();
    }
}
