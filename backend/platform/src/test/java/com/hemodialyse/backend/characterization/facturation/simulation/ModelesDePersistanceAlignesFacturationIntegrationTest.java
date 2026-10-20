package com.hemodialyse.backend.characterization.facturation.simulation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slice F2 : la simulation lit et écrit en SQL direct (ACL) des tables d'autres modules (séances, patients, forfaits, prises en
 * charge, séquence de numérotation) : il n'y a pas de modèle de persistance du module à comparer aux entités. Les colonnes que ce
 * SQL utilise sont donc comparées à la description figée du schéma de production (migration V1, et V39 pour les deux colonnes de
 * l'exclusion) : mêmes longueurs, précisions, nullités. Si le schéma de développement (H2, entités JPA et {@code schema.sql})
 * dérive, la production — PostgreSQL, {@code ddl-auto: validate} — et les tests ne décrivent plus la même chose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ModelesDePersistanceAlignesFacturationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static Set<String> attendues(String... colonnes) {
        return new TreeSet<>(Arrays.asList(colonnes));
    }

    private Set<String> colonnes(String table, String... utilisees) {
        String liste = String.join("', '", utilisees);
        return new TreeSet<>(jdbc.query(
                "SELECT column_name, character_maximum_length, numeric_precision, numeric_scale, is_nullable "
                        + "FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) IN ('" + liste + "')",
                (rs, i) -> rs.getString("column_name").toLowerCase() + "[longueur=" + rs.getObject("character_maximum_length")
                        + ", precision=" + rs.getObject("numeric_precision") + ", echelle=" + rs.getObject("numeric_scale")
                        + ", nullable=" + rs.getString("is_nullable") + "]", table));
    }

    @Test
    void seances() {
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "patient_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "date_seance[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "statut[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "facture_id[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_override_id[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_override_nom[longueur=255, precision=null, echelle=null, nullable=YES]",
                        "forfait_override_prix[longueur=null, precision=14, echelle=2, nullable=YES]",
                        "forfait_override_updated_at[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_override_updated_by[longueur=100, precision=null, echelle=null, nullable=YES]",
                        "statut_avant_exclusion[longueur=20, precision=null, echelle=null, nullable=YES]",
                        "exclusion_absence_id[longueur=null, precision=null, echelle=null, nullable=YES]"),
                colonnes("seances", "id", "center_id", "patient_id", "date_seance", "statut", "facture_id", "forfait_override_id",
                        "forfait_override_nom", "forfait_override_prix", "forfait_override_updated_at",
                        "forfait_override_updated_by", "statut_avant_exclusion", "exclusion_absence_id"),
                "colonnes de seances");
    }

    @Test
    void patients() {
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "code_patient[longueur=255, precision=null, echelle=null, nullable=YES]",
                        "nom[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "prenom[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "etat_patient[longueur=255, precision=null, echelle=null, nullable=YES]"),
                colonnes("patients", "id", "center_id", "code_patient", "nom", "prenom", "etat_patient"), "colonnes de patients");
    }

    @Test
    void forfait() {
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "libelle[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "prix[longueur=null, precision=10, echelle=2, nullable=YES]"),
                colonnes("forfait", "id", "center_id", "libelle", "prix"), "colonnes de forfait");
    }

    @Test
    void prise_en_charge() {
        assertEquals(attendues(
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "patient_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "date_debut_demande[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "date_fin_demande[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "date_debut_effectif[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "date_fin_effectif[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_demande_id[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_effectif_id[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "created_at[longueur=null, precision=null, echelle=null, nullable=YES]"),
                colonnes("prise_en_charge", "center_id", "patient_id", "date_debut_demande", "date_fin_demande",
                        "date_debut_effectif", "date_fin_effectif", "forfait_demande_id", "forfait_effectif_id", "created_at"),
                "colonnes de prise_en_charge");
    }

    @Test
    void facture_sequence() {
        assertEquals(attendues(
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "seq_year[longueur=null, precision=32, echelle=0, nullable=NO]",
                        "seq_value[longueur=null, precision=32, echelle=0, nullable=NO]"),
                colonnes("facture_sequence", "center_id", "seq_year", "seq_value"), "colonnes de facture_sequence");
    }
}
