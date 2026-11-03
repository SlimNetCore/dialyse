package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches.ADMISSION;
import static com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches.AUJOURDHUI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");

    private final AtomicInteger sequence = new AtomicInteger();
    private final Supplier<UUID> ids = () -> new UUID(0, sequence.incrementAndGet());

    private Patient admis(Fiche fiche) {
        return Patient.admettre(new PatientId(ids.get()), "PAT-0000000A", CENTRE, fiche, AUJOURDHUI, T0, ids);
    }

    private Patient admisPermanent() {
        Patient p = admis(Fiches.nominale());
        p.retirerLesMouvements();
        return p;
    }

    private void refus(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo(code);
    }

    // ---- admission

    @Test
    void should_admettre_en_etat_permanent_par_defaut_avec_un_mouvement_d_admission() {
        Patient p = admis(Fiches.nominale());

        assertThat(p.etat()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(p.vacancier()).isFalse();
        var mouvements = p.retirerLesMouvements();
        assertThat(mouvements).hasSize(1);
        assertThat(mouvements.get(0).type()).isEqualTo(TypeDeMouvement.ADMISSION);
        assertThat(mouvements.get(0).dateEffet()).isEqualTo(ADMISSION);
        assertThat(mouvements.get(0).etatPrecedent()).isNull();
        assertThat(mouvements.get(0).etatNouveau()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(p.retirerLesMouvements()).isEmpty();
    }

    @Test
    void should_normaliser_telephones_et_courriel_de_la_fiche() {
        Patient p = admis(Fiches.nominale());

        assertThat(p.fiche().coordonnees().telPersonnel()).isEqualTo("0555123456");
        assertThat(p.fiche().coordonnees().email()).isEqualTo("karim@example.org");
        assertThat(p.fiche().coordonnees().telMobile()).isNull();
    }

    @Test
    void should_etre_vacancier_quand_l_etat_d_admission_est_vacancier() {
        Patient p = admis(Fiches.nominale("V-1", EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1)));

        assertThat(p.vacancier()).isTrue();
        p.verifierAttestation(false, false);
    }

    @Test
    void should_recopier_l_etat_civil_du_patient_quand_il_est_son_propre_assure() {
        Patient p = admis(Fiches.nominale());

        var c = p.fiche().couverture();
        assertThat(c.assureNumeroAssurance()).isEqualTo("PC1-001");
        assertThat(c.assure().nom()).isEqualTo("Benali");
        assertThat(c.assure().dateNaissance()).isEqualTo("1970-05-17");
    }

    @Test
    void should_exiger_le_numero_de_l_assure_quand_l_assure_est_un_tiers() {
        assertThatThrownBy(() -> admis(Fiches.tiers("T-1", " "))).isInstanceOf(SaisieInvalide.class);
        assertThat(admis(Fiches.tiers("T-1", "AS-9")).fiche().couverture().assureNumeroAssurance()).isEqualTo("AS-9");
    }

    @Test
    void should_exiger_nom_prenom_sexe_et_dates() {
        assertThatThrownBy(() -> Identite.saisie(null, " ", "K", "M", null, 0, ADMISSION, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Identite.saisie(null, "B", "", "M", null, 0, ADMISSION, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Identite.saisie(null, "B", "K", null, null, 0, ADMISSION, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Identite.saisie(null, "B", "K", "M", null, 0, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Fiches.avec("N", null, null, null, ADMISSION)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuser_une_naissance_future_ou_posterieure_a_l_admission() {
        refus("PATIENT_DATE_NAISSANCE_FUTURE",
                () -> admis(Fiches.avec("N", null, null, AUJOURDHUI.plusDays(40), AUJOURDHUI.plusDays(1))));
        refus("PATIENT_DATE_NAISSANCE_APRES_ADMISSION",
                () -> admis(Fiches.avec("N", null, null, ADMISSION, ADMISSION.plusDays(1))));
    }

    @Test
    void should_accepter_une_admission_future() {
        assertThat(admis(Fiches.avec("N", null, null, AUJOURDHUI.plusDays(30), LocalDate.of(1980, 1, 1))).etat())
                .isEqualTo(EtatDuPatient.PERMANENT);
    }

    // ---- états et dates d'évènement

    @ParameterizedTest
    @EnumSource(value = EtatDuPatient.class, names = {"TRANSFERE", "DECEDE", "GREFFE", "GUERRI", "OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER"})
    void should_exiger_une_date_d_evenement_pour_une_sortie_ou_un_sejour_limite(EtatDuPatient etat) {
        refus("PATIENT_DATE_EVENEMENT_REQUISE", () -> admis(Fiches.nominale("N", etat, null)));
        refus("PATIENT_DATE_EVENEMENT_REQUISE", () -> admisPermanent().modifier(Fiches.nominale("PC1-001", etat, null),
                AUJOURDHUI, T0, ids));
    }

    @Test
    void should_refuser_une_date_d_evenement_avant_l_admission() {
        refus("PATIENT_DATE_EVENEMENT_AVANT_ADMISSION",
                () -> admisPermanent().modifier(Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, ADMISSION.minusDays(1)),
                        AUJOURDHUI, T0, ids));
    }

    @Test
    void should_accepter_une_date_d_evenement_le_jour_de_l_admission() {
        Patient p = admisPermanent();
        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, ADMISSION), AUJOURDHUI, T0, ids);

        assertThat(p.etat()).isEqualTo(EtatDuPatient.GUERRI);
    }

    @Test
    void should_refuser_un_etat_hors_de_la_liste_fermee() {
        refus("PATIENT_ETAT_INCONNU", () -> EtatDuPatient.depuis("BIDON"));
        refus("PATIENT_ETAT_INCONNU", () -> EtatDuPatient.depuis("permanent"));
        refus("PATIENT_ETAT_INCONNU", () -> EtatDuPatient.depuis(null));
        assertThat(EtatDuPatient.depuis("GUERRI")).isEqualTo(EtatDuPatient.GUERRI);
    }

    // ---- mouvements

    @ParameterizedTest
    @EnumSource(value = EtatDuPatient.class, names = {"PERMANENT"}, mode = EnumSource.Mode.EXCLUDE)
    void should_tracer_un_mouvement_du_type_de_l_etat_a_la_date_d_evenement(EtatDuPatient etat) {
        Patient p = admisPermanent();
        LocalDate date = LocalDate.of(2026, 12, 24);

        p.modifier(Fiches.nominale("PC1-001", etat, date), AUJOURDHUI, T0, ids);

        var m = p.retirerLesMouvements();
        assertThat(m).hasSize(1);
        assertThat(m.get(0).type()).isEqualTo(etat.mouvement());
        assertThat(m.get(0).dateEffet()).isEqualTo(date);
        assertThat(m.get(0).etatPrecedent()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(m.get(0).etatNouveau()).isEqualTo(etat);
    }

    @Test
    void should_associer_chaque_etat_au_vocabulaire_de_mouvement_du_legacy() {
        assertThat(EtatDuPatient.TRANSFERE.mouvement()).isEqualTo(TypeDeMouvement.TRANSFERT);
        assertThat(EtatDuPatient.GUERRI.mouvement()).isEqualTo(TypeDeMouvement.GUERISON);
        assertThat(EtatDuPatient.DECEDE.mouvement()).isEqualTo(TypeDeMouvement.DECES);
        assertThat(EtatDuPatient.GREFFE.mouvement()).isEqualTo(TypeDeMouvement.GREFFE);
        assertThat(EtatDuPatient.OCCASIONNEL.mouvement()).isEqualTo(TypeDeMouvement.SEJOUR_TEMPORAIRE);
        assertThat(EtatDuPatient.PERMANENT.mouvement()).isEqualTo(TypeDeMouvement.REPRISE);
    }

    @Test
    void should_dater_la_reprise_sans_date_au_jour_du_centre() {
        Patient p = admisPermanent();
        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, ADMISSION.plusDays(5)), AUJOURDHUI, T0, ids);
        p.retirerLesMouvements();

        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.PERMANENT, null), AUJOURDHUI, T0, ids);

        var m = p.retirerLesMouvements();
        assertThat(m).hasSize(1);
        assertThat(m.get(0).type()).isEqualTo(TypeDeMouvement.REPRISE);
        assertThat(m.get(0).dateEffet()).isEqualTo(AUJOURDHUI);
    }

    @Test
    void should_permettre_toute_transition_y_compris_depuis_le_deces() {
        Patient p = admisPermanent();
        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.DECEDE, ADMISSION.plusDays(9)), AUJOURDHUI, T0, ids);

        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.PERMANENT, null), AUJOURDHUI, T0, ids);

        assertThat(p.etat()).isEqualTo(EtatDuPatient.PERMANENT);
    }

    @Test
    void should_ne_pas_tracer_de_mouvement_quand_rien_ne_change() {
        Patient p = admisPermanent();

        p.modifier(Fiches.nominale(), AUJOURDHUI, T0, ids);

        assertThat(p.retirerLesMouvements()).isEmpty();
    }

    @Test
    void should_ne_pas_tracer_un_second_mouvement_quand_on_corrige_la_date_d_une_sortie() {
        Patient p = admisPermanent();
        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.TRANSFERE, ADMISSION.plusDays(20)), AUJOURDHUI, T0, ids);
        p.retirerLesMouvements();

        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.TRANSFERE, ADMISSION.plusDays(21)), AUJOURDHUI, T0, ids);

        assertThat(p.retirerLesMouvements()).isEmpty();
    }

    @Test
    void should_tracer_un_mouvement_quand_la_fin_d_un_sejour_limite_change() {
        Patient p = admis(Fiches.nominale("V", EtatDuPatient.OCCASIONNEL, ADMISSION.plusDays(10)));
        p.retirerLesMouvements();

        p.modifier(Fiches.nominale("V", EtatDuPatient.OCCASIONNEL, ADMISSION.plusDays(15)), AUJOURDHUI, T0, ids);

        var m = p.retirerLesMouvements();
        assertThat(m).hasSize(1);
        assertThat(m.get(0).type()).isEqualTo(TypeDeMouvement.SEJOUR_TEMPORAIRE);
    }

    @Test
    void should_conserver_l_etat_courant_quand_la_saisie_n_en_porte_pas() {
        Patient p = admisPermanent();
        p.modifier(Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, ADMISSION.plusDays(3)), AUJOURDHUI, T0, ids);
        p.retirerLesMouvements();

        p.modifier(Fiches.nominale("PC1-001", null, ADMISSION.plusDays(3)), AUJOURDHUI, T0, ids);

        assertThat(p.etat()).isEqualTo(EtatDuPatient.GUERRI);
        assertThat(p.retirerLesMouvements()).isEmpty();
    }

    @Test
    void should_figer_la_place_occupee_dans_le_mouvement() {
        UUID salle = new UUID(1, 1);
        UUID creneau = new UUID(1, 2);
        Fiche f = Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, ADMISSION.plusDays(2));
        Fiche avecPlace = new Fiche(f.identite(), f.coordonnees(), f.suivi(), f.couverture(),
                new Affectation(null, salle, creneau, null, null, null, null,
                        new Affectation.Jours(false, true, false, true, false, false, false)),
                f.dateAdmission(), f.etat(), f.dateEvenement());
        Patient p = admisPermanent();

        p.modifier(avecPlace, AUJOURDHUI, T0, ids);

        var m = p.retirerLesMouvements().get(0);
        assertThat(m.salleId()).isEqualTo(salle);
        assertThat(m.creneauId()).isEqualTo(creneau);
        assertThat(m.joursDeDialyse()).isEqualTo("LUNDI,MERCREDI");
    }

    // ---- ligne héritée

    @Test
    void should_lire_une_ligne_heritee_a_l_etat_nul_comme_permanente_et_la_laisser_modifier() {
        Fiche heritee = Fiches.nominale("OLD-1", null, null);
        Patient p = Patient.reconstituer(new PatientId(ids.get()), "PAT-OLD", CENTRE, false, T0, heritee);

        assertThat(p.etat()).isEqualTo(EtatDuPatient.PERMANENT);
        p.modifier(Fiches.nominale("OLD-1", null, null), AUJOURDHUI, T0, ids);
        assertThat(p.retirerLesMouvements()).isEmpty();
    }

    // ---- attestation et unicité

    @Test
    void should_exiger_l_attestation_d_un_non_vacancier_sauf_si_deja_enregistree() {
        Patient p = admisPermanent();

        assertThatThrownBy(() -> p.verifierAttestation(false, false)).isInstanceOf(SaisieInvalide.class);
        p.verifierAttestation(true, false);
        p.verifierAttestation(false, true);
    }

    @Test
    void should_refuser_un_numero_d_assurance_porte_par_un_autre_patient() {
        PatientId a = new PatientId(new UUID(0, 1));
        PatientId b = new PatientId(new UUID(0, 2));

        refus("BUSINESS_RULE_VIOLATION", () -> Patient.verifierNumeroLibre(Optional.of(a), Optional.empty()));
        refus("BUSINESS_RULE_VIOLATION", () -> Patient.verifierNumeroLibre(Optional.of(a), Optional.of(b)));
        Patient.verifierNumeroLibre(Optional.of(a), Optional.of(a));
        Patient.verifierNumeroLibre(Optional.empty(), Optional.empty());
    }

    @Test
    void should_comparer_les_numeros_sans_tenir_compte_de_la_casse_ni_des_espaces_de_bord() {
        assertThat(new NumeroAssurance(" pc1-doublon ")).isEqualTo(new NumeroAssurance("PC1-DOUBLON"));
        assertThatThrownBy(() -> new NumeroAssurance("  ")).isInstanceOf(SaisieInvalide.class);
    }

    // ---- formats

    @Test
    void should_normaliser_et_refuser_les_telephones() {
        assertThat(Telephone.ou("00 213 (555) 12-34-56")).isEqualTo("+213555123456");
        assertThat(Telephone.ou("   ")).isNull();
        assertThatThrownBy(() -> Telephone.ou("12345")).isInstanceOf(SaisieInvalide.class).hasMessage("Téléphone invalide");
    }

    @Test
    void should_normaliser_et_refuser_les_courriels() {
        assertThat(Courriel.ou(" A@B.CO ")).isEqualTo("a@b.co");
        assertThat(Courriel.ou("")).isNull();
        assertThatThrownBy(() -> Courriel.ou("pas-un-courriel")).isInstanceOf(SaisieInvalide.class).hasMessage("Email invalide");
    }

    // ---- état inconnu (donnée ancienne)

    private Patient patientAEtatInconnu(String brut) {
        return Patient.reconstituer(new PatientId(ids.get()), "PAT-OLD", CENTRE, false, T0, Fiches.nominale("OLD-2", null, null), brut);
    }

    @Test
    void should_conserver_la_valeur_brute_d_un_etat_inconnu_sans_la_lire_permanente() {
        Patient p = patientAEtatInconnu("BIDON");

        assertThat(p.etatBrut()).isEqualTo("BIDON");
        p.modifier(Fiches.nominale("OLD-2", null, null), AUJOURDHUI, T0, ids);

        assertThat(p.etatBrut()).as("une saisie sans état laisse l'état enregistré intact").isEqualTo("BIDON");
        assertThat(p.retirerLesMouvements()).as("pas de type de mouvement pour un état inconnu").isEmpty();
    }

    @Test
    void should_refuser_de_placer_un_patient_a_l_etat_inconnu_mais_laisser_sa_place_inchangee() {
        UUID salle = new UUID(2, 1);
        Fiche f = Fiches.nominale("OLD-2", null, null);
        Fiche avecPlace = new Fiche(f.identite(), f.coordonnees(), f.suivi(), f.couverture(),
                new Affectation(null, salle, null, null, null, null, null, null), f.dateAdmission(), null, null);
        Patient p = patientAEtatInconnu("permanent");

        refus("PATIENT_ETAT_INCONNU", () -> p.modifier(avecPlace, AUJOURDHUI, T0, ids));
        assertThat(p.etatBrut()).isEqualTo("permanent");
    }

    @Test
    void should_remplacer_l_etat_inconnu_quand_la_saisie_porte_un_etat() {
        Patient p = patientAEtatInconnu("BIDON");

        p.modifier(Fiches.nominale("OLD-2", EtatDuPatient.GUERRI, ADMISSION.plusDays(3)), AUJOURDHUI, T0, ids);

        assertThat(p.etatBrut()).isEqualTo("GUERRI");
        assertThat(p.retirerLesMouvements()).hasSize(1);
    }

    // ---- occupation de la place

    @Test
    void should_calculer_le_dernier_jour_occupe_selon_l_etat() {
        LocalDate d = LocalDate.of(2026, 6, 10);

        assertThat(EtatDuPatient.PERMANENT.dernierJourOccupe(d)).isEmpty();
        assertThat(EtatDuPatient.TRANSFERE.dernierJourOccupe(d)).contains(d);
        assertThat(EtatDuPatient.GUERRI.dernierJourOccupe(d)).contains(d);
        assertThat(EtatDuPatient.DECEDE.dernierJourOccupe(d)).contains(d.minusDays(1));
        assertThat(EtatDuPatient.GREFFE.dernierJourOccupe(d)).contains(d.minusDays(1));
        assertThat(EtatDuPatient.GREFFE.dernierJourOccupe(null)).contains(LocalDate.MIN);
        assertThat(EtatDuPatient.OCCASIONNEL.dernierJourOccupe(d)).contains(d);
        assertThat(EtatDuPatient.OCCASIONNEL.dernierJourOccupe(null)).isEmpty();
    }
}
