package com.hemodialyse.backend.contexts.seance.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Schéma H2 de la table réelle {@code seances} : colonnes et types de Flyway V1, V21, V23, V26 et V39 (28 colonnes, aucune contrainte d'unicité). */
final class TableDesSeances {
    private TableDesSeances() {
    }

    static final String[] COLONNES = {"id", "center_id", "created_at", "date_seance", "facture_id", "forfait_override_code",
            "forfait_override_id", "forfait_override_nom", "forfait_override_prix", "forfait_override_updated_at",
            "forfait_override_updated_by", "patient_id", "signed_infirmier_at", "signed_infirmier_by", "signed_medecin_at",
            "signed_medecin_by", "statut", "validated_at", "regularisation_deverrouillee_at", "regularisation_deverrouillee_by",
            "hors_planning", "motif_hors_planning", "precision_hors_planning", "salle_id", "creneau_id", "generateur_id",
            "statut_avant_exclusion", "exclusion_absence_id"};

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE, date_seance DATE NOT NULL, facture_id UUID, forfait_override_code VARCHAR(50), "
                + "forfait_override_id UUID, forfait_override_nom VARCHAR(255), forfait_override_prix NUMERIC(14, 2), "
                + "forfait_override_updated_at TIMESTAMP WITH TIME ZONE, forfait_override_updated_by VARCHAR(100), patient_id UUID NOT NULL, "
                + "signed_infirmier_at TIMESTAMP WITH TIME ZONE, signed_infirmier_by VARCHAR(100), signed_medecin_at TIMESTAMP WITH TIME ZONE, "
                + "signed_medecin_by VARCHAR(100), statut VARCHAR(255) NOT NULL, validated_at TIMESTAMP WITH TIME ZONE, "
                + "regularisation_deverrouillee_at TIMESTAMP WITH TIME ZONE, regularisation_deverrouillee_by VARCHAR(100), "
                + "hors_planning BOOLEAN DEFAULT FALSE NOT NULL, motif_hors_planning VARCHAR(20), precision_hors_planning VARCHAR(255), "
                + "salle_id UUID, creneau_id UUID, generateur_id UUID, statut_avant_exclusion VARCHAR(20), exclusion_absence_id UUID)");
        retirerLesContraintesDeProduction(jdbc);
    }

    /**
     * Sur la base PostgreSQL 16 JETABLE, retire les deux contraintes de production de {@code seances} (V54 clé étrangère, V55 CHECK du
     * statut, ADR 0114, toutes deux NOT VALID) : les contrats de LECTURE y posent volontairement des statuts non canoniques (matrices de
     * KB-SEA-70/82) et des identifiants de facture sans facture. Elles sont testées pour elles-mêmes par
     * {@code LiaisonConcurrentePostgresContractIT}, qui les rétablit. Sans effet sur H2 (qui ne les porte pas).
     */
    static void retirerLesContraintesDeProduction(JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE seances DROP CONSTRAINT IF EXISTS fk_seances_facture");
        jdbc.execute("ALTER TABLE seances DROP CONSTRAINT IF EXISTS ck_seances_statut_canonique");
    }

    /** Rétablit les deux contraintes comme les migrations V54 et V55 les posent (NOT VALID), sur la base jetable. */
    static void poserLesContraintesDeProduction(JdbcTemplate jdbc) {
        retirerLesContraintesDeProduction(jdbc);
        jdbc.execute("ALTER TABLE seances ADD CONSTRAINT fk_seances_facture FOREIGN KEY (facture_id) REFERENCES factures (id) NOT VALID");
        jdbc.execute("ALTER TABLE seances ADD CONSTRAINT ck_seances_statut_canonique CHECK (statut = UPPER(TRIM(statut))) NOT VALID");
    }
}
