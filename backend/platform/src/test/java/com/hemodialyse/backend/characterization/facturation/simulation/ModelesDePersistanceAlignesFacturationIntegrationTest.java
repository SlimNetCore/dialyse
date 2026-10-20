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
 * Slices F2 et F3 : la simulation et la validation lisent et écrit en SQL direct (ACL) des tables d'autres modules (séances, patients, forfaits, prises en
 * charge, séquence de numérotation) : il n'y a pas de modèle de persistance du module à comparer aux entités. Les colonnes que ce
 * SQL utilise sont donc comparées à la description figée du schéma de production (migration V1, V39 pour les deux colonnes de
 * l'exclusion et V40 pour l'auteur de l'émission) : mêmes longueurs, précisions, nullités. Si le schéma de développement (H2, entités JPA et {@code schema.sql})
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

    // ---- Slice F3 : validation et émission (colonnes écrites dans factures et facture_lignes, lues dans patients)

    @Test
    void factures_ecrites_par_la_validation_f3() {
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "patient_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "numero_facture[longueur=120, precision=null, echelle=null, nullable=NO]",
                        "patient_code[longueur=80, precision=null, echelle=null, nullable=YES]",
                        "patient_full_name[longueur=255, precision=null, echelle=null, nullable=YES]",
                        "patient_status_snapshot[longueur=120, precision=null, echelle=null, nullable=YES]",
                        "numero_immatriculation_snapshot[longueur=120, precision=null, echelle=null, nullable=YES]",
                        "centre_payeur_id_snapshot[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "agence_id_snapshot[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "period_start[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "period_end[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "date_facturation[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "tva_rate[longueur=null, precision=5, echelle=2, nullable=NO]",
                        "total_ht[longueur=null, precision=14, echelle=2, nullable=NO]",
                        "total_tva[longueur=null, precision=14, echelle=2, nullable=NO]",
                        "total_ttc[longueur=null, precision=14, echelle=2, nullable=NO]",
                        "emise_par[longueur=null, precision=null, echelle=null, nullable=YES]"),
                colonnes("factures", "id", "center_id", "patient_id", "numero_facture", "patient_code", "patient_full_name",
                        "patient_status_snapshot", "numero_immatriculation_snapshot", "centre_payeur_id_snapshot",
                        "agence_id_snapshot", "period_start", "period_end", "date_facturation", "tva_rate", "total_ht", "total_tva",
                        "total_ttc", "emise_par"),
                "colonnes de factures");
    }

    @Test
    void facture_lignes_ecrites_par_la_validation_f3() {
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "facture_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "forfait_id[longueur=null, precision=null, echelle=null, nullable=YES]",
                        "forfait_label[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "unit_price_ht[longueur=null, precision=14, echelle=2, nullable=NO]",
                        "seance_count[longueur=null, precision=32, echelle=0, nullable=NO]",
                        "line_ht[longueur=null, precision=14, echelle=2, nullable=NO]"),
                colonnes("facture_lignes", "id", "facture_id", "center_id", "forfait_id", "forfait_label", "unit_price_ht",
                        "seance_count", "line_ht"),
                "colonnes de facture_lignes");
    }

    @Test
    void payeur_du_patient_lu_pour_l_instantane_de_la_facture_f3() {
        assertEquals(attendues(
                        "numero_assurance[longueur=255, precision=null, echelle=null, nullable=NO]",
                        "centre_payeur_id[longueur=null, precision=null, echelle=null, nullable=YES]"),
                colonnes("patients", "numero_assurance", "centre_payeur_id"), "payeur des patients");
        assertEquals(attendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                        "agence_id[longueur=null, precision=null, echelle=null, nullable=YES]"),
                colonnes("centre_payeur", "id", "center_id", "agence_id"), "colonnes de centre_payeur");
    }
}
