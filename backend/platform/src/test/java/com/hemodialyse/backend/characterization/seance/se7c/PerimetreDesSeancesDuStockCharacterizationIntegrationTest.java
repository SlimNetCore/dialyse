package com.hemodialyse.backend.characterization.seance.se7c;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice seance-se7c1-lectures-stock, filet COMPLÉMENTAIRE (étape 0) : la lecture de la salle et de la demi-journée d'une séance par le
 * contexte Stock n'était verrouillée qu'avec un double (GestionDesConsommationsTest) ; aucune requête SQL n'était observée. Écrit sur
 * l'ACL SQL d'origine (9 tests verts sur le legacy), il appelle depuis SE7c1 les ports du Stock câblés en production (ACL de la
 * plateforme puis lecture publiée de la Séance) sur la base (H2) : parité. Il verrouille chaque branche : séance inconnue,
 * d'un autre centre, salle et créneau nuls, salle ou créneau d'un autre centre ou disparu, matin / après-midi / midi / soir / ni
 * l'un ni l'autre, accents et casse. Il verrouille aussi le statut de la séance pour les cas que le filet F6 ne couvre pas (statut
 * absent de la liste). Indépendant de l'horloge ; assertions explicites (pas de golden).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PerimetreDesSeancesDuStockCharacterizationIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99997700-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99997700-0000-0000-0000-00000000000b");
    private static final UUID PATIENT_A = UUID.fromString("99997700-0000-0000-0000-00000000a001");
    private static final UUID SALLE_A = UUID.fromString("99997700-0000-0000-0000-000000005a01");
    private static final UUID SALLE_B = UUID.fromString("99997700-0000-0000-0000-000000005b01");
    private static final UUID CRENEAU_MATIN = UUID.fromString("99997700-0000-0000-0000-000000006a01");
    private static final UUID CRENEAU_B = UUID.fromString("99997700-0000-0000-0000-000000006b01");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PerimetreDesSeances perimetres;
    @Autowired
    private StatutDesSeances statuts;

    @BeforeEach
    void donnees() {
        nettoyer();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                + "type_patient, etat_patient, created_at) VALUES (?, ?, 'P-SE7C', 'TEST', 'Test', 'F', ?, 'ASS-SE7C', "
                + "'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)", PATIENT_A, CENTRE_A, Date.valueOf(LocalDate.of(2025, 1, 10)));
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'S-A', 'Salle A')", SALLE_A, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'S-B', 'Salle B')", SALLE_B, CENTRE_B);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'C1', 'Premiere vague')",
                CRENEAU_MATIN, CENTRE_A);
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'MATIN', 'Matin B')", CRENEAU_B,
                CENTRE_B);
    }

    @AfterEach
    void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("seances", "patients", "salle", "position_creneau")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    private UUID seance(UUID centre, String statut, UUID salle, UUID creneau) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, salle_id, creneau_id, created_at, "
                        + "hors_planning) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)", id, PATIENT_A, centre,
                Date.valueOf(LocalDate.of(2026, 2, 10)), statut, salle, creneau);
        return id;
    }

    private UUID creneau(String code, String libelle) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, CENTRE_A, code, libelle);
        return id;
    }

    private PerimetreDeSortie lire(UUID seance) {
        return perimetres.perimetreDe(new CentreId(CENTRE_A), seance);
    }

    @Test
    void une_seance_inconnue_donne_le_perimetre_de_la_journee() {
        assertThat(lire(UUID.randomUUID())).isEqualTo(PerimetreDeSortie.journee());
    }

    @Test
    void une_seance_d_un_autre_centre_donne_le_perimetre_de_la_journee() {
        UUID autre = seance(CENTRE_B, "VALIDEE", SALLE_B, CRENEAU_B);
        assertThat(lire(autre)).isEqualTo(PerimetreDeSortie.journee());
        assertThat(perimetres.perimetreDe(new CentreId(CENTRE_B), autre)).isEqualTo(new PerimetreDeSortie("S-B", DemiJournee.MATIN));
    }

    @Test
    void une_seance_sans_salle_ni_creneau_donne_le_perimetre_de_la_journee() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, null))).isEqualTo(PerimetreDeSortie.journee());
    }

    @Test
    void la_salle_est_le_code_de_la_salle_du_centre() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", SALLE_A, null))).isEqualTo(new PerimetreDeSortie("S-A", null));
    }

    @Test
    void une_salle_d_un_autre_centre_est_ignoree() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", SALLE_B, null))).isEqualTo(PerimetreDeSortie.journee());
    }

    @Test
    void un_creneau_d_un_autre_centre_est_ignore() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, CRENEAU_B))).isEqualTo(PerimetreDeSortie.journee());
    }

    @Test
    void un_creneau_sans_mot_connu_ne_donne_aucune_demi_journee() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", SALLE_A, CRENEAU_MATIN))).isEqualTo(new PerimetreDeSortie("S-A", null));
    }

    @Test
    void la_demi_journee_se_lit_dans_le_code_ou_le_libelle_du_creneau_sans_accent_ni_casse() {
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("M1", "matin"))).demiJournee()).as("libelle matin")
                .isEqualTo(DemiJournee.MATIN);
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("MATIN", "-"))).demiJournee()).as("code MATIN, libelle neutre (colonne NOT NULL)")
                .isEqualTo(DemiJournee.MATIN);
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("AM", "Après-midi"))).demiJournee()).as("apres-midi accentue")
                .isEqualTo(DemiJournee.APRES_MIDI);
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("MIDI", "x"))).demiJournee()).as("midi")
                .isEqualTo(DemiJournee.APRES_MIDI);
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("S3", "Soirée"))).demiJournee()).as("soir")
                .isEqualTo(DemiJournee.APRES_MIDI);
        assertThat(lire(seance(CENTRE_A, "VALIDEE", null, creneau("P2", "Matin puis soir"))).demiJournee())
                .as("apres-midi l'emporte sur matin").isEqualTo(DemiJournee.APRES_MIDI);
    }

    @Test
    void une_seance_au_statut_hors_facturee_n_est_pas_facturee() {
        CentreId a = new CentreId(CENTRE_A);
        assertThat(statuts.estFacturee(a, seance(CENTRE_A, "CREE", null, null))).isFalse();
        assertThat(statuts.estFacturee(a, seance(CENTRE_A, "ABSENT", null, null))).isFalse();
        assertThat(statuts.estFacturee(a, seance(CENTRE_A, "FACTUREE", null, null))).isTrue();
    }
}
