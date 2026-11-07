package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Prescriptions} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class PrescriptionsContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d2001"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract Prescriptions prescriptions();

    private static ContenuDePrescription contenu(LocalDate date, int qb) {
        return new ContenuDePrescription(date, UUID.fromString("00000000-0000-0000-0000-0000000000a9"), qb, 500, 3000, 240,
                new BigDecimal("72.456"), "FX60", "HEPARINE",
                new Traitement(UUID.fromString("00000000-0000-0000-0000-0000000000e1"), 8000, "SC", 1, UniteDeFrequence.SEMAINE),
                new Traitement(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), 100, "IV", 2, UniteDeFrequence.MOIS));
    }

    private PrescriptionMedicale ajoutee(CentreId centre, PatientId patient, LocalDate date, int qb, Instant creee) {
        var p = PrescriptionMedicale.prescrire(UUID.randomUUID(), patient, centre, contenu(date, qb), creee);
        prescriptions().ajouter(p);
        return p;
    }

    @Test
    void une_prescription_ajoutee_est_relue_champ_par_champ() {
        var p = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), 320, T0);
        var relue = prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, p.id()).orElseThrow();
        assertThat(relue.contenu()).isEqualTo(p.contenu());
        assertThat(relue.contenu().poidsSecCibleKg()).isEqualByComparingTo("72.46");
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.modifieeLe()).isEqualTo(T0);
    }

    @Test
    void une_prescription_n_est_trouvee_que_par_son_identifiant_son_patient_et_son_centre() {
        var p = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), 320, T0);
        assertThat(prescriptions().trouverEnVerrouillant(CENTRE_B, PATIENT, p.id())).isEmpty();
        assertThat(prescriptions().trouverEnVerrouillant(CENTRE_A, new PatientId(UUID.randomUUID()), p.id())).isEmpty();
        assertThat(prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_remplacement_ecrase_le_contenu_sans_changer_le_patient_ni_le_centre() {
        var p = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), 320, T0);
        var chargee = prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, p.id()).orElseThrow();
        chargee.remplacerPar(new ContenuDePrescription(LocalDate.of(2026, 1, 9), null, 350, null, null, null, null, null, null, null, null),
                T0.plusSeconds(3600));
        prescriptions().enregistrerLeRemplacement(chargee);
        var relue = prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, p.id()).orElseThrow();
        assertThat(relue.contenu().qbCible()).isEqualTo(350);
        assertThat(relue.contenu().qdCible()).isNull();
        assertThat(relue.contenu().epo()).isEqualTo(Traitement.AUCUN);
        assertThat(relue.contenu().date()).isEqualTo(LocalDate.of(2026, 1, 9));
        assertThat(relue.creeeLe()).isEqualTo(T0.plusSeconds(3600));
        assertThat(relue.patient()).isEqualTo(PATIENT);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
    }

    @Test
    void la_suppression_est_portee_par_identifiant_patient_et_centre_et_sans_effet_sinon() {
        var p = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), 320, T0);
        prescriptions().supprimer(CENTRE_B, PATIENT, p.id());
        prescriptions().supprimer(CENTRE_A, new PatientId(UUID.randomUUID()), p.id());
        prescriptions().supprimer(CENTRE_A, PATIENT, UUID.randomUUID());
        assertThat(prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, p.id())).isPresent();
        prescriptions().supprimer(CENTRE_A, PATIENT, p.id());
        prescriptions().supprimer(CENTRE_A, PATIENT, p.id());
        assertThat(prescriptions().trouverEnVerrouillant(CENTRE_A, PATIENT, p.id())).isEmpty();
    }

    @Test
    void la_liste_est_triee_filtree_par_periode_bornes_incluses_et_paginee() {
        var d2 = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 2), 302, T0);
        var d4 = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 4), 304, T0);
        var d6 = ajoutee(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 6), 306, T0);
        ajoutee(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), 399, T0);
        assertThat(ids(prescriptions().duPatientSurLaPeriode(CENTRE_A, PATIENT, Periode.ILLIMITEE))).containsExactly(d6.id(), d4.id(), d2.id());
        assertThat(ids(prescriptions().duPatientSurLaPeriode(CENTRE_A, PATIENT, new Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 6)))))
                .containsExactly(d6.id(), d4.id());
        assertThat(ids(prescriptions().duPatientSurLaPeriode(CENTRE_A, PATIENT, Periode.jusquau(LocalDate.of(2026, 1, 5))))).containsExactly(d4.id(), d2.id());
        var page1 = prescriptions().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 1, 2);
        assertThat(page1.total()).isEqualTo(3);
        assertThat(ids(page1.items())).containsExactly(d2.id());
        assertThat(prescriptions().duPatient(CENTRE_A, PATIENT, Periode.ILLIMITEE, 9, 2).items()).isEmpty();
    }

    private static List<UUID> ids(List<PrescriptionMedicale> l) {
        return l.stream().map(PrescriptionMedicale::id).toList();
    }
}
