package com.hemodialyse.backend.contexts.planification.infrastructure;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PaginationInvalide;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.LectureDesRessourcesLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.SallesDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.VueDesSallesLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ParametresDuPlanningJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ACL des ressources du placement et de la vue des salles : Organisation (tables {@code salle}, {@code position_creneau}), GMAO (par
 * son langage publié, ici un double), calendrier clinique et paramétrage. Tests de traduction sur H2.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-acl-ressources;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class RessourcesEtSallesLegacyTest {

    private static final UUID CENTRE = UUID.fromString("99997805-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE = UUID.fromString("99997805-0000-0000-0000-0000000000a2");
    private static final UUID SALLE_A = UUID.fromString("99997805-0000-0000-0000-0000000000b1");
    private static final UUID SALLE_B = UUID.fromString("99997805-0000-0000-0000-0000000000b2");
    private static final UUID CRENEAU_MATIN = UUID.fromString("99997805-0000-0000-0000-0000000000c1");
    private static final UUID CRENEAU_SOIR = UUID.fromString("99997805-0000-0000-0000-0000000000c2");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 27);

    @Autowired
    private JdbcTemplate jdbc;
    private final List<Generateur> gmao = new ArrayList<>();
    private final GenerateursDuCentre generateurs = centre -> gmao.stream().filter(g -> g.centreId().equals(centre)).toList();

    @BeforeEach
    void preparer() {
        gmao.clear();
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, salles_isolement TEXT, patients_par_infirmier INTEGER, "
                + "patients_par_poste_serie INTEGER, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, isolement VARCHAR(3) NOT NULL DEFAULT 'NON', capacite INTEGER)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_holiday (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE NOT NULL, "
                + "label VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_closure_day (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "day_date DATE NOT NULL, reason VARCHAR(255))");
        for (String table : new String[]{"planning_parametres", "salle", "position_creneau", "center_holiday", "center_closure_day"}) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE);
        }
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement, capacite) VALUES (?, ?, 'S2', 'Salle B', 'OUI', 1)",
                SALLE_B, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement, capacite) VALUES (?, ?, 'S1', 'Salle A', 'NON', 3)",
                SALLE_A, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement) VALUES (?, ?, 'S1', 'Autre centre', 'NON')",
                UUID.randomUUID(), AUTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'P2', 'Soir')", CRENEAU_SOIR, CENTRE);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'P1', 'Matin')", CRENEAU_MATIN, CENTRE);
    }

    private static Generateur generateur(String code, UUID salle, String statut) {
        return new Generateur(UUID.randomUUID(), code, "Equipement " + code, salle, statut, CENTRE);
    }

    private LectureDesRessourcesLegacy ressources() {
        return new LectureDesRessourcesLegacy(jdbc, generateurs, new ParametresDuPlanningJdbc(jdbc, new SallesDuCentreJdbc(jdbc)));
    }

    @Test
    void should_read_the_rooms_and_slots_of_the_centre_sorted_by_code() {
        RessourcesDuCentre lues = ressources().duCentre(new CentreId(CENTRE), JOUR);

        assertThat(lues.salles()).extracting(RessourcesDuCentre.Salle::nom).containsExactly("Salle A", "Salle B");
        assertThat(lues.salles()).extracting(RessourcesDuCentre.Salle::isolement).containsExactly(false, true);
        assertThat(lues.creneaux()).extracting(RessourcesDuCentre.Creneau::libelle).containsExactly("Matin", "Soir");
        assertThat(lues.creneaux()).extracting(RessourcesDuCentre.Creneau::ordre).containsExactly(1, 2);
    }

    @Test
    void should_keep_only_the_generators_in_service_attached_to_a_room() {
        gmao.add(generateur("G1", SALLE_A, "EN_SERVICE"));
        gmao.add(generateur("G2", SALLE_A, "EN_MAINTENANCE"));
        gmao.add(generateur("G3", null, "EN_SERVICE"));
        gmao.add(generateur("G4", SALLE_B, "HORS_SERVICE"));

        RessourcesDuCentre lues = ressources().duCentre(new CentreId(CENTRE), JOUR);

        assertThat(lues.generateurs()).extracting(RessourcesDuCentre.Generateur::code).containsExactly("G1");
    }

    @Test
    void should_open_every_day_when_the_centre_was_never_configured_and_follow_the_configured_days_otherwise() {
        assertThat(ressources().duCentre(new CentreId(CENTRE), JOUR).joursOuverts()).hasSize(7);

        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, 'LUNDI,MERCREDI', 5, 4, CURRENT_TIMESTAMP)", CENTRE);

        assertThat(ressources().duCentre(new CentreId(CENTRE), JOUR).joursOuverts())
                .containsExactlyInAnyOrder(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI);
    }

    @Test
    void should_list_the_holidays_and_closures_of_the_next_90_days_of_the_centre_only() {
        ferie(CENTRE, JOUR.plusDays(10), "Aid");
        ferie(CENTRE, JOUR.plusDays(90), "Dernier jour");
        ferie(CENTRE, JOUR.plusDays(91), "Trop loin");
        ferie(CENTRE, JOUR.minusDays(1), "Passe");
        ferie(AUTRE, JOUR.plusDays(5), "Autre centre");
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, 'Travaux')", UUID.randomUUID(),
                CENTRE, Date.valueOf(JOUR.plusDays(3)));

        RessourcesDuCentre lues = ressources().duCentre(new CentreId(CENTRE), JOUR);

        assertThat(lues.fermetures()).extracting(RessourcesDuCentre.Fermeture::motif)
                .containsExactlyInAnyOrder("Aid", "Dernier jour", "Travaux");
    }

    private void ferie(UUID centre, LocalDate jour, String libelle) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, ?)", UUID.randomUUID(), centre,
                Date.valueOf(jour), libelle);
    }

    @Test
    void should_compose_the_view_of_the_rooms_with_capacity_and_remaining_places() {
        gmao.add(generateur("G1", SALLE_A, "EN_SERVICE"));
        gmao.add(generateur("G2", SALLE_A, "EN_MAINTENANCE"));
        gmao.add(generateur("G3", SALLE_A, "REFORME"));
        gmao.add(generateur("G4", SALLE_A, "DESACTIF"));
        gmao.add(generateur("G5", SALLE_B, "EN_SERVICE"));
        gmao.add(generateur("G6", SALLE_B, "EN_SERVICE"));

        VueDesSalles.Page page = new VueDesSallesLegacy(jdbc, generateurs).lister(new CentreId(CENTRE), new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(2);
        VueDesSalles.SalleVue a = page.items().get(0);
        assertThat(a.generateurs()).extracting(VueDesSalles.GenerateurVue::code).containsExactly("G1", "G2");
        assertThat(a.placesRestantes()).isEqualTo(1);
        assertThat(a.depassement()).isFalse();
        VueDesSalles.SalleVue b = page.items().get(1);
        assertThat(b.isolement()).isTrue();
        assertThat(b.placesRestantes()).isEqualTo(-1);
        assertThat(b.depassement()).isTrue();
    }

    @Test
    void should_page_the_rooms_and_cap_the_size_at_100() {
        VueDesSallesLegacy vue = new VueDesSallesLegacy(jdbc, generateurs);

        assertThat(vue.lister(new CentreId(CENTRE), new Pagination(1, 1)).items()).extracting(VueDesSalles.SalleVue::nom)
                .containsExactly("Salle B");
        assertThat(vue.lister(new CentreId(CENTRE), new Pagination(0, 0)).items()).isEmpty();
        assertThat(vue.lister(new CentreId(CENTRE), new Pagination(0, 0)).total()).isEqualTo(2);
        assertThat(new Pagination(0, 1000).taille()).isEqualTo(100);
        assertThatThrownBy(() -> new Pagination(-1, 20)).isInstanceOf(PaginationInvalide.class);
        assertThatThrownBy(() -> new Pagination(0, -1)).isInstanceOf(PaginationInvalide.class);
    }
}
