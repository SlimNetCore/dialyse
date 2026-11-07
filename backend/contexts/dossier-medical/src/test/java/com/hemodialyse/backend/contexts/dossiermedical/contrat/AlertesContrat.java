package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DetailDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAlerte;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Alertes} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AlertesContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d2001"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract Alertes alertes();

    private AlerteDObservance declenchee(CentreId centre, TypeDeTraitement traitement, TypeDAlerte nature, int minutes, DetailDObservance detail) {
        var a = AlerteDObservance.declencher(UUID.randomUUID(), PATIENT, centre, traitement, nature, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7),
                8000, 4000, "message fictif", detail, T0.plusSeconds(60L * minutes));
        alertes().ajouter(a);
        return a;
    }

    @Test
    void une_alerte_ajoutee_est_relue_avec_son_detail() {
        var detail = new DetailDObservance("UI", 4000, 2, "SEMAINE");
        var a = declenchee(CENTRE_A, TypeDeTraitement.EPO, TypeDAlerte.RETARD_CONSTATE, 0, detail);
        var lue = alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(lue.traitement()).isEqualTo(TypeDeTraitement.EPO);
        assertThat(lue.nature()).isEqualTo(TypeDAlerte.RETARD_CONSTATE);
        assertThat(lue.debut()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(lue.fin()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(lue.attendues()).isEqualTo(8000);
        assertThat(lue.administrees()).isEqualTo(4000);
        assertThat(lue.message()).isEqualTo("message fictif");
        assertThat(lue.detail()).isEqualTo(detail);
        assertThat(lue.creeeLe()).isEqualTo(T0);
        assertThat(lue.resolueLe()).isNull();
        var sansDetail = declenchee(CENTRE_A, TypeDeTraitement.FER_INJECTABLE, TypeDAlerte.RAPPEL_ECHEANCE, 1, DetailDObservance.AUCUN);
        assertThat(alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, sansDetail.id()).orElseThrow().detail()).isEqualTo(DetailDObservance.AUCUN);
    }

    @Test
    void une_alerte_n_est_trouvee_que_par_son_identifiant_son_patient_et_son_centre() {
        var a = declenchee(CENTRE_A, TypeDeTraitement.EPO, TypeDAlerte.RETARD_CONSTATE, 0, null);
        assertThat(alertes().trouverEnVerrouillant(CENTRE_B, PATIENT, a.id())).isEmpty();
        assertThat(alertes().trouverEnVerrouillant(CENTRE_A, new PatientId(UUID.randomUUID()), a.id())).isEmpty();
        assertThat(alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_resolution_est_enregistree_et_se_rejoue_en_reecrivant_la_date() {
        var a = declenchee(CENTRE_A, TypeDeTraitement.EPO, TypeDAlerte.RETARD_CONSTATE, 0, null);
        var chargee = alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        chargee.resoudre(T0.plusSeconds(3600));
        alertes().enregistrerLaResolution(chargee);
        assertThat(alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow().resolueLe()).isEqualTo(T0.plusSeconds(3600));
        chargee.resoudre(T0.plusSeconds(7200));
        alertes().enregistrerLaResolution(chargee);
        assertThat(alertes().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow().resolueLe()).isEqualTo(T0.plusSeconds(7200));
    }

    @Test
    void la_liste_va_de_la_plus_recente_a_la_plus_ancienne_resolues_comprises_et_l_ouverte_ignore_les_resolues() {
        var ancienne = declenchee(CENTRE_A, TypeDeTraitement.EPO, TypeDAlerte.RETARD_CONSTATE, 0, null);
        var recente = declenchee(CENTRE_A, TypeDeTraitement.EPO, TypeDAlerte.RAPPEL_ECHEANCE, 10, null);
        declenchee(CENTRE_B, TypeDeTraitement.EPO, TypeDAlerte.RAPPEL_ECHEANCE, 20, null);
        assertThat(alertes().duPatient(CENTRE_A, PATIENT)).extracting(AlerteDObservance::id).containsExactly(recente.id(), ancienne.id());
        assertThat(alertes().ouverte(CENTRE_A, PATIENT, TypeDeTraitement.EPO, TypeDAlerte.RAPPEL_ECHEANCE)).map(AlerteDObservance::id).contains(recente.id());
        assertThat(alertes().ouverte(CENTRE_A, PATIENT, TypeDeTraitement.FER_INJECTABLE, TypeDAlerte.RAPPEL_ECHEANCE)).isEmpty();
        recente.resoudre(T0.plusSeconds(99));
        alertes().enregistrerLaResolution(recente);
        assertThat(alertes().ouverte(CENTRE_A, PATIENT, TypeDeTraitement.EPO, TypeDAlerte.RAPPEL_ECHEANCE)).isEmpty();
        assertThat(alertes().duPatient(CENTRE_A, PATIENT)).hasSize(2);
    }
}
