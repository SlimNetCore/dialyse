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
        for (CentreId c : new CentreId[]{CENTRE_A, CENTRE_B}) {
            for (String t : new String[]{"salle", "medecin", "machine", "protocole", "position_creneau"}) {
                jdbc().update("DELETE FROM " + t + " WHERE center_id = ?", c.valeur());
            }
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
    void should_never_delete_a_doctor_of_another_centre() {
        MedecinsJdbc medecins = new MedecinsJdbc(jdbc());
        MedecinId id = new MedecinId(UUID.randomUUID());
        medecins.sauvegarder(new Medecin(id, CENTRE_A, "Benali", "Karim", "Néphrologue"));
        medecins.supprimer(CENTRE_B, id);
        assertThat(medecins.trouver(id)).isPresent();
        medecins.supprimer(CENTRE_A, id);
        assertThat(medecins.trouver(id)).isEmpty();
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
