package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.PrescriptionsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAlerte;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Le contrôle de nuit de l'observance, avec les doubles en mémoire des ports (jour fixe : 10/01/2026). */
class ControleDeLObservanceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    private final PrescriptionsEnMemoire prescriptions = new PrescriptionsEnMemoire();
    private final List<AlerteDObservance> alertes = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();
    private Administrations.Administre administre = new Administrations.Administre(0, 0);
    private int sequence;

    private final Administrations administrations = new Administrations() {
        @Override
        public void ajouter(AdministrationDeTraitement administration) {
        }

        @Override
        public PageDAdministrations duPatient(CentreId centre, PatientId patient, int page, int taille) {
            return new PageDAdministrations(List.of(), 0, page, taille);
        }

        @Override
        public List<AdministrationDeTraitement> lesPlusRecentes(CentreId centre, PatientId patient, int nombre) {
            return List.of();
        }

        @Override
        public Administre administreSur(CentreId centre, PatientId patient, TypeDeTraitement traitement, LocalDate debut, LocalDate fin) {
            return administre;
        }

        @Override
        public int effacerCellesDeLaSeance(CentreId centre, java.util.UUID seance) {
            return 0;
        }
    };
    private final Alertes registre = new Alertes() {
        @Override
        public void ajouter(AlerteDObservance alerte) {
            alertes.add(alerte);
        }

        @Override
        public void enregistrerLaResolution(AlerteDObservance alerte) {
        }

        @Override
        public List<AlerteDObservance> duPatient(CentreId centre, PatientId patient) {
            return alertes;
        }

        @Override
        public Optional<AlerteDObservance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
            return alertes.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<AlerteDObservance> ouverte(CentreId centre, PatientId patient, TypeDeTraitement traitement, TypeDAlerte nature) {
            return alertes.stream().filter(a -> a.traitement() == traitement && a.nature() == nature && a.resolueLe() == null).findFirst();
        }
    };
    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };
    private final HorlogeDuCentre horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-10T06:30:00Z"), ZoneOffset.UTC));
    private final ControlerLObservance controler = new ControlerLObservance(prescriptions, administrations, registre, () -> List.of(CENTRE), unite,
            () -> new UUID(0, ++sequence), horloge, new com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie() {
        @Override
        public void administrationSaisie(UUID centre, UUID patient, LocalDate jour) {
        }

        @Override
        public void observanceNonRespectee(UUID centre, UUID patient, String message, java.util.Map<String, String> details) {
            notifications.add(details.get("typeAlerte"));
        }
    });

    private void prescrireEpoParSemaine(LocalDate date) {
        var epo = new Traitement(UUID.randomUUID(), 4000, "SC", 2, UniteDeFrequence.SEMAINE);
        var contenu = new ContenuDePrescription(date, null, null, null, null, null, null, null, null, epo, null);
        prescriptions.ajouter(PrescriptionMedicale.prescrire(UUID.randomUUID(), PATIENT, CENTRE, contenu, Instant.EPOCH));
    }

    @Test
    void un_retard_sur_la_periode_precedente_ouvre_une_alerte_une_seule_fois_mais_notifie_a_chaque_nuit() {
        prescrireEpoParSemaine(LocalDate.of(2026, 1, 1));
        controler.controlerLeCentre(CENTRE);
        controler.controlerLeCentre(CENTRE);
        assertThat(alertes).filteredOn(a -> a.nature() == TypeDAlerte.RETARD_CONSTATE).hasSize(1);
        assertThat(notifications).filteredOn("RETARD_CONSTATE"::equals).hasSize(2);
    }

    @Test
    void le_rappel_de_l_echeance_est_resolu_quand_la_prescription_est_soldee() {
        // Période courante du 04/01 au 10/01 : il reste 1 jour le 10/01, sous le seuil de 2 jours.
        prescrireEpoParSemaine(LocalDate.of(2026, 1, 4));
        controler.controlerLeCentre(CENTRE);
        var rappel = alertes.stream().filter(a -> a.nature() == TypeDAlerte.RAPPEL_ECHEANCE).findFirst().orElseThrow();
        assertThat(rappel.resolueLe()).isNull();
        administre = new Administrations.Administre(2, 8000);
        controler.controlerLeCentre(CENTRE);
        assertThat(rappel.resolueLe()).isNotNull();
    }

    @Test
    void sans_article_ni_frequence_le_traitement_n_est_pas_suivi() {
        var contenu = new ContenuDePrescription(LocalDate.of(2026, 1, 1), null, null, null, null, null, null, null, null,
                new Traitement(null, 4000, "SC", 2, UniteDeFrequence.SEMAINE), null);
        prescriptions.ajouter(PrescriptionMedicale.prescrire(UUID.randomUUID(), PATIENT, CENTRE, contenu, Instant.EPOCH));
        controler.controlerLeCentre(CENTRE);
        assertThat(alertes).isEmpty();
        assertThat(notifications).isEmpty();
    }
}
