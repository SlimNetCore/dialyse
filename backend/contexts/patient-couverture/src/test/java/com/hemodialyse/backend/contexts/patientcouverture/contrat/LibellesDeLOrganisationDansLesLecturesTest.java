package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.LibellesDeLOrganisationJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDeRecherche;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.application.LignePatient;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesMouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesPatientsJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les libellés de l'Organisation (médecin, salle, créneau) dans la recherche des patients et dans le journal des mouvements (O4 lot C,
 * ADR 0109). Écrit VERT sur les lecteurs SQL d'origine AVANT la migration, avec les mêmes assertions après : identifiant inconnu ou NULL,
 * autre centre, médecin désactivé (V52), libellé blanc, filtres de la recherche par libellé ou par identifiant.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-libelles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LibellesDeLOrganisationDansLesLecturesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("0c0c0c0c-0000-4000-8000-00000000c001"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("0c0c0c0c-0000-4000-8000-00000000c002"));

    private static final UUID MEDECIN_COMPLET = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000001");
    private static final UUID MEDECIN_SANS_PRENOM = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000002");
    private static final UUID MEDECIN_PRENOM_BLANC = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000003");
    private static final UUID MEDECIN_DESACTIVE = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000004");
    private static final UUID MEDECIN_AUTRE_CENTRE = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000005");
    private static final UUID MEDECIN_INCONNU = UUID.fromString("0d0d0d0d-0000-4000-8000-0000000000ff");

    private static final UUID MATIN = UUID.fromString("0e0e0e0e-0000-4000-8000-000000000001");
    private static final UUID APRES_MIDI = UUID.fromString("0e0e0e0e-0000-4000-8000-000000000002");
    private static final UUID CRENEAU_AUTRE_CENTRE = UUID.fromString("0e0e0e0e-0000-4000-8000-000000000003");
    private static final UUID CRENEAU_INCONNU = UUID.fromString("0e0e0e0e-0000-4000-8000-0000000000ff");

    private static final UUID SALLE_1 = UUID.fromString("0f0f0f0f-0000-4000-8000-000000000001");
    private static final UUID SALLE_AUTRE_CENTRE = UUID.fromString("0f0f0f0f-0000-4000-8000-000000000002");
    private static final UUID SALLE_INCONNUE = UUID.fromString("0f0f0f0f-0000-4000-8000-0000000000ff");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS medecin (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255) NOT NULL, "
                + "prenom VARCHAR(255), specialite VARCHAR(255), actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS transporteur (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS forfait (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50), libelle VARCHAR(255))");
        PatientsJdbcContratTest.creerLesTables(jdbc);
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM medecin WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM salle WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM position_creneau WHERE center_id = ?", centre);
        }
        medecin(MEDECIN_COMPLET, CENTRE, "Dupont", "Jean", true);
        medecin(MEDECIN_SANS_PRENOM, CENTRE, "Martin", null, true);
        medecin(MEDECIN_PRENOM_BLANC, CENTRE, "Durand", "   ", true);
        medecin(MEDECIN_DESACTIVE, CENTRE, "Ancien", "Paul", false);
        medecin(MEDECIN_AUTRE_CENTRE, AUTRE, "Etranger", "Eve", true);
        creneau(MATIN, CENTRE, "MAT", "Matin");
        creneau(APRES_MIDI, CENTRE, "APM", "Apres-midi");
        creneau(CRENEAU_AUTRE_CENTRE, AUTRE, "SOIR", "Soir");
        salle(SALLE_1, CENTRE, "S1", "Salle un");
        salle(SALLE_AUTRE_CENTRE, AUTRE, "S9", "Salle neuf");
    }

    private LectureDesPatientsJdbc patients() {
        return new LectureDesPatientsJdbc(jdbc, new LibellesDeLOrganisationJdbc(jdbc));
    }

    private LectureDesMouvementsJdbc mouvements() {
        return new LectureDesMouvementsJdbc(jdbc, new LibellesDeLOrganisationJdbc(jdbc));
    }

    // ------------------------------------------------------------------ libellés des patients

    @Test
    void le_medecin_traitant_est_rendu_nom_puis_prenom_sans_blanc_de_bord_si_le_prenom_manque_ou_est_blanc() {
        patient("P1", "A", MEDECIN_COMPLET, null);
        patient("P2", "B", MEDECIN_SANS_PRENOM, null);
        patient("P3", "C", MEDECIN_PRENOM_BLANC, null);

        List<LignePatient> lignes = rechercher(critere(null, null)).items();

        assertThat(lignes).extracting(LignePatient::medecinTraitantNom).containsExactly("Dupont Jean", "Martin", "Durand");
    }

    @Test
    void un_medecin_desactive_garde_son_libelle() {
        patient("P1", "A", MEDECIN_DESACTIVE, null);

        assertThat(rechercher(critere(null, null)).items()).extracting(LignePatient::medecinTraitantNom).containsExactly("Ancien Paul");
    }

    @Test
    void un_medecin_inconnu_d_un_autre_centre_ou_absent_n_a_pas_de_libelle_mais_garde_son_identifiant() {
        patient("P1", "A", MEDECIN_INCONNU, null);
        patient("P2", "B", MEDECIN_AUTRE_CENTRE, null);
        patient("P3", "C", null, null);

        List<LignePatient> lignes = rechercher(critere(null, null)).items();

        assertThat(lignes).extracting(LignePatient::medecinTraitantNom).containsExactly(null, null, null);
        assertThat(lignes).extracting(LignePatient::medecinTraitantId).containsExactly(MEDECIN_INCONNU, MEDECIN_AUTRE_CENTRE, null);
    }

    @Test
    void le_creneau_du_patient_donne_son_code_et_son_libelle_seulement_dans_le_centre() {
        patient("P1", "A", null, MATIN);
        patient("P2", "B", null, CRENEAU_AUTRE_CENTRE);
        patient("P3", "C", null, CRENEAU_INCONNU);
        patient("P4", "D", null, null);

        List<LignePatient> lignes = rechercher(critere(null, null)).items();

        assertThat(lignes).extracting(LignePatient::positionCode).containsExactly("MAT", null, null, null);
        assertThat(lignes).extracting(LignePatient::positionLibelle).containsExactly("Matin", null, null, null);
        assertThat(lignes).extracting(LignePatient::positionId).containsExactly(MATIN, CRENEAU_AUTRE_CENTRE, CRENEAU_INCONNU, null);
    }

    @Test
    void deux_patients_du_meme_medecin_et_du_meme_creneau_partagent_le_libelle() {
        patient("P1", "A", MEDECIN_COMPLET, MATIN);
        patient("P2", "B", MEDECIN_COMPLET, MATIN);

        List<LignePatient> lignes = rechercher(critere(null, null)).items();

        assertThat(lignes).extracting(LignePatient::medecinTraitantNom).containsExactly("Dupont Jean", "Dupont Jean");
        assertThat(lignes).extracting(LignePatient::positionLibelle).containsExactly("Matin", "Matin");
    }

    // ------------------------------------------------------------------ filtres par libellé ou par identifiant

    @Test
    void le_filtre_medecin_cherche_dans_nom_prenom_et_prenom_nom_sans_casse_ou_dans_l_identifiant_et_reste_dans_le_centre() {
        patient("P1", "A", MEDECIN_COMPLET, null);
        patient("P2", "B", MEDECIN_SANS_PRENOM, null);
        patient("P3", "C", MEDECIN_AUTRE_CENTRE, null);

        assertThat(noms(critere("dupont", null))).containsExactly("A");
        assertThat(noms(critere("  JEAN dupont ", null))).containsExactly("A");
        assertThat(noms(critere("dupont jean", null))).containsExactly("A");
        assertThat(noms(critere("a", null))).containsExactly("A", "B");
        assertThat(noms(critere(MEDECIN_COMPLET.toString(), null))).containsExactly("A");
        assertThat(noms(critere("etranger", null))).isEmpty();
        assertThat(noms(critere("introuvable", null))).isEmpty();
    }

    @Test
    void le_filtre_creneau_prend_des_identifiants_ou_cherche_dans_le_code_et_le_libelle_et_reste_dans_le_centre() {
        patient("P1", "A", null, MATIN);
        patient("P2", "B", null, APRES_MIDI);
        patient("P3", "C", null, CRENEAU_AUTRE_CENTRE);

        assertThat(noms(critere(null, MATIN.toString()))).containsExactly("A");
        assertThat(noms(critere(null, MATIN + "," + APRES_MIDI))).containsExactly("A", "B");
        assertThat(noms(critere(null, CRENEAU_AUTRE_CENTRE.toString()))).containsExactly("C");
        assertThat(noms(critere(null, "matin"))).containsExactly("A");
        assertThat(noms(critere(null, "apm"))).containsExactly("B");
        assertThat(noms(critere(null, "MAT APM"))).isEmpty();
        assertThat(noms(critere(null, "soir"))).isEmpty();
        assertThat(noms(critere(null, "introuvable"))).isEmpty();
    }

    // ------------------------------------------------------------------ journal des mouvements

    @Test
    void le_journal_rend_le_nom_de_la_salle_et_le_libelle_du_creneau_sans_borne_de_centre_et_vide_si_disparus() {
        UUID p = patient("M1", "A", null, null);
        mouvement(p, 1, SALLE_1, MATIN);
        mouvement(p, 2, SALLE_AUTRE_CENTRE, CRENEAU_AUTRE_CENTRE);
        mouvement(p, 3, SALLE_INCONNUE, CRENEAU_INCONNU);
        mouvement(p, 4, null, null);

        var page = mouvements().lister(CENTRE, new LectureDesMouvements.Filtre(null, null, null, null), 0, 20);

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.items()).extracting(LectureDesMouvements.Ligne::salle).containsExactly(null, null, "Salle neuf", "Salle un");
        assertThat(page.items()).extracting(LectureDesMouvements.Ligne::creneau).containsExactly(null, null, "Soir", "Matin");
    }

    // ------------------------------------------------------------------ outillage

    private com.hemodialyse.backend.contexts.patientcouverture.application.PageDePatients rechercher(CriteresDeRecherche criteres) {
        return patients().rechercher(CENTRE, criteres);
    }

    private List<String> noms(CriteresDeRecherche criteres) {
        return patients().rechercher(CENTRE, criteres).items().stream().map(LignePatient::nom).toList();
    }

    private static CriteresDeRecherche critere(String medecin, String position) {
        return new CriteresDeRecherche(0, 50, null, null, null, null, null, null, null, null, null, medecin, position, null, null, null,
                null, null);
    }

    private void medecin(UUID id, CentreId centre, String nom, String prenom, boolean actif) {
        jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, actif) VALUES (?, ?, ?, ?, ?)", id, centre.valeur(), nom, prenom, actif);
    }

    private void creneau(UUID id, CentreId centre, String code, String libelle) {
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre.valeur(), code, libelle);
    }

    private void salle(UUID id, CentreId centre, String code, String nom) {
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre.valeur(), code, nom);
    }

    private UUID patient(String code, String nom, UUID medecin, UUID position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "medecin_traitant_id, position_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTRE.valeur(), code, nom, "Prenom", "F", Date.valueOf(LocalDate.of(2026, 1, 5)), "N-" + code, "PERMANENT", medecin,
                position);
        return id;
    }

    private void mouvement(UUID patient, int jour, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, salle_id, position_id, automatique, cree_le) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), CENTRE.valeur(), patient, "ADMISSION",
                Date.valueOf(LocalDate.of(2026, 3, jour)), salle, creneau, false, Timestamp.valueOf("2026-03-01 08:00:00"));
    }
}
