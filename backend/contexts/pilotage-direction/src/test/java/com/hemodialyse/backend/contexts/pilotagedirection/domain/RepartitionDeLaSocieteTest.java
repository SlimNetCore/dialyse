package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.AdministrationsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ChiffreDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.SeancesDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.TraitementDeLAnemieDuCentre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Les règles de la répartition de la société (RG-DIR, KB-DIR-01) figées par les goldens PD1 `breakdown` et PD4. */
class RepartitionDeLaSocieteTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 9);
    private static final Periode PERIODE = new Periode(LocalDate.of(2026, 1, 1), AUJOURDHUI);
    private static final OffsetDateTime GENERE_LE = OffsetDateTime.of(2026, 11, 9, 10, 0, 0, 0, ZoneOffset.UTC);

    private static SocieteSuivie societe(CentreDeLaSociete... centres) {
        return new SocieteSuivie(UUID.randomUUID(), "Société", List.of(centres));
    }

    private static SocieteSuivie annabaEtBlida() {
        return societe(new CentreDeLaSociete(A, "Annaba", true), new CentreDeLaSociete(B, "blida", true));
    }

    private static RepartitionDeLaSociete repartir(SocieteSuivie societe, List<ProfilDUnPatient> profils,
                                                   List<PatientsDeLaCaisse> patients, List<SeancesDeLaCaisse> seances,
                                                   List<ChiffreDeLaCaisse> chiffre) {
        return RepartitionDeLaSociete.constituer(societe, PERIODE, AUJOURDHUI, GENERE_LE, profils, patients, seances, chiffre, Map.of(),
                Map.of());
    }

    private static List<ProfilDUnPatient> patients(UUID centre, String sexe, int nombre) {
        List<ProfilDUnPatient> profils = new ArrayList<>();
        for (int i = 0; i < nombre; i++) profils.add(new ProfilDUnPatient(centre, sexe, LocalDate.of(1980, 1, 1)));
        return profils;
    }

    @Test
    void une_societe_sans_centre_na_aucune_repartition() {
        var repartition = RepartitionDeLaSociete.sansCentre(societe(), PERIODE, GENERE_LE);

        assertThat(repartition.profils()).isEmpty();
        assertThat(repartition.caisses()).isEmpty();
        assertThat(repartition.caissesDeLaSociete()).isEmpty();
        assertThat(repartition.anemie()).isEmpty();
    }

    @Test
    void le_sexe_est_lu_a_linitiale_sans_tenir_compte_de_la_casse_ni_des_blancs() {
        var profils = new ArrayList<ProfilDUnPatient>();
        profils.addAll(patients(A, " masculin", 2));
        profils.addAll(patients(A, "F", 3));
        profils.addAll(patients(A, "x", 1));
        profils.addAll(patients(A, null, 1));
        profils.addAll(patients(A, "  ", 1));

        var centre = repartir(annabaEtBlida(), profils, List.of(), List.of(), List.of()).profils().getFirst();

        assertThat(centre.masculin()).isEqualTo(2);
        assertThat(centre.feminin()).isEqualTo(3);
        assertThat(centre.autre()).isEqualTo(3);
    }

    @Test
    void un_centre_sans_patient_apparait_avec_des_effectifs_nuls_publies_a_zero() {
        var repartition = repartir(annabaEtBlida(), patients(A, "M", 6), List.of(), List.of(), List.of());

        var blida = repartition.profils().get(1);
        assertThat(blida.nom()).isEqualTo("blida");
        assertThat(blida.masculinPublie()).isEqualTo(0L);
        assertThat(blida.tranchesPubliees()).hasSize(TrancheDAge.values().length).containsValue(0L);
    }

    @Test
    void les_effectifs_sous_le_seuil_sont_masques_et_les_effectifs_au_seuil_sont_publies() {
        var profils = new ArrayList<ProfilDUnPatient>();
        profils.addAll(patients(A, "M", 4));
        profils.addAll(patients(A, "F", 5));

        var centre = repartir(annabaEtBlida(), profils, List.of(), List.of(), List.of()).profils().getFirst();

        assertThat(centre.masculinPublie()).isNull();
        assertThat(centre.femininPublie()).isEqualTo(5L);
        assertThat(centre.autrePublie()).isEqualTo(0L);
    }

    @Test
    void les_tranches_dage_sont_servies_dans_lordre_daffichage_avec_une_tranche_inconnue_pour_une_naissance_absente() {
        var profils = List.of(new ProfilDUnPatient(A, "M", null), new ProfilDUnPatient(A, "M", LocalDate.of(2000, 1, 1)));

        var tranches = repartir(annabaEtBlida(), profils, List.of(), List.of(), List.of()).profils().getFirst().ages();

        assertThat(tranches).containsEntry(TrancheDAge.INCONNUE, 1L).containsEntry(TrancheDAge.DE_18_A_29, 1L);
        assertThat(repartir(annabaEtBlida(), profils, List.of(), List.of(), List.of()).profils().getFirst().tranchesPubliees().keySet())
                .containsExactly(TrancheDAge.values());
    }

    @Test
    void les_lignes_de_caisse_sont_triees_par_nom_de_centre_sans_casse_puis_par_chiffre_daffaires_decroissant() {
        var chiffre = List.of(
                new ChiffreDeLaCaisse(B, "CNAS", "CNAS", new BigDecimal("50.00")),
                new ChiffreDeLaCaisse(A, "CASNOS", "CASNOS", new BigDecimal("10.00")),
                new ChiffreDeLaCaisse(A, "CNAS", "CNAS", new BigDecimal("90.00")));

        var lignes = repartir(annabaEtBlida(), List.of(), List.of(), List.of(), chiffre).caisses();

        assertThat(lignes).extracting(l -> l.centre() + "/" + l.code())
                .containsExactly("Annaba/CNAS", "Annaba/CASNOS", "blida/CNAS");
    }

    @Test
    void les_totaux_par_caisse_sont_tries_par_chiffre_daffaires_decroissant_et_additionnent_les_bruts_sans_les_publier_masques_kb_dir_01_corrige() {
        var patients = List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 3), new PatientsDeLaCaisse(B, "CNAS", "CNAS", 4),
                new PatientsDeLaCaisse(A, "CASNOS", "CASNOS", 2));
        var seances = List.of(new SeancesDeLaCaisse(A, "CNAS", "CNAS", 10), new SeancesDeLaCaisse(B, "CNAS", "CNAS", 5));
        var chiffre = List.of(new ChiffreDeLaCaisse(A, "CASNOS", "CASNOS", new BigDecimal("500.00")),
                new ChiffreDeLaCaisse(A, "CNAS", "CNAS", new BigDecimal("100.00")),
                new ChiffreDeLaCaisse(B, "CNAS", "CNAS", new BigDecimal("50.50")));

        var repartition = repartir(annabaEtBlida(), List.of(), patients, seances, chiffre);

        var cnas = repartition.caissesDeLaSociete().stream().filter(c -> c.code().equals("CNAS")).findFirst().orElseThrow();
        assertThat(cnas.patients()).isEqualTo(7);
        assertThat(cnas.patientsPublies()).isNull();
        assertThat(cnas.seances()).isEqualTo(15);
        assertThat(cnas.caHt()).isEqualByComparingTo("150.50");
        assertThat(repartition.caisses().stream().filter(c -> c.centreId().equals(A) && c.code().equals("CNAS")).findFirst().orElseThrow()
                .patientsPublies()).isNull();
        assertThat(repartition.caissesDeLaSociete()).extracting(c -> c.code()).containsExactly("CASNOS", "CNAS");
    }

    private static RepartitionDeLaSociete.CaisseDeLaSociete total(RepartitionDeLaSociete r, String code) {
        return r.caissesDeLaSociete().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void le_total_d_une_caisse_est_supprime_des_qu_un_seul_centre_est_masque_pour_cette_caisse() {
        var patients = List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 40), new PatientsDeLaCaisse(B, "CNAS", "CNAS", 2),
                new PatientsDeLaCaisse(A, "CASNOS", "CASNOS", 10), new PatientsDeLaCaisse(B, "CASNOS", "CASNOS", 8));

        var repartition = repartir(annabaEtBlida(), List.of(), patients, List.of(), List.of());

        assertThat(total(repartition, "CNAS").patientsPublies()).isNull();
        assertThat(total(repartition, "CASNOS").patientsPublies()).isEqualTo(18L);
        assertThat(total(repartition, "CNAS").patients()).isEqualTo(42);
    }

    @Test
    void le_total_d_une_caisse_est_supprime_quand_tous_ses_centres_sont_masques_et_publie_a_cinq_pile() {
        var patients = List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 1), new PatientsDeLaCaisse(B, "CNAS", "CNAS", 4),
                new PatientsDeLaCaisse(A, "CASNOS", "CASNOS", 5), new PatientsDeLaCaisse(B, "CASNOS", "CASNOS", 0));

        var repartition = repartir(annabaEtBlida(), List.of(), patients, List.of(), List.of());

        assertThat(total(repartition, "CNAS").patientsPublies()).isNull();
        assertThat(total(repartition, "CASNOS").patientsPublies()).isEqualTo(5L);
    }

    @Test
    void une_societe_a_un_seul_centre_masque_n_a_pas_de_total_de_caisse_et_zero_reste_zero() {
        var seul = societe(new CentreDeLaSociete(A, "Annaba", true));
        var patients = List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 3), new PatientsDeLaCaisse(A, "CASNOS", "CASNOS", 0));

        var repartition = repartir(seul, List.of(), patients, List.of(), List.of());

        assertThat(total(repartition, "CNAS").patientsPublies()).isNull();
        assertThat(total(repartition, "CASNOS").patientsPublies()).isEqualTo(0L);
    }

    @Test
    void la_suppression_complementaire_ne_touche_ni_les_seances_ni_le_chiffre_d_affaires_du_total() {
        var patients = List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 3));
        var seances = List.of(new SeancesDeLaCaisse(A, "CNAS", "CNAS", 40));
        var chiffre = List.of(new ChiffreDeLaCaisse(A, "CNAS", "CNAS", new BigDecimal("10.00")));

        var cnas = total(repartir(annabaEtBlida(), List.of(), patients, seances, chiffre), "CNAS");

        assertThat(cnas.patientsPublies()).isNull();
        assertThat(cnas.seances()).isEqualTo(40);
        assertThat(cnas.caHt()).isEqualByComparingTo("10.00");
    }

    @Test
    void une_caisse_masquee_dun_centre_ne_masque_pas_les_seances_ni_le_chiffre_daffaires() {
        var repartition = repartir(annabaEtBlida(), List.of(), List.of(new PatientsDeLaCaisse(A, "CNAS", "CNAS", 2)),
                List.of(new SeancesDeLaCaisse(A, "CNAS", "CNAS", 40)),
                List.of(new ChiffreDeLaCaisse(A, "CNAS", "CNAS", new BigDecimal("1234.50"))));

        var ligne = repartition.caisses().getFirst();
        assertThat(ligne.patientsPublies()).isNull();
        assertThat(ligne.seances()).isEqualTo(40);
        assertThat(ligne.caHt()).isEqualByComparingTo("1234.50");
    }

    @Test
    void un_patient_sans_caisse_a_le_code_vide_et_garde_le_premier_nom_connu() {
        var patients = List.of(new PatientsDeLaCaisse(A, "", null, 6));
        var seances = List.of(new SeancesDeLaCaisse(A, "", "Sans caisse", 3));

        var repartition = repartir(annabaEtBlida(), List.of(), patients, seances, List.of());

        assertThat(repartition.caisses()).hasSize(1);
        assertThat(repartition.caisses().getFirst().code()).isEmpty();
        assertThat(repartition.caisses().getFirst().nom()).isEqualTo("Sans caisse");
    }

    @Test
    void le_taux_dadministration_est_la_part_des_administrations_effectives_a_une_decimale() {
        var anemie = Map.of(A, new AdministrationsDeLAnemie(3, 2, 1, 1, 1));

        var traitement = RepartitionDeLaSociete.constituer(annabaEtBlida(), PERIODE, AUJOURDHUI, GENERE_LE, List.of(), List.of(),
                List.of(), List.of(), anemie, Map.of(A, new PrescriptionsDeLAnemie(4, 6))).anemie();

        TraitementDeLAnemieDuCentre annaba = traitement.getFirst();
        assertThat(annaba.tauxAdministration()).isEqualByComparingTo("66.7");
        assertThat(annaba.sousEpoPublies()).isNull();
        assertThat(annaba.sousFerPublies()).isEqualTo(6L);
        assertThat(traitement.get(1).tauxAdministration()).isNull();
    }
}
