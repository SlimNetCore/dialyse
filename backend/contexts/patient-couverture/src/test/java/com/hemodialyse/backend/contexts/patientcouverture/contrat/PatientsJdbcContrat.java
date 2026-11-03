package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesMouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.MouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PatientsJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat contre les vraies tables {@code patients} et {@code mouvement_patient} : le jeu commun, plus ce qui n'a de sens qu'en
 * base (la place n'est jamais écrite par la fiche, ligne héritée aux colonnes nulles, journal des mouvements). Exécuté sur H2 et
 * sur PostgreSQL 16.
 */
abstract class PatientsJdbcContrat extends PatientsContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected PatientRepository depot() {
        return new PatientsJdbc(jdbc());
    }

    @Test
    void should_ne_jamais_ecrire_la_place_ni_les_jours_de_dialyse_la_planification_en_est_proprietaire() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-1", null, null));
        depot().enregistrer(p);
        UUID salle = UUID.randomUUID();
        jdbc().update("UPDATE patients SET salle_id = ?, jour_lundi = TRUE WHERE id = ?", salle, p.id().valeur());
        Patient relu = depot().trouver(CENTRE, p.id()).orElseThrow();
        relu.modifier(Fiches.nominale("JD-1", null, null), Fiches.AUJOURDHUI, T0, UUID::randomUUID);

        depot().enregistrer(relu);

        assertThat(jdbc().queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, p.id().valeur())).isEqualTo(salle);
        assertThat(jdbc().queryForObject("SELECT jour_lundi FROM patients WHERE id = ?", Boolean.class, p.id().valeur())).isTrue();
    }

    @Test
    void should_ecrire_l_etat_et_sa_date_dans_les_vraies_colonnes() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-2", EtatDuPatient.TRANSFERE, LocalDate.of(2026, 10, 5)));
        depot().enregistrer(p);

        assertThat(jdbc().queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, p.id().valeur()))
                .isEqualTo("TRANSFERE");
        assertThat(jdbc().queryForObject("SELECT date_evenement_etat FROM patients WHERE id = ?", Date.class, p.id().valeur()))
                .isEqualTo(Date.valueOf(LocalDate.of(2026, 10, 5)));
    }

    @Test
    void should_relire_une_ligne_heritee_aux_colonnes_nulles_comme_permanente_et_la_laisser_modifier() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, type_patient, numero_assurance, date_admission, "
                        + "date_naissance) VALUES (?,?,?,?,?,?,?,?,?)", id, CENTRE.valeur(), "Heritee", "Ligne", "F",
                "NON_VACANCIER", "HER-1", Date.valueOf(LocalDate.of(2020, 1, 1)), Date.valueOf(LocalDate.of(1960, 1, 1)));

        Patient relu = depot().trouver(CENTRE, new com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId(id))
                .orElseThrow();

        assertThat(relu.etat()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(relu.fiche().suivi().enSommeil()).isFalse();
        assertThat(relu.fiche().affectation().jours().codes()).isEmpty();
        relu.modifier(Fiches.avec("HER-1", null, null, LocalDate.of(2020, 1, 1), LocalDate.of(1960, 1, 1)), Fiches.AUJOURDHUI, T0,
                UUID::randomUUID);
        depot().enregistrer(relu);
        assertThat(jdbc().queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, id)).isEqualTo("PERMANENT");
    }

    @Test
    void should_ajouter_un_mouvement_et_le_relire_du_plus_recent_au_plus_ancien() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-3", null, null));
        depot().enregistrer(p);
        var mouvements = new MouvementsJdbc(jdbc());
        MouvementDuPatient admission = p.retirerLesMouvements().get(0);
        mouvements.ajouter(admission);
        p.modifier(Fiches.nominale("JD-3", EtatDuPatient.GUERRI, LocalDate.of(2026, 10, 3)), Fiches.AUJOURDHUI, T0, UUID::randomUUID);
        p.retirerLesMouvements().forEach(mouvements::ajouter);
        depot().enregistrer(p);

        var lues = new LectureDesMouvementsJdbc(jdbc()).lister(CENTRE,
                new LectureDesMouvements.Filtre(p.id().valeur(), null, null, null), 0, 20);

        assertThat(lues.total()).isEqualTo(2);
        assertThat(lues.items()).extracting(LectureDesMouvements.Ligne::type)
                .containsExactly(TypeDeMouvement.GUERISON, TypeDeMouvement.ADMISSION);
        assertThat(lues.items().get(0).etatPrecedent()).isEqualTo("PERMANENT");
        assertThat(lues.items().get(0).patientNom()).isEqualTo("Karim Benali");
        var filtrees = new LectureDesMouvementsJdbc(jdbc()).lister(CENTRE,
                new LectureDesMouvements.Filtre(null, TypeDeMouvement.GUERISON, null, null), 0, 20);
        assertThat(filtrees.items()).extracting(LectureDesMouvements.Ligne::patientId).contains(p.id().valeur());
        assertThat(new LectureDesMouvementsJdbc(jdbc()).lister(AUTRE, new LectureDesMouvements.Filtre(null, null, null, null), 0, 20)
                .items()).isEmpty();
    }
}
