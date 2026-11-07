package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesDExamenJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des demandes d'examen sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class DemandesDExamenJdbcContrat extends DemandesDExamenContrat {

    protected abstract JdbcTemplate jdbc();

    /** Schéma H2 des deux tables (colonnes et contraintes du schéma réel ; les lignes n'ont PAS de center_id : ADR 0072). */
    static void creerLesTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS demandes_examen (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "prescripteur_id VARCHAR(100), date_demande DATE NOT NULL, categorie VARCHAR(15) NOT NULL, urgent BOOLEAN NOT NULL, "
                + "motif VARCHAR(500), statut VARCHAR(20) NOT NULL, conclusion TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lignes_demande_examen (id UUID PRIMARY KEY, demande_id UUID NOT NULL, "
                + "analyte_code_system VARCHAR(10), analyte_code VARCHAR(20), analyte_code_display VARCHAR(255), libelle VARCHAR(255), "
                + "commentaire VARCHAR(500))");
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM lignes_demande_examen WHERE demande_id IN (SELECT id FROM demandes_examen WHERE center_id = ?)", centre);
            jdbc().update("DELETE FROM demandes_examen WHERE center_id = ?", centre);
        }
    }

    @Override
    protected DemandesDExamen demandes() {
        return new DemandesDExamenJdbc(jdbc());
    }

    private UUID poser(String statut, String categorie, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
                        + "conclusion, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, PATIENT.valeur(), CENTRE_A.valeur(), "dr",
                java.sql.Date.valueOf(date), categorie, false, null, statut, null, Timestamp.from(T0), Timestamp.from(T0));
        jdbc().update("INSERT INTO lignes_demande_examen (id, demande_id, libelle) VALUES (?,?,?)", UUID.randomUUID(), id, "Examen");
        return id;
    }

    @Test
    void une_donnee_incoherente_en_base_est_ignoree_par_la_liste_et_par_la_recherche_sans_casser_les_autres() {
        UUID bonne = poser("DEMANDE", "BIOLOGIE", LocalDate.of(2026, 1, 1));
        UUID statutInconnu = poser("EN_COURS", "BIOLOGIE", LocalDate.of(2026, 1, 2));
        UUID categorieInconnue = poser("DEMANDE", "SCANNER", LocalDate.of(2026, 1, 3));
        var page = demandes().duPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(bonne);
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, statutInconnu)).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, categorieInconnue)).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    @Test
    void une_demande_dont_une_ligne_est_illisible_est_ignoree_a_chaque_lecture_sans_casser_les_autres() {
        UUID bonne = poser("DEMANDE", "BIOLOGIE", LocalDate.of(2026, 1, 1));
        UUID nomenclatureInconnue = poser("DEMANDE", "BIOLOGIE", LocalDate.of(2026, 1, 2));
        jdbc().update("INSERT INTO lignes_demande_examen (id, demande_id, analyte_code_system, analyte_code) VALUES (?,?,?,?)", UUID.randomUUID(),
                nomenclatureInconnue, "INCONNU", "X1");
        UUID loincInvalide = poser("PRELEVE", "IMAGERIE", LocalDate.of(2026, 1, 3));
        jdbc().update("INSERT INTO lignes_demande_examen (id, demande_id, analyte_code_system, analyte_code) VALUES (?,?,?,?)", UUID.randomUUID(),
                loincInvalide, "LOINC", "pas-un-loinc");
        for (int lecture = 0; lecture < 2; lecture++) {
            var page = demandes().duPatient(CENTRE_A, PATIENT, 0, 10);
            assertThat(page.items()).extracting(d -> d.id()).containsExactly(bonne);
            // Limite consignée (ADR 0082) : le total compte les demandes aux statut et catégorie lisibles, donc aussi les deux ignorées.
            assertThat(page.total()).isEqualTo(3);
        }
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, nomenclatureInconnue)).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, loincInvalide)).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    @Test
    void une_demande_sans_ligne_en_base_est_listee_avec_une_liste_de_lignes_vide() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
                        + "conclusion, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, PATIENT.valeur(), CENTRE_A.valeur(), null,
                java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)), "IMAGERIE", true, null, "PRELEVE", null, Timestamp.from(T0), Timestamp.from(T0));
        var page = demandes().duPatient(CENTRE_A, new PatientId(PATIENT.valeur()), 0, 10);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).lignes()).isEmpty();
    }

    @Test
    void les_lignes_n_ont_pas_de_colonne_de_centre() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'lignes_demande_examen'", String.class);
        assertThat(colonnes).contains("demande_id", "analyte_code_system", "analyte_code", "analyte_code_display", "libelle", "commentaire")
                .doesNotContain("center_id");
    }
}
