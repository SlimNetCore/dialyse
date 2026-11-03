package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du dépôt des patients : le même jeu de tests passe sur le double en mémoire et sur la table {@code patients} (H2 puis
 * PostgreSQL 16), ce qui prouve la fidélité du double.
 */
public abstract class PatientsContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("c1000000-0000-0000-0000-0000000000a1"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("c1000000-0000-0000-0000-0000000000b2"));
    protected static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    protected abstract PatientRepository depot();

    protected Patient admis(CentreId centre, Fiche fiche) {
        return Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()), centre,
                fiche, Fiches.AUJOURDHUI, T0, UUID::randomUUID);
    }

    @Test
    void should_relire_la_fiche_enregistree_champ_par_champ() {
        Patient p = admis(CENTRE, Fiches.nominale("CT-1", null, null));
        depot().enregistrer(p);

        Patient relu = depot().trouver(CENTRE, p.id()).orElseThrow();

        assertThat(relu.code()).isEqualTo(p.code());
        assertThat(relu.fiche().identite()).isEqualTo(p.fiche().identite());
        assertThat(relu.fiche().coordonnees()).isEqualTo(p.fiche().coordonnees());
        assertThat(relu.fiche().couverture().numeroAssurance()).isEqualTo(p.fiche().couverture().numeroAssurance());
        assertThat(relu.fiche().dateAdmission()).isEqualTo(Fiches.ADMISSION);
        assertThat(relu.etat()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(relu.vacancier()).isFalse();
    }

    @Test
    void should_ne_pas_trouver_un_patient_d_un_autre_centre() {
        Patient p = admis(CENTRE, Fiches.nominale("CT-2", null, null));
        depot().enregistrer(p);

        assertThat(depot().trouver(AUTRE, p.id())).isEmpty();
    }

    @Test
    void should_mettre_a_jour_la_fiche_et_conserver_l_identite_et_le_code() {
        Patient p = admis(CENTRE, Fiches.nominale("CT-3", null, null));
        depot().enregistrer(p);
        Patient relu = depot().trouver(CENTRE, p.id()).orElseThrow();
        relu.modifier(Fiches.nominale("CT-3", EtatDuPatient.GUERRI, LocalDate.of(2026, 10, 1)), Fiches.AUJOURDHUI, T0, UUID::randomUUID);

        depot().enregistrer(relu);

        Patient apres = depot().trouver(CENTRE, p.id()).orElseThrow();
        assertThat(apres.etat()).isEqualTo(EtatDuPatient.GUERRI);
        assertThat(apres.fiche().dateEvenement()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(apres.code()).isEqualTo(p.code());
    }

    @Test
    void should_rendre_le_detenteur_d_un_numero_sans_tenir_compte_de_la_casse_ni_du_centre() {
        Patient p = admis(CENTRE, Fiches.nominale("ct-4-abc", null, null));
        depot().enregistrer(p);

        assertThat(depot().trouverLeDetenteurDuNumero(CENTRE, new NumeroAssurance(" CT-4-ABC "))).contains(p.id());
        assertThat(depot().trouverLeDetenteurDuNumero(AUTRE, new NumeroAssurance("CT-4-ABC"))).isEmpty();
        assertThat(depot().trouverLeDetenteurDuNumero(CENTRE, new NumeroAssurance("CT-INCONNU"))).isEmpty();
    }

    @Test
    void should_relire_un_vacancier_comme_vacancier() {
        Patient p = admis(CENTRE, Fiches.nominale("CT-5", EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1)));
        depot().enregistrer(p);

        Patient relu = depot().trouver(CENTRE, p.id()).orElseThrow();

        assertThat(relu.vacancier()).isTrue();
        assertThat(relu.etat()).isEqualTo(EtatDuPatient.VACANCIER_LOCAL);
    }
}
