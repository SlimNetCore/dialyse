package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.MachineId;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CreneauxJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MachinesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MedecinsJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.ProtocolesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SallesJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des adapters JDBC d'Organisation (salles, créneaux, médecins, machines, protocoles), exécuté sur H2 et sur le schéma
 * Flyway réel de PostgreSQL 16 (variante {@code OrganisationJdbcPostgresContractIT}).
 */
abstract class OrganisationJdbcContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("99998100-0000-0000-0000-00000000000a"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("99998100-0000-0000-0000-00000000000b"));

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        jdbc().update("DELETE FROM centers WHERE code LIKE 'ORGT-%'");
        jdbc().update("DELETE FROM societes WHERE code LIKE 'ORGT-%'");
        for (CentreId c : new CentreId[]{CENTRE_A, CENTRE_B}) {
            for (String t : new String[]{"salle", "medecin", "machine", "protocole", "position_creneau"}) {
                jdbc().update("DELETE FROM " + t + " WHERE center_id = ?", c.valeur());
            }
        }
    }

    @Test
    void should_keep_one_active_centre_when_two_deactivations_run_concurrently() throws Exception {
        UUID societe = UUID.fromString("99998100-0000-0000-0000-0000000000f1");
        UUID c1 = UUID.fromString("99998100-0000-0000-0000-0000000000c1");
        UUID c2 = UUID.fromString("99998100-0000-0000-0000-0000000000c2");
        jdbc().update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ORGT-CC', 'Concurrence', TRUE, CURRENT_TIMESTAMP)", societe);
        jdbc().update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ORGT-CC1', 'C1', ?, TRUE)", c1, societe);
        jdbc().update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ORGT-CC2', 'C2', ?, TRUE)", c2, societe);
        var transactions = new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc().getDataSource());
        var service = new com.hemodialyse.backend.contexts.organisation.application.OrganisationService(
                new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SocietesJdbc(jdbc()),
                new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CentresJdbc(jdbc()),
                new SallesJdbc(jdbc()), new CreneauxJdbc(jdbc()),
                new com.hemodialyse.backend.contexts.organisation.infrastructure.transaction.UniteDeTravailSpring(
                        new org.springframework.transaction.support.TransactionTemplate(transactions), jdbc(), java.time.Duration.ofSeconds(5)),
                new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.VerrouDeLaSocieteJdbc(jdbc()),
                java.time.Clock.systemUTC(), UUID::randomUUID);
        java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var f1 = pool.submit(() -> {
                depart.await();
                service.desactiverCentre(new CentreId(c1));
                return null;
            });
            var f2 = pool.submit(() -> {
                depart.await();
                service.desactiverCentre(new CentreId(c2));
                return null;
            });
            depart.countDown();
            int reussies = 0;
            for (var f : java.util.List.of(f1, f2)) {
                try {
                    f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    reussies++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(IllegalArgumentException.class,
                            com.hemodialyse.backend.contexts.organisation.domain.EcritureConcurrente.class);
                }
            }
            assertThat(reussies).as("une seule désactivation passe").isEqualTo(1);
            assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM centers WHERE societe_id = ? AND actif = TRUE", Integer.class, societe))
                    .as("la société garde un centre actif").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_open_and_close_a_room_of_the_centre_and_list_it() {
        SallesJdbc salles = new SallesJdbc(jdbc());
        SalleId id = new SalleId(UUID.randomUUID());
        salles.sauvegarder(new Salle(id, CENTRE_A, "S1", "Salle 1", true));
        Salle lue = salles.trouver(CENTRE_A, id).orElseThrow();
        lue.fermer();
        salles.sauvegarder(lue);
        assertThat(salles.trouver(CENTRE_A, id).orElseThrow().ouverte()).isFalse();
        assertThat(salles.trouver(CENTRE_B, id)).as("une salle n'est pas visible depuis un autre centre").isEmpty();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM salle WHERE center_id = ? AND ouverte = FALSE", Integer.class,
                CENTRE_A.valeur())).isEqualTo(1);
    }

    @Test
    void should_create_and_modify_a_slot_of_the_centre() {
        CreneauxJdbc creneaux = new CreneauxJdbc(jdbc());
        CreneauId id = new CreneauId(UUID.randomUUID());
        creneaux.sauvegarder(new Creneau(id, CENTRE_A, "M", "Matin"));
        Creneau lu = creneaux.trouver(CENTRE_A, id).orElseThrow();
        lu.modifier("M", "Matinée");
        creneaux.sauvegarder(lu);
        assertThat(creneaux.listerParCentre(CENTRE_A)).extracting(Creneau::libelle).containsExactly("Matinée");
        assertThat(creneaux.listerParCentre(CENTRE_B)).isEmpty();
    }

    @Test
    void should_deactivate_a_doctor_without_deleting_the_row_and_hide_him_from_the_list() {
        MedecinsJdbc medecins = new MedecinsJdbc(jdbc());
        MedecinId id = new MedecinId(UUID.randomUUID());
        medecins.sauvegarder(new Medecin(id, CENTRE_A, "Benali", "Karim", "Néphrologue"));
        assertThat(medecins.listerParCentre(CENTRE_A, 0, 10).items()).hasSize(1);
        Medecin lu = medecins.trouver(id).orElseThrow();
        lu.desactiver();
        medecins.sauvegarder(lu);
        assertThat(medecins.trouver(id)).as("la ligne reste (référencée par les patients)").isPresent();
        assertThat(medecins.trouver(id).orElseThrow().actif()).isFalse();
        assertThat(medecins.listerParCentre(CENTRE_A, 0, 10).items()).isEmpty();
        assertThat(medecins.listerParCentre(CENTRE_A, 0, 10).total()).isZero();
    }

    @Test
    void should_page_the_doctors_of_the_centre_only() {
        MedecinsJdbc medecins = new MedecinsJdbc(jdbc());
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Amrani", "A", "x"));
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Benali", "B", "x"));
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_B, "Autre", "C", "x"));
        var page = jdbc().queryForObject("SELECT COUNT(*) FROM medecin WHERE center_id = ?", Integer.class, CENTRE_A.valeur());
        assertThat(page).isEqualTo(2);
        assertThat(medecins.listerParCentre(CENTRE_A, 0, 1).items()).extracting(Medecin::nom).containsExactly("Amrani");
        assertThat(medecins.listerParCentre(CENTRE_A, 1, 1).items()).extracting(Medecin::nom).containsExactly("Benali");
    }

    @Test
    void should_list_all_doctors_of_the_centre_with_the_deactivated_ones_for_reactivation() {
        MedecinsJdbc medecins = new MedecinsJdbc(jdbc());
        Medecin desactive = new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Benali", "Karim", "x");
        desactive.desactiver();
        medecins.sauvegarder(desactive);
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Amrani", null, null));
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_B, "Autre", "C", "x"));

        assertThat(medecins.listerTousParCentre(CENTRE_A)).extracting(Medecin::nom).containsExactly("Amrani", "Benali");
        assertThat(medecins.listerTousParCentre(CENTRE_A)).extracting(Medecin::actif).containsExactly(true, false);
    }

    @Test
    void should_search_and_page_the_active_doctors_by_name_first_name_or_speciality() {
        MedecinsJdbc medecins = new MedecinsJdbc(jdbc());
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Zeroual", "Amine", "Nephrologue"));
        medecins.sauvegarder(new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Benali", "Karim", "Cardiologue"));
        Medecin inactif = new Medecin(new MedecinId(UUID.randomUUID()), CENTRE_A, "Benali", "Abdel", null);
        inactif.desactiver();
        medecins.sauvegarder(inactif);

        assertThat(medecins.page(CENTRE_A, "BENALI", 0, 10).lignes()).extracting(Medecin::prenom).containsExactly("Karim");
        assertThat(medecins.page(CENTRE_A, "amine", 0, 10).total()).isEqualTo(1);
        assertThat(medecins.page(CENTRE_A, "cardio", 0, 10).lignes()).extracting(Medecin::nom).containsExactly("Benali");
        assertThat(medecins.page(CENTRE_A, null, 1, 1).lignes()).extracting(Medecin::nom).containsExactly("Zeroual");
        assertThat(medecins.page(CENTRE_A, null, 0, 10).total()).isEqualTo(2);
        assertThat(medecins.page(CENTRE_B, null, 0, 10).total()).isZero();
    }

    @Test
    void should_never_delete_a_machine_or_a_protocol_of_another_centre() {
        MachinesJdbc machines = new MachinesJdbc(jdbc());
        ProtocolesJdbc protocoles = new ProtocolesJdbc(jdbc());
        MachineId m = new MachineId(UUID.randomUUID());
        ProtocoleId p = new ProtocoleId(UUID.randomUUID());
        machines.sauvegarder(new Machine(m, CENTRE_A, "M1", "Machine 1", true));
        protocoles.sauvegarder(new Protocole(p, CENTRE_A, "P1", "Protocole 1"));
        machines.supprimer(CENTRE_B, m);
        protocoles.supprimer(CENTRE_B, p);
        assertThat(machines.trouver(m)).isPresent();
        assertThat(protocoles.trouver(p)).isPresent();
        machines.supprimer(CENTRE_A, m);
        protocoles.supprimer(CENTRE_A, p);
        assertThat(machines.trouver(m)).isEmpty();
        assertThat(protocoles.trouver(p)).isEmpty();
    }
}
