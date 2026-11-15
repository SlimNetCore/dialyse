package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Dossier;
import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Medicament;
import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient.Traitement;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.ForfaitImpose;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.SeanceDuCahier;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.VoletMedicalBrut;
import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier.VoletParamedicalBrut;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le jeu de données du cahier (DOC5a, ADR 0193) : composition par les ports et mise en forme identique à celle de l'ancienne requête du
 * gabarit (tirets, virgule décimale, libellés). La parité sur base réelle est portée par le filet {@code doc5a/cahier} de la plateforme.
 */
class ComposerLesLignesDuCahierTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final PeriodeDuCahier PERIODE = new PeriodeDuCahier(null, null);
    private static final SeanceId S1 = new SeanceId(UUID.fromString("00000000-0000-0000-0000-000000000051"));
    private static final SeanceId S2 = new SeanceId(UUID.fromString("00000000-0000-0000-0000-000000000052"));
    private static final UUID FORFAIT_IMPOSE = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private final List<SeanceDuCahier> seances = new ArrayList<>();
    private final List<String> lectures = new ArrayList<>();
    private Optional<Dossier> dossier = Optional.empty();
    private Optional<Traitement> traitement = Optional.empty();
    private List<SortiesDeLaSeance.Sortie> sorties = List.of();
    private Optional<ForfaitDuCentre> reference = Optional.empty();

    private ComposerLesLignesDuCahier composeur() {
        return new ComposerLesLignesDuCahier((centre, patient, periode, visible) -> seances,
                (centre, patient, jour) -> Optional.of(new ForfaitsDeSeance.ForfaitCourant(UUID.randomUUID(), "FPEC", "Forfait de la PEC",
                        new BigDecimal("100.00"), 0, null, null, false)),
                (centre, forfait) -> reference, (centre, seance) -> sorties, new DossierEtTraitementDuPatient() {
            @Override
            public Optional<Dossier> dossier(CentreId centre, PatientId patient) {
                lectures.add("dossier");
                return dossier;
            }

            @Override
            public Optional<Traitement> traitementEnVigueur(CentreId centre, PatientId patient, LocalDate jour) {
                lectures.add("traitement " + jour);
                return traitement;
            }
        });
    }

    private static SeanceDuCahier seance(SeanceId id, LocalDate jour, ForfaitImpose impose, VoletParamedicalBrut para, VoletMedicalBrut med) {
        return new SeanceDuCahier(id, jour, "VALIDEE", impose, para, med);
    }

    @Test
    void une_seance_sans_saisie_ni_consommable_donne_une_ligne_de_tirets_avec_le_forfait_de_la_prise_en_charge() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null, null, null));

        List<LigneDuCahier> lignes = composeur().composer(CENTRE, PATIENT, PERIODE, true);

        assertThat(lignes).hasSize(1);
        LigneDuCahier l = lignes.getFirst();
        assertThat(l.seance()).isEqualTo(S1.valeur().toString());
        assertThat(l.statut()).isEqualTo("Validée");
        assertThat(l.forfaitCode()).isEqualTo("FPEC");
        assertThat(l.forfaitPrix()).isEqualByComparingTo("100");
        assertThat(List.of(l.poidsAvant(), l.tensionAvant(), l.duree(), l.dialysat(), l.incidents(), l.nephropathie(), l.hepatiteB(),
                l.poidsSec(), l.epo(), l.fer(), l.prescription(), l.conclusionMedicale())).containsOnly("-");
        assertThat(l.articleCode()).isNull();
        assertThat(l.miseEnDialyse()).isNull();
    }

    @Test
    void une_ligne_par_consommable_dans_l_ordre_du_stock() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null, null, null));
        sorties = List.of(new SortiesDeLaSeance.Sortie("A", "Article A", "boîte", new BigDecimal("2"), new BigDecimal("10.00"), new BigDecimal("20.00")),
                new SortiesDeLaSeance.Sortie(null, null, null, new BigDecimal("1"), null, BigDecimal.ZERO));

        List<LigneDuCahier> lignes = composeur().composer(CENTRE, PATIENT, PERIODE, true);

        assertThat(lignes).extracting(LigneDuCahier::articleCode).containsExactly("A", null);
        assertThat(lignes).extracting(LigneDuCahier::seance).containsOnly(S1.valeur().toString());
        assertThat(lignes.getFirst().totalValorise()).isEqualByComparingTo("20");
    }

    @Test
    void le_forfait_impose_prime_et_complete_son_code_et_son_nom_par_le_referentiel_mais_garde_son_prix() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), new ForfaitImpose(FORFAIT_IMPOSE, null, null, null), null, null));
        seances.add(seance(S2, LocalDate.of(2026, 1, 1), new ForfaitImpose(FORFAIT_IMPOSE, "SUR", "Surcharge", new BigDecimal("150.00")), null, null));
        reference = Optional.of(new ForfaitDuCentre(new ForfaitId(FORFAIT_IMPOSE), "REF", "Référence", new BigDecimal("999.00")));

        List<LigneDuCahier> lignes = composeur().composer(CENTRE, PATIENT, PERIODE, true);

        assertThat(lignes.get(0)).extracting(LigneDuCahier::forfaitCode, LigneDuCahier::forfaitNom, LigneDuCahier::forfaitPrix)
                .containsExactly("REF", "Référence", null);
        assertThat(lignes.get(1)).extracting(LigneDuCahier::forfaitCode, LigneDuCahier::forfaitNom)
                .containsExactly("SUR", "Surcharge");
        assertThat(lignes.get(1).forfaitPrix()).isEqualByComparingTo("150");
    }

    @Test
    void le_volet_medical_le_dossier_et_la_prescription_ne_sont_pas_lus_quand_le_profil_n_y_a_pas_droit() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null, null, null));
        dossier = Optional.of(new Dossier("Néphropathie", null, "PORTEUR", null, null, null));

        LigneDuCahier l = composeur().composer(CENTRE, PATIENT, PERIODE, false).getFirst();

        assertThat(lectures).isEmpty();
        assertThat(l.nephropathie()).isEqualTo("-");
        assertThat(l.hepatiteB()).isEqualTo("-");
    }

    @Test
    void le_dossier_est_lu_une_fois_et_la_prescription_pour_le_jour_de_chaque_seance() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null, null, null));
        seances.add(seance(S2, LocalDate.of(2026, 1, 1), null, null, null));
        dossier = Optional.of(new Dossier("  Diabète  ", LocalDate.of(2025, 6, 15), "VACCINE", "Texte libre", "", "Stable"));

        List<LigneDuCahier> lignes = composeur().composer(CENTRE, PATIENT, PERIODE, true);

        assertThat(lectures).containsExactly("dossier", "traitement 2026-02-01", "traitement 2026-01-01");
        assertThat(lignes.getFirst()).extracting(LigneDuCahier::nephropathie, LigneDuCahier::hepatiteB, LigneDuCahier::hepatiteC,
                LigneDuCahier::observationGlobale, LigneDuCahier::conclusionDuDossier)
                .containsExactly("Diabète", "Vacciné", "Texte libre", "-", "Stable");
        assertThat(lignes.getFirst().miseEnDialyse()).isEqualTo(LocalDate.of(2025, 6, 15));
    }

    @Test
    void les_volets_sont_mis_en_forme_comme_l_ancienne_requete() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null,
                new VoletParamedicalBrut(new BigDecimal("72.50"), new BigDecimal("70.00"), " 13/8 ", "   ", 240, null, new BigDecimal("2500.00"),
                        "Bicarbonate", null, "Crampes"),
                new VoletMedicalBrut("Hémodialyse 4h", null, " ", "Hb 10,5", "Ajuster", "Satisfaisante")));

        LigneDuCahier l = composeur().composer(CENTRE, PATIENT, PERIODE, true).getFirst();

        assertThat(List.of(l.poidsAvant(), l.poidsApres(), l.tensionAvant(), l.tensionApres(), l.duree(), l.debitSang(), l.ultrafiltration(),
                l.dialysat(), l.anticoagulant(), l.incidents())).containsExactly("72,5", "70", "13/8", "-", "240", "-", "2500", "Bicarbonate",
                "-", "Crampes");
        assertThat(List.of(l.prescription(), l.tolerance(), l.examen(), l.resultats(), l.ajustements(), l.conclusionMedicale()))
                .containsExactly("Hémodialyse 4h", "-", "-", "Hb 10,5", "Ajuster", "Satisfaisante");
    }

    @Test
    void la_prescription_en_vigueur_donne_le_poids_sec_et_les_lignes_de_traitement() {
        seances.add(seance(S1, LocalDate.of(2026, 2, 1), null, null, null));
        traitement = Optional.of(new Traitement(new BigDecimal("69.50"), new Medicament("Époétine", 6000, "SC", 1, "SEMAINE"),
                new Medicament(null, null, null, null, null)));

        LigneDuCahier l = composeur().composer(CENTRE, PATIENT, PERIODE, true).getFirst();

        assertThat(l.poidsSec()).isEqualTo("69,5 kg");
        assertThat(l.epo()).isEqualTo("Époétine — 6000 UI SC 1/semaine");
        assertThat(l.fer()).isEqualTo("-");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"72.50|72,5", "70.00|70", "100.00|100", "0.00|0", "2500|2500", "80.13|80,13", "0.05|0,05", "10.10|10,1"})
    void un_decimal_perd_ses_zeros_inutiles_et_prend_la_virgule(String brut, String attendu) {
        assertThat(ComposerLesLignesDuCahier.decimal(new BigDecimal(brut))).isEqualTo(attendu);
    }

    @ParameterizedTest
    @CsvSource({"HEURE, heure", "JOUR, jour", "SEMAINE, semaine", "MOIS, mois", "ANNEE, année", "QUINZAINE, QUINZAINE"})
    void la_periode_de_la_frequence_est_traduite_ou_gardee_telle_quelle(String unite, String attendu) {
        assertThat(ComposerLesLignesDuCahier.traitement(new Medicament("Fer", 100, "IV", 2, unite), "mg")).isEqualTo("Fer — 100 mg IV 2/" + attendu);
    }

    @Test
    void une_dose_nulle_ou_absente_n_imprime_que_l_article() {
        assertThat(ComposerLesLignesDuCahier.traitement(new Medicament("Époétine", 0, "SC", 3, "JOUR"), "UI")).isEqualTo("Époétine");
        assertThat(ComposerLesLignesDuCahier.traitement(new Medicament("Époétine", null, null, null, null), "UI")).isEqualTo("Époétine");
        assertThat(ComposerLesLignesDuCahier.traitement(new Medicament(null, 50, null, null, null), "mg")).isEqualTo("- — 50 mg  /");
    }

    @ParameterizedTest
    @CsvSource({"NEGATIF, Négatif", "PORTEUR, Porteur", "VACCINE, Vacciné", "IMMUNE, Immunisé", "INCONNU, Inconnu", "negatif, negatif"})
    void les_statuts_d_hepatite_connus_sont_traduits_les_autres_gardes(String code, String attendu) {
        assertThat(ComposerLesLignesDuCahier.hepatite(code)).isEqualTo(attendu);
    }
}
