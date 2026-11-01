package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de caractérisation (pinning) du service d'application {@link AllergieService}.
 * Les ports sont stubbés : seule l'orchestration des use cases est vérifiée.
 */
class AllergieServiceTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997500-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-03-10T08:00:00Z");

    private AllergieService nouveauService() {
        FakeAllergieRepository repo = new FakeAllergieRepository();
        return new AllergieService(repo, UUID::randomUUID);
    }

    @Test
    void enregistrer_persiste_allergie_active_avec_id_genere() {
        AllergieService service = nouveauService();

        AllergieId id = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption cutanée", LocalDate.of(2020, 1, 1), null, "sévère", null), MAINTENANT);

        assertThat(id).isNotNull();
        assertThat(service.consulter(id, CENTRE_A).getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(service.consulter(id, CENTRE_A).getCentreId()).isEqualTo(CENTRE_A);
    }

    @Test
    void enregistrer_rejette_doublon_allergene_actif() {
        AllergieService service = nouveauService();
        service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption cutanée", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        assertThatThrownBy(() -> service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Autre réaction", LocalDate.of(2021, 1, 1), null, null, null), MAINTENANT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enregistrer_autorise_meme_allergene_apres_resolution() {
        AllergieService service = nouveauService();
        AllergieId premier = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);
        service.resoudre(premier, CENTRE_A, LocalDate.of(2023, 5, 1), MAINTENANT);

        AllergieId second = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2021, 1, 1), null, null, null), MAINTENANT);

        assertThat(service.consulter(second, CENTRE_A).getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
    }

    @Test
    void consulter_inexistant_lance_exception() {
        AllergieService service = nouveauService();
        assertThatThrownBy(() -> service.consulter(new AllergieId(UUID.randomUUID()), CENTRE_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consulter_ne_retourne_pas_donnees_dun_autre_centre() {
        AllergieService service = nouveauService();
        AllergieId id = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        assertThatThrownBy(() -> service.consulter(id, CENTRE_B))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lister_par_patient_pagine_et_filtre_centre() {
        AllergieService service = nouveauService();
        service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);
        service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Arachide", TypeAllergie.ALIMENTAIRE,
                "Œdème", LocalDate.of(2019, 1, 1), null, null, null), MAINTENANT);
        // Données d'un autre centre : ne doivent pas apparaître
        service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_B, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        PagedResult<Allergie> page = service.listerParPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void resoudre_marque_resolue() {
        AllergieService service = nouveauService();
        AllergieId id = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        Allergie resolue = service.resoudre(id, CENTRE_A, LocalDate.of(2023, 5, 1), MAINTENANT);

        assertThat(resolue.getStatutClinique()).isEqualTo(StatutClinique.RESOLU);
        assertThat(resolue.getPeriode().fin()).isEqualTo(LocalDate.of(2023, 5, 1));
    }

    @Test
    void supprimer_retire_allergie() {
        AllergieService service = nouveauService();
        AllergieId id = service.enregistrer(new AllergieUseCases.EnregistrerAllergieCommande(
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption", LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        service.supprimer(id, CENTRE_A);

        assertThatThrownBy(() -> service.consulter(id, CENTRE_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class FakeAllergieRepository implements com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository {
        private final List<Allergie> stock = new ArrayList<>();

        @Override
        public void save(Allergie allergie) {
            stock.removeIf(a -> a.getId().value().equals(allergie.getId().value()));
            stock.add(allergie);
        }

        @Override
        public Optional<Allergie> trouver(AllergieId id, CentreId centreId) {
            return stock.stream()
                    .filter(a -> a.getId().value().equals(id.value()) && a.getCentreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public List<Allergie> findByPatientId(UUID patientId, CentreId centreId) {
            return stock.stream()
                    .filter(a -> a.getPatientId().equals(patientId) && a.getCentreId().equals(centreId))
                    .toList();
        }

        @Override
        public PagedResult<Allergie> findPagedByPatientId(UUID patientId, CentreId centreId, int page, int size) {
            List<Allergie> filtres = stock.stream()
                    .filter(a -> a.getPatientId().equals(patientId) && a.getCentreId().equals(centreId))
                    .toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }

        @Override
        public void deleteById(AllergieId id, CentreId centreId) {
            stock.removeIf(a -> a.getId().value().equals(id.value()) && a.getCentreId().equals(centreId));
        }
    }
}
