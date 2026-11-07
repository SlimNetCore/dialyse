package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Medicament;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutOrdonnance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Ordonnances} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class OrdonnancesContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d2001"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract Ordonnances ordonnances();

    /** Pose le patient dans le centre (les tables réelles exigent que la fiche existe pour certains adapters). */
    protected void patientDuCentre(CentreId centre, PatientId patient) {
    }

    private static LigneDOrdonnance ligne(String libelle, String posologie, Medicament m) {
        return LigneDOrdonnance.nouvelle(UUID.randomUUID(), m, libelle, posologie, "orale", 30, 2, "a jeun");
    }

    private Ordonnance brouillon(CentreId centre, PatientId patient, LocalDate date, Instant creee, LigneDOrdonnance... lignes) {
        patientDuCentre(centre, patient);
        return Ordonnance.rediger(UUID.randomUUID(), patient, centre, "dr fictif", date, List.of(lignes), creee);
    }

    @Test
    void une_ordonnance_ajoutee_est_relue_avec_ses_lignes_dans_un_ordre_deterministe() {
        var o = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("Zinc", "1", null), ligne("Aspirine", "2", null),
                ligne(null, "3", new Medicament("ATC", "b03xa02", "Darbepoetine")));
        ordonnances().ajouter(o);
        var relue = ordonnances().trouver(CENTRE_A, PATIENT, o.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutOrdonnance.BROUILLON);
        assertThat(relue.numero()).isNull();
        assertThat(relue.medecin()).isEqualTo("dr fictif");
        assertThat(relue.datePrescription()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.lignes()).extracting(LigneDOrdonnance::libelle).containsExactly("Aspirine", "Zinc", null);
        var codee = relue.lignes().get(2);
        assertThat(codee.medicament().code()).isEqualTo("B03XA02");
        assertThat(codee.medicament().nomenclature()).isEqualTo("ATC");
        assertThat(relue.lignes().get(0).dureeJours()).isEqualTo(30);
        assertThat(relue.lignes().get(0).instructions()).isEqualTo("a jeun");
    }

    @Test
    void une_ordonnance_n_est_trouvee_que_par_son_identifiant_son_patient_et_son_centre() {
        var o = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("X", "1", null));
        ordonnances().ajouter(o);
        assertThat(ordonnances().trouver(CENTRE_B, PATIENT, o.id())).isEmpty();
        assertThat(ordonnances().trouver(CENTRE_A, new PatientId(UUID.randomUUID()), o.id())).isEmpty();
        assertThat(ordonnances().trouver(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
        assertThat(ordonnances().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id())).isPresent();
        assertThat(ordonnances().trouverEnVerrouillant(CENTRE_B, PATIENT, o.id())).isEmpty();
    }

    @Test
    void une_transition_est_enregistree_sans_toucher_aux_lignes() {
        var o = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("X", "1", null), ligne("Y", "2", null));
        ordonnances().ajouter(o);
        var chargee = ordonnances().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id()).orElseThrow();
        chargee.signer("ORD-00001", T0.plusSeconds(60));
        ordonnances().enregistrerLaTransition(chargee);
        var relue = ordonnances().trouver(CENTRE_A, PATIENT, o.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutOrdonnance.SIGNEE);
        assertThat(relue.numero()).isEqualTo("ORD-00001");
        assertThat(relue.signeeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.modifieeLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.lignes()).extracting(LigneDOrdonnance::id).containsExactlyInAnyOrderElementsOf(o.lignes().stream().map(LigneDOrdonnance::id).toList());
    }

    @Test
    void la_liste_est_triee_de_la_plus_recente_a_la_plus_ancienne_et_paginee_dans_le_centre_et_pour_le_patient() {
        var a = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 1), T0, ligne("A", "1", null));
        var b = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 4), T0, ligne("B", "1", null));
        var c = brouillon(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 2), T0, ligne("C", "1", null));
        var autreCentre = brouillon(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), T0, ligne("D", "1", null));
        for (var o : List.of(a, b, c, autreCentre)) ordonnances().ajouter(o);
        var page0 = ordonnances().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(page0.total()).isEqualTo(3);
        assertThat(page0.items()).extracting(Ordonnance::id).containsExactly(b.id(), c.id());
        var page1 = ordonnances().duPatient(CENTRE_A, PATIENT, 1, 2);
        assertThat(page1.items()).extracting(Ordonnance::id).containsExactly(a.id());
        assertThat(ordonnances().duPatient(CENTRE_A, PATIENT, 2, 2).items()).isEmpty();
        assertThat(ordonnances().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 5).total()).isZero();
    }
}
