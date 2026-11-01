package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAntecedent;
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
 * Tests de caractérisation (pinning) du service d'application {@link AntecedentService}.
 * Les ports sont stubbés : seule l'orchestration des use cases est vérifiée.
 */
class AntecedentServiceTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997400-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-03-10T08:00:00Z");

    private static ConceptCode cim10() {
        return new ConceptCode("CIM10", "N18.5", "IRC stade 5");
    }

    private AntecedentService nouveauService() {
        FakeAntecedentRepository repo = new FakeAntecedentRepository();
        return new AntecedentService(repo, UUID::randomUUID);
    }

    @Test
    void enregistrer_persiste_antecedent_actif_avec_id_genere() {
        AntecedentService service = nouveauService();

        AntecedentId id = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        assertThat(id).isNotNull();
        assertThat(service.consulter(id, CENTRE_A).getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(service.consulter(id, CENTRE_A).getCentreId()).isEqualTo(CENTRE_A);
    }

    @Test
    void enregistrer_rejette_doublon_diagnostic_actif() {
        AntecedentService service = nouveauService();
        service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        assertThatThrownBy(() -> service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2021, 1, 1), null, null, null), MAINTENANT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enregistrer_autorise_meme_diagnostic_apres_resolution() {
        AntecedentService service = nouveauService();
        AntecedentId premier = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);
        service.resoudre(premier, CENTRE_A, LocalDate.of(2023, 5, 1), MAINTENANT);

        AntecedentId second = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2021, 1, 1), null, null, null), MAINTENANT);

        assertThat(service.consulter(second, CENTRE_A).getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
    }

    @Test
    void enregistrer_rejette_commande_et_maintenant_nuls() {
        AntecedentService service = nouveauService();
        assertThatThrownBy(() -> service.enregistrer(null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consulter_inexistant_lance_exception() {
        AntecedentService service = nouveauService();
        assertThatThrownBy(() -> service.consulter(new AntecedentId(UUID.randomUUID()), CENTRE_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consulter_ne_retourne_pas_donnees_dun_autre_centre() {
        AntecedentService service = nouveauService();
        AntecedentId id = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        assertThatThrownBy(() -> service.consulter(id, CENTRE_B))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lister_par_patient_pagine_et_filtre_centre() {
        AntecedentService service = nouveauService();
        service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);
        service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.CHIRURGICAL, null, "Appendicectomie",
                LocalDate.of(2019, 1, 1), null, null, null), MAINTENANT);
        // Données d'un autre centre : ne doivent pas apparaître
        service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_B, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        PagedResult<Antecedent> page = service.listerParPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(10);

        PagedResult<Antecedent> pageB = service.listerParPatient(CENTRE_A, UUID.randomUUID(), 0, 10);
        assertThat(pageB.total()).isEqualTo(0);
    }

    @Test
    void pagination_respecte_page_et_size() {
        AntecedentService service = nouveauService();
        String[] codes = {"N18.5", "N18.4", "E11.9", "I10", "J45.9"};
        for (int i = 0; i < 5; i++) {
            service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                    PATIENT, CENTRE_A, TypeAntecedent.MEDICAL,
                    new ConceptCode("CIM10", codes[i], "libelle " + i), null,
                    LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);
        }
        assertThat(service.listerParPatient(CENTRE_A, PATIENT, 0, 2).items()).hasSize(2);
        assertThat(service.listerParPatient(CENTRE_A, PATIENT, 0, 2).total()).isEqualTo(5);
        assertThat(service.listerParPatient(CENTRE_A, PATIENT, 1, 2).items()).hasSize(2);
        assertThat(service.listerParPatient(CENTRE_A, PATIENT, 2, 2).items()).hasSize(1);
    }

    @Test
    void resoudre_marque_resolu() {
        AntecedentService service = nouveauService();
        AntecedentId id = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, null, "Diabète",
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        Antecedent resolu = service.resoudre(id, CENTRE_A, LocalDate.of(2023, 5, 1), MAINTENANT);

        assertThat(resolu.getStatutClinique()).isEqualTo(StatutClinique.RESOLU);
        assertThat(resolu.getPeriode().fin()).isEqualTo(LocalDate.of(2023, 5, 1));
    }

    @Test
    void supprimer_retire_antecedent() {
        AntecedentService service = nouveauService();
        AntecedentId id = service.enregistrer(new AntecedentUseCases.EnregistrerAntecedentCommande(
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                LocalDate.of(2020, 1, 1), null, null, null), MAINTENANT);

        service.supprimer(id, CENTRE_A);

        assertThatThrownBy(() -> service.consulter(id, CENTRE_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class FakeAntecedentRepository implements com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentRepository {
        private final List<Antecedent> stock = new ArrayList<>();

        @Override
        public void save(Antecedent antecedent) {
            stock.removeIf(a -> a.getId().value().equals(antecedent.getId().value()));
            stock.add(antecedent);
        }

        @Override
        public Optional<Antecedent> trouver(AntecedentId id, CentreId centreId) {
            return stock.stream()
                    .filter(a -> a.getId().value().equals(id.value()) && a.getCentreId().equals(centreId))
                    .findFirst();
        }

        @Override
        public List<Antecedent> findByPatientId(UUID patientId, CentreId centreId) {
            return stock.stream()
                    .filter(a -> a.getPatientId().equals(patientId) && a.getCentreId().equals(centreId))
                    .toList();
        }

        @Override
        public PagedResult<Antecedent> findPagedByPatientId(UUID patientId, CentreId centreId, int page, int size) {
            List<Antecedent> filtres = stock.stream()
                    .filter(a -> a.getPatientId().equals(patientId) && a.getCentreId().equals(centreId))
                    .toList();
            int from = Math.min(page * size, filtres.size());
            int to = Math.min(from + size, filtres.size());
            return PagedResult.of(filtres.subList(from, to), filtres.size(), page, size);
        }

        @Override
        public void deleteById(AntecedentId id, CentreId centreId) {
            stock.removeIf(a -> a.getId().value().equals(id.value()) && a.getCentreId().equals(centreId));
        }
    }
}
