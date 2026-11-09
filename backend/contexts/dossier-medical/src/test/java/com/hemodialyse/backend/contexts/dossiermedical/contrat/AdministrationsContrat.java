package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DoseAdministree;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Administrations} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AdministrationsContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d2001"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract Administrations administrations();

    private AdministrationDeTraitement ajoutee(CentreId centre, PatientId patient, TypeDeTraitement type, LocalDate jour, boolean administree,
                                               String dose, String unite) {
        var a = AdministrationDeTraitement.enregistrer(UUID.randomUUID(), patient, centre, null, type, "Molecule fictive",
                dose == null ? null : new DoseAdministree(new BigDecimal(dose), unite), "SC", jour, null, "inf", administree,
                administree ? null : "Patient absent", null, null, T0);
        administrations().ajouter(a);
        return a;
    }

    @Test
    void une_administration_ajoutee_est_relue_champ_par_champ() {
        var a = ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 5), true, "4000", "UI");
        var relue = administrations().lesPlusRecentes(CENTRE_A, PATIENT, 1).get(0);
        assertThat(relue.id()).isEqualTo(a.id());
        assertThat(relue.traitement()).isEqualTo(TypeDeTraitement.EPO);
        assertThat(relue.dose().valeur()).isEqualByComparingTo("4000");
        assertThat(relue.dose().unite()).isEqualTo("UI");
        assertThat(relue.voie()).isEqualTo("SC");
        assertThat(relue.administree()).isTrue();
        assertThat(relue.creeeLe()).isEqualTo(T0);
        var nonFaite = ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.FER_INJECTABLE, LocalDate.of(2026, 1, 6), false, null, null);
        var lue = administrations().lesPlusRecentes(CENTRE_A, PATIENT, 1).get(0);
        assertThat(lue.id()).isEqualTo(nonFaite.id());
        assertThat(lue.dose()).isNull();
        assertThat(lue.motif()).isEqualTo("Patient absent");
    }

    @Test
    void les_plus_recentes_et_la_page_sont_triees_de_la_plus_recente_a_la_plus_ancienne_dans_le_centre_du_patient() {
        var d2 = ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 2), true, "1", "UI");
        var d9 = ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 9), true, "1", "UI");
        var d5 = ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 5), true, "1", "UI");
        ajoutee(CENTRE_B, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 20), true, "1", "UI");
        assertThat(ids(administrations().lesPlusRecentes(CENTRE_A, PATIENT, 2))).containsExactly(d9.id(), d5.id());
        var page1 = administrations().duPatient(CENTRE_A, PATIENT, 1, 2);
        assertThat(page1.total()).isEqualTo(3);
        assertThat(ids(page1.items())).containsExactly(d2.id());
        assertThat(administrations().duPatient(CENTRE_A, PATIENT, 9, 2).items()).isEmpty();
        assertThat(administrations().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 5).total()).isZero();
    }

    @Test
    void l_administre_de_la_periode_compte_les_effectives_et_la_dose_dans_l_unite_du_traitement_sans_casse() {
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 1), true, "4000", "UI");
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 7), true, "2000", "ui");
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 3), true, "100", "mg");
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 4), false, null, null);
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 8), true, "9000", "UI");
        ajoutee(CENTRE_A, PATIENT, TypeDeTraitement.FER_INJECTABLE, LocalDate.of(2026, 1, 2), true, "100", "mg");
        ajoutee(CENTRE_B, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 2), true, "7000", "UI");
        var epo = administrations().administreSur(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7));
        assertThat(epo.nombre()).isEqualTo(3);
        assertThat(epo.dose()).isEqualTo(6000);
        var fer = administrations().administreSur(CENTRE_A, PATIENT, TypeDeTraitement.FER_INJECTABLE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7));
        assertThat(fer.nombre()).isEqualTo(1);
        assertThat(fer.dose()).isEqualTo(100);
        assertThat(administrations().administreSur(CENTRE_A, PATIENT, TypeDeTraitement.EPO, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 7)).nombre()).isZero();
    }

    /** DM9 (ADR 0101) : l'effacement des administrations d'une séance supprimée (SE6) ne touche que la séance AU centre. */
    @Test
    void l_effacement_des_administrations_d_une_seance_ne_touche_que_cette_seance_dans_ce_centre() {
        UUID seance = UUID.randomUUID();
        UUID autreSeance = UUID.randomUUID();
        DoseAdministree dose = new DoseAdministree(new BigDecimal("4000"), "UI");
        for (int i = 0; i < 2; i++) {
            administrations().ajouter(AdministrationDeTraitement.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, null, TypeDeTraitement.EPO,
                    "Molecule fictive", dose, "SC", LocalDate.of(2026, 1, 5), seance, "inf", true, null, null, null, T0));
        }
        administrations().ajouter(AdministrationDeTraitement.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, null, TypeDeTraitement.EPO,
                "Molecule fictive", dose, "SC", LocalDate.of(2026, 1, 6), autreSeance, "inf", true, null, null, null, T0));
        administrations().ajouter(AdministrationDeTraitement.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_B, null, TypeDeTraitement.EPO,
                "Molecule fictive", dose, "SC", LocalDate.of(2026, 1, 7), seance, "inf", true, null, null, null, T0));
        assertThat(administrations().effacerCellesDeLaSeance(CENTRE_A, seance)).isEqualTo(2);
        assertThat(administrations().lesPlusRecentes(CENTRE_A, PATIENT, 10)).hasSize(1);
        assertThat(administrations().lesPlusRecentes(CENTRE_B, PATIENT, 10)).hasSize(1);
        assertThat(administrations().effacerCellesDeLaSeance(CENTRE_A, seance)).isZero();
    }

    private static List<UUID> ids(List<AdministrationDeTraitement> l) {
        return l.stream().map(AdministrationDeTraitement::id).toList();
    }
}
