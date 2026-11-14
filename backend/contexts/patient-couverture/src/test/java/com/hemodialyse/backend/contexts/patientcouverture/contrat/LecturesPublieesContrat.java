package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SuiviMedical;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du langage publié {@code patientcouverture.v1} (PC5) : le même jeu de tests passe sur le double en mémoire et sur les
 * tables réelles (H2 puis PostgreSQL 16). Les lectures reprennent À L'IDENTIQUE celles que Absences, Planification, Facturation,
 * Séance et le tableau de bord faisaient en SQL direct.
 */
public abstract class LecturesPublieesContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("c1000000-0000-0000-0000-0000000000a1"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("c1000000-0000-0000-0000-0000000000b2"));
    protected static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    protected static final LocalDate JOUR = LocalDate.of(2026, 11, 2);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    protected abstract PatientRepository patients();

    protected abstract PrisesEnCharge pecs();

    protected abstract Attestations attestations();

    protected abstract UUID forfait(CentreId centre, String libelle, BigDecimal prix);

    protected abstract FichesDesPatients fiches();

    protected abstract CouvertureDesPatients couverture();

    protected abstract CompteursDeCouverture compteurs();

    // ---- fixtures

    protected Patient patient(CentreId centre, EtatDuPatient etat, LocalDate evenement, LocalDate admission) {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()),
                centre, Fiches.avec("LP-" + SEQUENCE.incrementAndGet(), etat, evenement, admission, LocalDate.of(1970, 5, 17)),
                JOUR, T0, UUID::randomUUID);
        patients().enregistrer(p);
        return p;
    }

    protected Patient patient(CentreId centre) {
        return patient(centre, null, null, Fiches.ADMISSION);
    }

    protected PriseEnCharge pec(CentreId centre, Patient patient, StatutPec statut, LocalDate debutDemande, LocalDate finDemande,
                                UUID forfaitDemande, LocalDate debutEffectif, LocalDate finEffectif, UUID forfaitEffectif,
                                Instant creeLe) {
        PriseEnCharge p = PriseEnCharge.reconstituer(UUID.randomUUID(), patient.id(), centre, debutDemande, finDemande, forfaitDemande,
                debutEffectif, finEffectif, forfaitEffectif, statut, creeLe);
        pecs().enregistrer(p);
        return p;
    }

    protected AttestationDroit attestation(CentreId centre, Patient patient, LocalDate debut, LocalDate fin) {
        AttestationDroit a = AttestationDroit.declarer(UUID.randomUUID(), patient.id(), centre, debut, fin, T0);
        attestations().enregistrer(a);
        return a;
    }

    // ---- FichesDesPatients

    @Test
    void should_dire_si_un_patient_existe_pour_le_centre_et_seulement_pour_lui() {
        Patient p = patient(CENTRE);

        assertThat(fiches().existe(CENTRE.valeur(), p.id().valeur())).isTrue();
        assertThat(fiches().existe(AUTRE.valeur(), p.id().valeur())).isFalse();
        assertThat(fiches().existe(CENTRE.valeur(), UUID.randomUUID())).isFalse();
    }

    @Test
    void should_rendre_les_noms_des_patients_du_centre_seulement() {
        Patient p = patient(CENTRE);
        Patient autreCentre = patient(AUTRE);

        var noms = fiches().noms(CENTRE.valeur(), List.of(p.id().valeur(), autreCentre.id().valeur(), UUID.randomUUID(),
                p.id().valeur()));

        assertThat(noms).containsOnlyKeys(p.id().valeur());
        assertThat(noms.get(p.id().valeur()).prenom()).isEqualTo("Karim");
        assertThat(noms.get(p.id().valeur()).nom()).isEqualTo("Benali");
        assertThat(fiches().noms(CENTRE.valeur(), List.of())).isEmpty();
    }

    @Test
    void should_rendre_l_identite_du_patient_du_centre_seulement() {
        Patient p = patient(CENTRE);
        Patient autreCentre = patient(AUTRE);

        var identite = fiches().identite(CENTRE.valeur(), p.id().valeur());

        assertThat(identite).isPresent();
        assertThat(identite.get().prenom()).isEqualTo("Karim");
        assertThat(identite.get().nom()).isEqualTo("Benali");
        assertThat(identite.get().dateNaissance()).isEqualTo(LocalDate.of(1970, 5, 17));
        assertThat(fiches().identite(CENTRE.valeur(), autreCentre.id().valeur())).isEmpty();
        assertThat(fiches().identite(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_rendre_la_carte_d_identite_du_patient_du_centre_seulement() {
        Patient p = patient(CENTRE);
        Patient autreCentre = patient(AUTRE);

        var carte = fiches().carte(CENTRE.valeur(), p.id().valeur());

        assertThat(carte).isPresent();
        assertThat(carte.get().code()).isEqualTo(p.code());
        assertThat(carte.get().prenom()).isEqualTo("Karim");
        assertThat(carte.get().nom()).isEqualTo("Benali");
        assertThat(carte.get().sexe()).isEqualTo("M");
        assertThat(carte.get().dateNaissance()).isEqualTo(LocalDate.of(1970, 5, 17));
        assertThat(carte.get().groupeSanguin()).isEqualTo("O+");
        assertThat(carte.get().numeroAssurance()).isEqualTo(p.fiche().couverture().numeroAssurance().valeur());
        assertThat(carte.get().telMobile()).isNull();
        assertThat(fiches().carte(CENTRE.valeur(), autreCentre.id().valeur())).isEmpty();
        assertThat(fiches().carte(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_rendre_le_medecin_traitant_du_patient_du_centre_seulement_et_rien_sans_medecin() {
        UUID medecin = UUID.randomUUID();
        Fiche nominale = Fiches.nominale();
        Patient avec = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()),
                CENTRE, new Fiche(nominale.identite(), nominale.coordonnees(), nominale.suivi(), nominale.couverture(),
                        new Affectation(medecin, null, null, null, null, null, null, null), nominale.dateAdmission(), null, null),
                JOUR, T0, UUID::randomUUID);
        patients().enregistrer(avec);
        Patient sans = patient(CENTRE);

        assertThat(fiches().medecinTraitant(CENTRE.valeur(), avec.id().valeur())).contains(medecin);
        assertThat(fiches().medecinTraitant(AUTRE.valeur(), avec.id().valeur())).as("patient d'un autre centre").isEmpty();
        assertThat(fiches().medecinTraitant(CENTRE.valeur(), sans.id().valeur())).as("aucun médecin renseigné").isEmpty();
        assertThat(fiches().medecinTraitant(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_situer_le_patient_inconnu_suivi_ou_parti() {
        Patient permanent = patient(CENTRE);
        Patient vacancier = patient(CENTRE, EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1), Fiches.ADMISSION);
        Patient transfere = patient(CENTRE, EtatDuPatient.TRANSFERE, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);
        Patient decede = patient(CENTRE, EtatDuPatient.DECEDE, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);
        Patient greffe = patient(CENTRE, EtatDuPatient.GREFFE, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);
        Patient gueri = patient(CENTRE, EtatDuPatient.GUERRI, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);

        assertThat(fiches().situation(CENTRE.valeur(), UUID.randomUUID())).isEqualTo(FichesDesPatients.Situation.INTROUVABLE);
        assertThat(fiches().situation(AUTRE.valeur(), permanent.id().valeur())).isEqualTo(FichesDesPatients.Situation.INTROUVABLE);
        assertThat(fiches().situation(CENTRE.valeur(), permanent.id().valeur())).isEqualTo(FichesDesPatients.Situation.EN_SUIVI);
        assertThat(fiches().situation(CENTRE.valeur(), vacancier.id().valeur())).isEqualTo(FichesDesPatients.Situation.EN_SUIVI);
        for (Patient parti : List.of(transfere, decede, greffe, gueri)) {
            assertThat(fiches().situation(CENTRE.valeur(), parti.id().valeur())).isEqualTo(FichesDesPatients.Situation.SORTI);
        }
    }

    // ---- FichesDesPatients : recherches et références (Séance, S5a)

    private Patient patientDontLAssureEst(CentreId centre, String numeroAssure) {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()),
                centre, Fiches.tiers("LP-" + SEQUENCE.incrementAndGet(), numeroAssure), JOUR, T0, UUID::randomUUID);
        patients().enregistrer(p);
        return p;
    }

    @Test
    void should_retrouver_un_patient_par_son_code_dans_le_centre_seulement_sans_tolerance_de_casse_ni_d_espaces() {
        Patient p = patient(CENTRE);
        Patient autreCentre = patient(AUTRE);

        assertThat(fiches().parCode(CENTRE.valeur(), p.code())).contains(p.id().valeur());
        assertThat(fiches().parCode(CENTRE.valeur(), p.code().toLowerCase())).as("comparaison exacte, casse comprise").isEmpty();
        assertThat(fiches().parCode(CENTRE.valeur(), " " + p.code())).as("aucune normalisation des espaces").isEmpty();
        assertThat(fiches().parCode(CENTRE.valeur(), autreCentre.code())).as("code d'un autre centre").isEmpty();
        assertThat(fiches().parCode(CENTRE.valeur(), "PAT-INCONNU")).isEmpty();
    }

    @Test
    void should_retrouver_un_patient_par_son_numero_d_assurance_dans_le_centre_seulement_sans_tolerance_de_casse() {
        Patient p = patient(CENTRE);
        Patient autreCentre = patient(AUTRE);
        String numero = p.fiche().couverture().numeroAssurance().valeur();

        assertThat(fiches().parNumeroDAssurance(CENTRE.valeur(), numero)).contains(p.id().valeur());
        assertThat(fiches().parNumeroDAssurance(CENTRE.valeur(), numero.toLowerCase())).as("comparaison exacte, casse comprise")
                .isEmpty();
        assertThat(fiches().parNumeroDAssurance(CENTRE.valeur(), " " + numero)).as("aucune normalisation des espaces").isEmpty();
        assertThat(fiches().parNumeroDAssurance(CENTRE.valeur(), autreCentre.fiche().couverture().numeroAssurance().valeur()))
                .as("numéro d'un autre centre").isEmpty();
        assertThat(fiches().parNumeroDAssurance(CENTRE.valeur(), "INCONNU-0")).isEmpty();
    }

    @Test
    void should_rendre_les_references_de_tous_les_patients_du_centre_par_identifiant_croissant() {
        Patient lui = patient(CENTRE);
        Patient tiers = patientDontLAssureEst(CENTRE, "AS-9");
        Patient autreCentre = patient(AUTRE);

        var references = fiches().references(CENTRE.valeur());

        assertThat(references).extracting(FichesDesPatients.ReferencesDuPatient::patient)
                .containsExactlyElementsOf(java.util.stream.Stream.of(lui, tiers).map(x -> x.id().valeur())
                        .sorted(java.util.Comparator.comparing(UUID::toString)).toList())
                .doesNotContain(autreCentre.id().valeur());
        var deLui = references.stream().filter(r -> r.patient().equals(lui.id().valeur())).findFirst().orElseThrow();
        assertThat(deLui.code()).isEqualTo(lui.code());
        assertThat(deLui.numeroAssurance()).isEqualTo(lui.fiche().couverture().numeroAssurance().valeur());
        assertThat(deLui.numeroAssuranceDeLAssure()).as("l'assuré est le patient : son propre numéro")
                .isEqualTo(deLui.numeroAssurance());
        var duTiers = references.stream().filter(r -> r.patient().equals(tiers.id().valeur())).findFirst().orElseThrow();
        assertThat(duTiers.numeroAssuranceDeLAssure()).isEqualTo("AS-9");
        assertThat(fiches().references(UUID.randomUUID())).isEmpty();
    }

    // ---- ProgrammationDesPatients et LotsDePatients (LEC-PAT i, ADR 0176)

    protected abstract ProgrammationDesPatients programmation();

    protected abstract LotsDePatients lots();

    private static final LocalDate NAISSANCE = LocalDate.of(1970, 5, 17);

    private Patient patientProgramme(CentreId centre, String nom, String prenom, String sexe, boolean sommeil, Affectation.Jours jours,
                                     EtatDuPatient etat, LocalDate evenement) {
        Fiche base = Fiches.avec("LP-" + SEQUENCE.incrementAndGet(), etat, evenement, Fiches.ADMISSION, NAISSANCE);
        Fiche fiche = new Fiche(Identite.saisie("M", nom, prenom, sexe, "O+", 2, NAISSANCE, "Alger", "MARIE"), base.coordonnees(),
                new SuiviMedical(false, false, null, false, null, sommeil, null, null), base.couverture(),
                new Affectation(null, null, null, null, null, null, null, jours), Fiches.ADMISSION, etat, evenement);
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()), centre,
                fiche, JOUR, T0, UUID::randomUUID);
        patients().enregistrer(p);
        poserLesJours(p, jours);
        return p;
    }

    /**
     * Les colonnes de PLACE ({@code jour_*}) sont écrites par la Planification, jamais par le dépôt de la fiche (ADR 0052) : le double
     * en mémoire les porte déjà dans la fiche, les tables réelles les reçoivent ici.
     */
    protected void poserLesJours(Patient patient, Affectation.Jours jours) {
    }

    private Patient patientNomme(CentreId centre, String nom, String prenom) {
        return patientProgramme(centre, nom, prenom, "F", false, Affectation.Jours.aucun(), EtatDuPatient.PERMANENT, null);
    }

    @Test
    void should_rendre_la_programmation_de_tous_les_patients_du_centre_endormis_et_sortis_compris() {
        Patient suivi = patientProgramme(CENTRE, "Benali", "Karim", "M", false,
                new Affectation.Jours(false, true, false, false, true, false, false), EtatDuPatient.PERMANENT, null);
        Patient parti = patientProgramme(CENTRE, "Amrani", "Sara", "F", true,
                new Affectation.Jours(true, false, false, false, false, false, false), EtatDuPatient.TRANSFERE, LocalDate.of(2026, 10, 1));
        Patient autreCentre = patientNomme(AUTRE, "Ailleurs", "Eve");

        var liste = programmation().duCentre(CENTRE.valeur());

        assertThat(liste).extracting(ProgrammationDesPatients.ProgrammationDuPatient::patient)
                .containsExactlyInAnyOrder(suivi.id().valeur(), parti.id().valeur()).doesNotContain(autreCentre.id().valeur());
        var deSuivi = liste.stream().filter(l -> l.patient().equals(suivi.id().valeur())).findFirst().orElseThrow();
        assertThat(deSuivi.sexe()).isEqualTo("M");
        assertThat(deSuivi.dateNaissance()).isEqualTo(NAISSANCE);
        assertThat(deSuivi.dateAdmission()).isEqualTo(Fiches.ADMISSION);
        assertThat(deSuivi.enSommeil()).isFalse();
        assertThat(deSuivi.etat()).isEqualTo("PERMANENT");
        assertThat(deSuivi.dateEvenement()).isNull();
        assertThat(deSuivi.joursDeDialyse()).containsExactlyInAnyOrder(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY);
        var dePartie = liste.stream().filter(l -> l.patient().equals(parti.id().valeur())).findFirst().orElseThrow();
        assertThat(dePartie.enSommeil()).isTrue();
        assertThat(dePartie.etat()).isEqualTo("TRANSFERE");
        assertThat(dePartie.dateEvenement()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(dePartie.joursDeDialyse()).containsExactly(java.time.DayOfWeek.SUNDAY);
        assertThat(programmation().duCentre(UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_rendre_la_programmation_du_patient_du_centre_seulement() {
        Patient p = patientProgramme(CENTRE, "Benali", "Karim", "M", false,
                new Affectation.Jours(false, false, true, false, false, false, true), EtatDuPatient.PERMANENT, null);
        Patient autreCentre = patientNomme(AUTRE, "Ailleurs", "Eve");

        var lue = programmation().duPatient(CENTRE.valeur(), p.id().valeur());

        assertThat(lue).isPresent();
        assertThat(lue.get().joursDeDialyse()).containsExactlyInAnyOrder(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.SATURDAY);
        assertThat(programmation().duPatient(CENTRE.valeur(), autreCentre.id().valeur())).as("patient d'un autre centre").isEmpty();
        assertThat(programmation().duPatient(AUTRE.valeur(), p.id().valeur())).isEmpty();
        assertThat(programmation().duPatient(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void should_rendre_les_identites_de_tous_les_patients_du_centre_seulement() {
        Patient p = patientNomme(CENTRE, "Benali", "Karim");
        Patient autreCentre = patientNomme(AUTRE, "Ailleurs", "Eve");

        var identites = lots().identitesDuCentre(CENTRE.valeur());

        assertThat(identites).containsOnlyKeys(p.id().valeur());
        assertThat(identites.get(p.id().valeur()).nom()).isEqualTo("Benali");
        assertThat(identites.get(p.id().valeur()).prenom()).isEqualTo("Karim");
        assertThat(identites).doesNotContainKey(autreCentre.id().valeur());
    }

    @Test
    void should_rendre_le_sexe_et_la_naissance_du_lot_dans_l_ordre_de_la_demande_sans_borne_de_centre() {
        Patient lui = patientNomme(CENTRE, "Benali", "Karim");
        Patient autreCentre = patientNomme(AUTRE, "Ailleurs", "Eve");
        UUID inconnu = UUID.randomUUID();

        var lot = lots().etatCivil(List.of(autreCentre.id().valeur(), inconnu, lui.id().valeur(), autreCentre.id().valeur()));

        assertThat(lot).extracting(LotsDePatients.EtatCivilDuPatient::patient)
                .as("un patient d'un autre centre est rendu (KB-SEA-64), l'inconnu est absent, chacun une fois")
                .containsExactly(autreCentre.id().valeur(), lui.id().valeur());
        assertThat(lot.get(1).sexe()).isEqualTo("F");
        assertThat(lot.get(1).dateNaissance()).isEqualTo(NAISSANCE);
        assertThat(lots().etatCivil(List.of())).isEmpty();
    }

    @Test
    void should_trier_le_lot_par_nom_puis_prenom_et_ranger_les_homonymes_au_meme_rang() {
        Patient martin1 = patientNomme(CENTRE, "Martin", "Paul");
        Patient dupont = patientNomme(CENTRE, "Dupont", "Anne");
        Patient martin2 = patientNomme(CENTRE, "Martin", "Paul");
        Patient aubert = patientNomme(AUTRE, "Aubert", "Eve");
        Patient dupontMarie = patientNomme(CENTRE, "Dupont", "Marie");

        var lot = lots().parNomPuisPrenom(List.of(martin1.id().valeur(), dupont.id().valeur(), martin2.id().valeur(),
                aubert.id().valeur(), dupontMarie.id().valeur(), UUID.randomUUID(), martin1.id().valeur()));

        assertThat(lot).extracting(LotsDePatients.PatientDuLot::nom).containsExactly("Aubert", "Dupont", "Dupont", "Martin", "Martin");
        assertThat(lot).extracting(LotsDePatients.PatientDuLot::prenom).containsExactly("Eve", "Anne", "Marie", "Paul", "Paul");
        assertThat(lot).extracting(LotsDePatients.PatientDuLot::rang).as("rangs des couples (nom, prénom) distincts")
                .containsExactly(1, 2, 3, 4, 4);
        assertThat(lot.get(0).patient()).as("patient d'un autre centre, rendu (KB-SEA-61)").isEqualTo(aubert.id().valeur());
        assertThat(lot.get(0).code()).isEqualTo(aubert.code());
        assertThat(lot.stream().skip(3).map(LotsDePatients.PatientDuLot::patient).toList())
                .containsExactlyInAnyOrder(martin1.id().valeur(), martin2.id().valeur());
        assertThat(lots().parNomPuisPrenom(List.of())).isEmpty();
    }

    // ---- LotsDePatients : fiche de facturation et centre payeur (LEC-PAT ii, ADR 0177), BORNÉS au centre

    private Patient patientPayeur(CentreId centre, String nom, String prenom, UUID centrePayeur, EtatDuPatient etat) {
        Fiche base = Fiches.avec("LP-" + SEQUENCE.incrementAndGet(), etat, etat == null ? null : LocalDate.of(2026, 10, 1),
                Fiches.ADMISSION, NAISSANCE);
        Fiche fiche = new Fiche(Identite.saisie("M", nom, prenom, "F", "O+", 2, NAISSANCE, "Alger", "MARIE"), base.coordonnees(),
                base.suivi(), new com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture(
                        base.couverture().numeroAssurance(), base.couverture().qualiteAssure(), centrePayeur, null, null, null, null),
                base.affectation(), Fiches.ADMISSION, etat, etat == null ? null : LocalDate.of(2026, 10, 1));
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-" + String.format("%08X", SEQUENCE.incrementAndGet()), centre,
                fiche, JOUR, T0, UUID::randomUUID);
        patients().enregistrer(p);
        return p;
    }

    @Test
    void should_rendre_la_fiche_de_facturation_du_centre_seulement_triee_par_nom_puis_prenom_avec_les_rangs() {
        UUID payeur = UUID.randomUUID();
        Patient martin1 = patientPayeur(CENTRE, "Martin", "Paul", payeur, EtatDuPatient.PERMANENT);
        Patient dupont = patientPayeur(CENTRE, "Dupont", "Anne", null, EtatDuPatient.TRANSFERE);
        Patient martin2 = patientPayeur(CENTRE, "Martin", "Paul", null, null);
        Patient etranger = patientPayeur(AUTRE, "Aubert", "Eve", null, EtatDuPatient.PERMANENT);

        var lot = lots().aFacturerDuCentre(CENTRE.valeur(), List.of(martin1.id().valeur(), dupont.id().valeur(), martin2.id().valeur(),
                etranger.id().valeur(), UUID.randomUUID(), dupont.id().valeur()));

        assertThat(lot).extracting(LotsDePatients.PatientAFacturer::nom).as("le patient d'un autre centre et l'inconnu sont absents")
                .containsExactly("Dupont", "Martin", "Martin");
        assertThat(lot).extracting(LotsDePatients.PatientAFacturer::rang).containsExactly(1, 2, 2);
        var deDupont = lot.get(0);
        assertThat(deDupont.patient()).isEqualTo(dupont.id().valeur());
        assertThat(deDupont.code()).isEqualTo(dupont.code());
        assertThat(deDupont.prenom()).isEqualTo("Anne");
        assertThat(deDupont.etat()).isEqualTo("TRANSFERE");
        assertThat(deDupont.numeroAssurance()).isEqualTo(dupont.fiche().couverture().numeroAssurance().valeur());
        assertThat(deDupont.centrePayeur()).isNull();
        var deMartin = lot.stream().filter(l -> l.patient().equals(martin1.id().valeur())).findFirst().orElseThrow();
        assertThat(deMartin.centrePayeur()).isEqualTo(payeur);
        assertThat(deMartin.etat()).isEqualTo("PERMANENT");
        assertThat(lots().aFacturerDuCentre(AUTRE.valeur(), List.of(martin1.id().valeur()))).as("borne de centre").isEmpty();
        assertThat(lots().aFacturerDuCentre(CENTRE.valeur(), List.of())).isEmpty();
    }

    @Test
    void should_rendre_le_centre_payeur_des_patients_du_centre_qui_en_ont_un() {
        UUID payeur = UUID.randomUUID();
        Patient avec = patientPayeur(CENTRE, "Martin", "Paul", payeur, EtatDuPatient.PERMANENT);
        Patient sans = patientPayeur(CENTRE, "Dupont", "Anne", null, EtatDuPatient.PERMANENT);
        Patient etranger = patientPayeur(AUTRE, "Aubert", "Eve", UUID.randomUUID(), EtatDuPatient.PERMANENT);

        var payeurs = lots().centresPayeursDuCentre(CENTRE.valeur(),
                List.of(avec.id().valeur(), sans.id().valeur(), etranger.id().valeur(), UUID.randomUUID()));

        assertThat(payeurs).containsOnlyKeys(avec.id().valeur()).containsEntry(avec.id().valeur(), payeur);
        assertThat(lots().centresPayeursDuCentre(CENTRE.valeur(), List.of())).isEmpty();
    }

    // ---- CouvertureDesPatients : forfait applicable

    @Test
    void should_rendre_le_forfait_de_la_pec_dont_la_periode_couvre_la_date_sans_regarder_le_statut() {
        Patient p = patient(CENTRE);
        UUID f1 = forfait(CENTRE, "HD3", new BigDecimal("9500.00"));
        pec(CENTRE, p, StatutPec.CLOTUREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), f1, null, null, null, T0);

        var applicable = couverture().forfaitApplicable(CENTRE.valeur(), p.id().valeur(), JOUR).orElseThrow();

        assertThat(applicable.id()).isEqualTo(f1);
        assertThat(applicable.libelle()).isEqualTo("HD3");
        assertThat(applicable.code()).isEqualTo("F-" + f1.toString().substring(0, 8));
        assertThat(applicable.prix()).isEqualByComparingTo("9500.00");
    }

    @Test
    void should_preferer_le_forfait_effectif_puis_la_pec_la_plus_recente_et_se_rabattre_sans_condition_de_date() {
        Patient p = patient(CENTRE);
        UUID demande = forfait(CENTRE, "DEMANDE", new BigDecimal("100.00"));
        UUID accorde = forfait(CENTRE, "ACCORDE", new BigDecimal("200.00"));
        UUID recent = forfait(CENTRE, "RECENT", new BigDecimal("300.00"));
        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), demande, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), demande, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), accorde, T0.minusSeconds(3600));

        assertThat(couverture().forfaitApplicable(CENTRE.valeur(), p.id().valeur(), JOUR).orElseThrow().id())
                .as("le forfait effectif d'abord, même si la PEC est plus ancienne").isEqualTo(accorde);

        Patient seul = patient(CENTRE);
        pec(CENTRE, seul, StatutPec.CLOTUREE, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), demande, null, null, null,
                T0.minusSeconds(7200));
        pec(CENTRE, seul, StatutPec.CLOTUREE, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31), recent, null, null, null, T0);

        assertThat(couverture().forfaitApplicable(CENTRE.valeur(), seul.id().valeur(), JOUR).orElseThrow().id())
                .as("aucune période ne couvre : la plus récente portant un forfait").isEqualTo(recent);
    }

    @Test
    void should_ne_rien_rendre_sans_pec_porteuse_d_un_forfait_du_centre() {
        Patient p = patient(CENTRE);
        UUID dUnAutreCentre = forfait(AUTRE, "AUTRE", new BigDecimal("1.00"));
        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), dUnAutreCentre, null, null, null, T0);

        assertThat(couverture().forfaitApplicable(CENTRE.valeur(), p.id().valeur(), JOUR)).isEmpty();
        assertThat(couverture().forfaitApplicable(AUTRE.valeur(), p.id().valeur(), JOUR)).isEmpty();
    }

    // ---- prises en charge tarifées

    @Test
    void should_lister_les_pec_porteuses_d_un_forfait_avec_leurs_periodes() {
        Patient p = patient(CENTRE);
        UUID demande = forfait(CENTRE, "DEMANDE", new BigDecimal("100.00"));
        UUID accorde = forfait(CENTRE, "ACCORDE", new BigDecimal("200.00"));
        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), demande, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), demande, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 5, 31), accorde, T0);
        pec(CENTRE, p, StatutPec.CREE, null, null, null, null, null, null, T0);
        pec(CENTRE, patient(CENTRE), StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), demande, null, null, null, T0);

        var liste = couverture().prisesEnChargeTarifees(CENTRE.valeur(), p.id().valeur());

        assertThat(liste).hasSize(2);
        var enDemande = liste.stream().filter(l -> !l.forfaitEffectif()).findFirst().orElseThrow();
        assertThat(enDemande.forfaitId()).isEqualTo(demande);
        assertThat(enDemande.libelle()).isEqualTo("DEMANDE");
        assertThat(enDemande.prix()).isEqualByComparingTo("100.00");
        assertThat(enDemande.debutDemande()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(enDemande.finDemande()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(enDemande.debutEffectif()).isNull();
        assertThat(enDemande.creeLe()).isEqualTo(T0);
        var enAccord = liste.stream().filter(l -> l.forfaitEffectif()).findFirst().orElseThrow();
        assertThat(enAccord.forfaitId()).isEqualTo(accorde);
        assertThat(enAccord.debutEffectif()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(enAccord.finEffectif()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(couverture().prisesEnChargeTarifees(AUTRE.valeur(), p.id().valeur())).isEmpty();
    }

    // ---- éligibilité à la facturation

    @Test
    void should_dire_pourquoi_une_seance_n_est_pas_facturable_etage_par_etage_bornes_incluses() {
        Patient p = patient(CENTRE);
        UUID f = forfait(CENTRE, "HD3", new BigDecimal("9500.00"));
        UUID id = p.id().valeur();

        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, JOUR)).contains(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);

        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30), f, null, null, null, T0);
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, JOUR)).as("début inclus")
                .contains(CauseDeNonFacturation.PEC_NON_VALIDEE);
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, LocalDate.of(2026, 11, 30))).as("fin incluse")
                .contains(CauseDeNonFacturation.PEC_NON_VALIDEE);
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, LocalDate.of(2026, 12, 1))).as("au lendemain de la fin")
                .contains(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);

        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), f, null, null, null, T0);
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, JOUR)).contains(CauseDeNonFacturation.ATTESTATION_ABSENTE);

        attestation(CENTRE, p, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30));
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, JOUR)).as("l'attestation ne couvre pas le 2")
                .contains(CauseDeNonFacturation.ATTESTATION_ABSENTE);
        attestation(CENTRE, p, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), id, JOUR)).as("attestation d'un seul jour").isEmpty();
        assertThat(couverture().causeDeNonFacturation(AUTRE.valeur(), id, JOUR)).contains(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);
    }

    @Test
    void should_ignorer_une_pec_sans_forfait_et_l_attestation_d_un_autre_centre_pour_la_facturation() {
        Patient p = patient(CENTRE);
        UUID f = forfait(CENTRE, "HD3", new BigDecimal("9500.00"));
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null, null, null, T0);
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), p.id().valeur(), JOUR))
                .as("une PEC sans forfait ne couvre pas").contains(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);

        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), f, null, null, null, T0);
        attestation(AUTRE, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(couverture().causeDeNonFacturation(CENTRE.valeur(), p.id().valeur(), JOUR))
                .contains(CauseDeNonFacturation.ATTESTATION_ABSENTE);
    }

    @Test
    void should_dire_si_une_pec_est_validee_pour_ce_patient_et_ce_centre() {
        Patient p = patient(CENTRE);
        PriseEnCharge validee = pec(CENTRE, p, StatutPec.VALIDEE, null, null, null, null, null, null, T0);
        PriseEnCharge creee = pec(CENTRE, p, StatutPec.CREE, null, null, null, null, null, null, T0);

        assertThat(couverture().pecValidee(CENTRE.valeur(), p.id().valeur(), validee.id())).isTrue();
        assertThat(couverture().pecValidee(CENTRE.valeur(), p.id().valeur(), creee.id())).isFalse();
        assertThat(couverture().pecValidee(AUTRE.valeur(), p.id().valeur(), validee.id())).isFalse();
        assertThat(couverture().pecValidee(CENTRE.valeur(), UUID.randomUUID(), validee.id())).isFalse();
        assertThat(couverture().pecValidee(CENTRE.valeur(), p.id().valeur(), UUID.randomUUID())).isFalse();
    }

    // ---- compteurs du tableau de bord

    @Test
    void should_compter_les_pec_par_statut_avec_ou_sans_periode_de_creation() {
        Patient p = patient(CENTRE);
        pec(CENTRE, p, StatutPec.CREE, null, null, null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.CREE, null, null, null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, null, null, null, null, null, null, T0);
        pec(AUTRE, patient(AUTRE), StatutPec.CREE, null, null, null, null, null, null, T0);

        assertThat(compteurs().prisesEnCharge(CENTRE.valeur(), CompteursDeCouverture.StatutDePec.CREE, null, null)).isEqualTo(2);
        assertThat(compteurs().prisesEnCharge(CENTRE.valeur(), CompteursDeCouverture.StatutDePec.VALIDEE, null, null)).isEqualTo(1);
        assertThat(compteurs().prisesEnCharge(CENTRE.valeur(), CompteursDeCouverture.StatutDePec.CREE, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)))
                .isEqualTo(2);
        assertThat(compteurs().prisesEnCharge(CENTRE.valeur(), CompteursDeCouverture.StatutDePec.CREE, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31)))
                .isZero();
    }

    @Test
    void should_compter_les_pec_validees_a_echeance_echues_exclues() {
        Patient p = patient(CENTRE);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.VALIDEE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 1), null, null, null, null, T0);
        pec(CENTRE, p, StatutPec.CREE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 15), null, null, null, null, T0);

        // PL3, KB-PL3-3 : une PEC déjà échue (fin avant le jour du centre) n'est plus « à échéance » ; le jour même et le seuil sont inclus
        assertThat(compteurs().prisesEnChargeValideesAEcheance(CENTRE.valeur(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isEqualTo(2);
        assertThat(compteurs().prisesEnChargeValideesAEcheance(CENTRE.valeur(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30)))
                .isEqualTo(1);
        assertThat(compteurs().prisesEnChargeValideesAEcheance(AUTRE.valeur(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isZero();
    }

    @Test
    void should_compter_les_attestations_les_a_echeance_et_les_patients_crees() {
        Patient p = patient(CENTRE);
        patient(AUTRE);
        attestation(CENTRE, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        attestation(CENTRE, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30));
        attestation(CENTRE, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(compteurs().attestations(CENTRE.valeur(), null, null)).isEqualTo(3);
        assertThat(compteurs().attestations(CENTRE.valeur(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isEqualTo(3);
        assertThat(compteurs().attestations(CENTRE.valeur(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).isZero();
        // PL3, KB-PL3-3 étendu : une attestation déjà échue (fin avant le jour du centre) n'est plus « à échéance »
        assertThat(compteurs().attestationsAEcheance(CENTRE.valeur(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isEqualTo(1);
        assertThat(compteurs().attestationsAEcheance(CENTRE.valeur(), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 30))).isEqualTo(1);
        assertThat(compteurs().attestationsAEcheance(CENTRE.valeur(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).isEqualTo(1);
        assertThat(compteurs().attestations(AUTRE.valeur(), null, null)).isZero();
        assertThat(compteurs().patientsCrees(CENTRE.valeur(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isEqualTo(1);
        assertThat(compteurs().patientsCrees(CENTRE.valeur(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void should_compter_l_effectif_d_un_jour_comme_la_regle_unique_de_presence() {
        patient(CENTRE);
        patient(CENTRE, EtatDuPatient.TRANSFERE, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);
        patient(CENTRE, EtatDuPatient.TRANSFERE, LocalDate.of(2026, 11, 2), Fiches.ADMISSION);
        patient(CENTRE, EtatDuPatient.DECEDE, LocalDate.of(2026, 11, 2), Fiches.ADMISSION);
        patient(CENTRE, EtatDuPatient.GREFFE, LocalDate.of(2026, 11, 3), Fiches.ADMISSION);
        patient(CENTRE, EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1), Fiches.ADMISSION);
        patient(CENTRE, EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 10, 1), Fiches.ADMISSION);
        patient(CENTRE, null, null, LocalDate.of(2027, 1, 1));
        patient(AUTRE);

        // présents le 2 novembre : permanent, transféré le 2 (dernière présence incluse), greffé le 3 (place libérée le 3),
        // vacancier jusqu'au 1er décembre. Absents : transféré le 1er octobre, décédé le 2 (place libérée), séjour échu, admission future.
        assertThat(compteurs().patientsPresentsLe(CENTRE.valeur(), JOUR)).isEqualTo(4);
        assertThat(compteurs().patientsPresentsLe(CENTRE.valeur(), LocalDate.of(2026, 11, 1))).isEqualTo(5);
        assertThat(compteurs().patientsPresentsLe(CENTRE.valeur(), LocalDate.of(2027, 2, 1))).as("permanent et admission future")
                .isEqualTo(2);
        assertThat(compteurs().patientsPresentsLe(AUTRE.valeur(), JOUR)).isEqualTo(1);
    }
}
