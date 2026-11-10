package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesSuivies;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.PatientsDesCentresJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesSocietesJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation.SocietesEtCentresPublies;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des lectures du Pilotage direction sur les tables de la plateforme (société, centres, patients) : le MÊME jeu de tests joue sur
 * H2 (mode PostgreSQL) et sur un PostgreSQL 16 réel. Reprend les cas de l'ancien {@code EffectifSqlTest} de la plateforme (admis après la
 * fin exclu, sortie avant / pendant / après la période, règle du décès et de la greffe à la veille, séjour limité, sortie sans date).
 * Les lectures de la caisse d'assurance passent par la jointure déjà rejouée par les goldens PD1 (sans caisse : code vide).
 */
abstract class LecturesDuPilotageContrat {

    static final UUID SOCIETE = UUID.fromString("99990500-0000-0000-0000-000000000001");
    static final UUID C1 = UUID.fromString("99990500-0000-0000-0000-0000000000c1");
    static final UUID C2 = UUID.fromString("99990500-0000-0000-0000-0000000000c2");
    static final Periode SEPTEMBRE = new Periode(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    static final LocalDate ADMIS = LocalDate.of(2025, 1, 10);

    protected abstract JdbcTemplate jdbc();

    /** RF3b (ADR 0127) : la caisse d'un centre payeur est lue dans l'annuaire des payeurs de Patient et couverture, sur les mêmes tables. */
    private PatientsDesCentresJdbc lecteurDesPatients() {
        return new PatientsDesCentresJdbc(jdbc(), new com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture
                .CaissesParLAnnuaireDesPayeurs(new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence
                .PayeursDuCentreJdbc(jdbc())));
    }

    protected void preparer() {
        nettoyer();
        jdbc().update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-PD5", "Société PD");
        jdbc().update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C1, "ZT-PD5-B", "Blida", SOCIETE);
        jdbc().update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", C2, "ZT-PD5-A", "Annaba", SOCIETE);
    }

    protected void nettoyer() {
        jdbc().update("DELETE FROM patients WHERE center_id IN (?,?)", C1, C2);
        for (String table : List.of("centre_payeur", "agence", "caisse_assurance")) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?,?)", C1, C2);
        }
        jdbc().update("DELETE FROM centers WHERE code LIKE 'ZT-PD5%'");
        jdbc().update("DELETE FROM societes WHERE code LIKE 'ZT-PD5%'");
    }

    private void patient(UUID centre, String sexe, String etat, LocalDate evenement, LocalDate admission, boolean sousKt) {
        patient(centre, sexe, etat, evenement, admission, sousKt, null);
    }

    private void patient(UUID centre, String sexe, String etat, LocalDate evenement, LocalDate admission, boolean sousKt, UUID payeur) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, date_evenement_etat, qualite_assure, sous_kt, epo_enabled, "
                        + "centre_payeur_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, centre, "PD-" + id.toString().substring(0, 8), "NOM", "Prenom", sexe, Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), admission == null ? null : Date.valueOf(admission), "NON_VACANCIER", etat,
                evenement == null ? null : Date.valueOf(evenement), "ASSURE", sousKt, "M".equals(sexe), payeur);
    }

    /** Une caisse, son agence et un centre payeur rattaché, tous du centre donné. */
    private UUID payeur(UUID centre, String codeCaisse) {
        UUID caisse = UUID.randomUUID();
        UUID agence = UUID.randomUUID();
        UUID payeur = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisse, centre, codeCaisse, "Caisse " + codeCaisse);
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agence, centre, caisse, "AG-" + codeCaisse,
                "Agence");
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", payeur, centre, agence,
                "CP-" + codeCaisse, "Payeur");
        return payeur;
    }

    @Test
    void les_centres_de_la_societe_sont_tries_par_nom_avec_leur_etat() {
        var societe = new SocietesEtCentresPublies(new AnnuaireDesSocietesJdbc(jdbc())).societe(SOCIETE);

        assertThat(societe.raisonSociale()).isEqualTo("Société PD");
        assertThat(societe.centres()).extracting("nom").containsExactly("Annaba", "Blida");
        assertThat(societe.centres()).allMatch(c -> c.actif());
    }

    @Test
    void une_societe_inconnue_est_signalee() {
        assertThatThrownBy(() -> new SocietesEtCentresPublies(new AnnuaireDesSocietesJdbc(jdbc())).societe(UUID.randomUUID()))
                .isInstanceOf(SocietesSuivies.SocieteInconnue.class);
    }

    @Test
    void l_effectif_exclut_les_admis_apres_la_fin_et_les_sortis_avant_le_debut() {
        patient(C1, "M", "PERMANENT", null, ADMIS, false);
        patient(C1, "F", "PERMANENT", null, LocalDate.of(2026, 9, 30), false);   // admis le dernier jour : compte
        patient(C1, "F", "PERMANENT", null, LocalDate.of(2026, 10, 1), false);   // admis après la fin : exclu
        patient(C1, "M", "TRANSFERE", LocalDate.of(2026, 8, 31), ADMIS, false);  // sorti avant le début : exclu
        patient(C1, "M", "TRANSFERE", LocalDate.of(2026, 9, 1), ADMIS, true);    // transfert le premier jour : compte
        patient(C1, "M", "TRANSFERE", LocalDate.of(2026, 12, 1), ADMIS, false);  // sortie après la période : compte

        var effectifs = lecteurDesPatients().parCentre(List.of(C1, C2), SEPTEMBRE);

        assertThat(effectifs).containsOnlyKeys(C1);
        assertThat(effectifs.get(C1).patients()).isEqualTo(4);
        assertThat(effectifs.get(C1).sousKt()).isEqualTo(1);
    }

    @Test
    void un_deces_ou_une_greffe_le_premier_jour_libere_la_place_mais_pas_un_transfert_ou_une_guerison() {
        LocalDate premier = SEPTEMBRE.debut();
        patient(C1, "M", "DECEDE", premier, ADMIS, false);
        patient(C1, "M", "GREFFE", premier, ADMIS, false);
        patient(C1, "M", "DECEDE", premier.plusDays(1), ADMIS, false);
        patient(C1, "M", "GUERRI", premier, ADMIS, false);
        patient(C1, "M", "TRANSFERE", premier, ADMIS, false);

        assertThat(lecteurDesPatients().parCentre(List.of(C1), SEPTEMBRE).get(C1).patients()).isEqualTo(3);
    }

    @Test
    void une_sortie_sans_date_n_est_plus_comptee_et_un_sejour_limite_sans_date_l_est() {
        patient(C1, "M", "DECEDE", null, ADMIS, false);
        patient(C1, "M", "TRANSFERE", null, ADMIS, false);
        patient(C1, "M", "VACANCIER_LOCAL", null, ADMIS, false);
        patient(C1, "M", "OCCASIONNEL", LocalDate.of(2026, 8, 31), ADMIS, false);   // fin de séjour avant le début : exclu
        patient(C1, "M", "OCCASIONNEL", LocalDate.of(2026, 9, 1), ADMIS, false);    // fin de séjour incluse : compte

        assertThat(lecteurDesPatients().parCentre(List.of(C1), SEPTEMBRE).get(C1).patients()).isEqualTo(2);
    }

    @Test
    void le_profil_et_la_caisse_portent_sur_le_meme_effectif_sans_identifier_de_patient() {
        patient(C1, "M", "PERMANENT", null, ADMIS, false);
        patient(C1, "F", "PERMANENT", null, ADMIS, false);
        patient(C1, "F", "DECEDE", LocalDate.of(2026, 3, 1), ADMIS, false);       // sorti : exclu

        var lecture = lecteurDesPatients();

        assertThat(lecture.profils(List.of(C1), SEPTEMBRE)).hasSize(2).allMatch(p -> p.centreId().equals(C1));
        var caisses = lecture.parCaisse(List.of(C1), SEPTEMBRE);
        assertThat(caisses).hasSize(1);
        assertThat(caisses.getFirst().code()).isEmpty();
        assertThat(caisses.getFirst().patients()).isEqualTo(2);
    }

    @Test
    void un_patient_dont_le_centre_payeur_est_d_un_autre_centre_tombe_sur_la_caisse_vide_comme_ses_seances_kb_sea_84_corrige() {
        UUID payeurDuC1 = payeur(C1, "CNAS");
        UUID payeurDuC2 = payeur(C2, "CASNOS");
        patient(C1, "M", "PERMANENT", null, ADMIS, false, payeurDuC1);
        patient(C1, "F", "PERMANENT", null, ADMIS, false, payeurDuC2);   // chaîne payeur du centre C2 : caisse vide
        patient(C1, "F", "PERMANENT", null, ADMIS, false, null);

        var caisses = lecteurDesPatients().parCaisse(List.of(C1), SEPTEMBRE);

        assertThat(caisses).extracting("code", "patients").containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("CNAS", 1L), org.assertj.core.groups.Tuple.tuple("", 2L));
    }

    @Test
    void un_patient_dont_l_agence_est_d_un_autre_centre_que_son_centre_payeur_tombe_sur_la_caisse_vide_l8() {
        UUID caisseDuC2 = UUID.randomUUID();
        UUID agenceDuC2 = UUID.randomUUID();
        UUID payeur = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisseDuC2, C2, "CASNOS", "Caisse");
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agenceDuC2, C2, caisseDuC2, "AG", "Agence");
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", payeur, C1, agenceDuC2, "CP", "Payeur");
        patient(C1, "M", "PERMANENT", null, ADMIS, false, payeur);   // centre payeur du C1, agence du C2 : caisse vide

        var caisses = lecteurDesPatients().parCaisse(List.of(C1), SEPTEMBRE);

        assertThat(caisses).extracting("code", "patients").containsExactly(org.assertj.core.groups.Tuple.tuple("", 1L));
    }

    @Test
    void les_prescriptions_de_l_anemie_comptent_les_patients_sous_epo_de_l_effectif() {
        patient(C1, "M", "PERMANENT", null, ADMIS, false);   // epo_enabled = vrai pour le sexe M dans ce jeu
        patient(C1, "F", "PERMANENT", null, ADMIS, false);

        var prescriptions = lecteurDesPatients().prescriptionsDeLAnemie(List.of(C1), SEPTEMBRE);

        assertThat(prescriptions.get(C1).sousEpo()).isEqualTo(1);
    }
}
