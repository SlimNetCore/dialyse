package com.hemodialyse.backend.contexts.seance.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Schéma H2 des tables réelles {@code volet_paramedical} et {@code volet_medical} : colonnes, types et {@code UNIQUE (seance_id)} de Flyway V1. */
final class TableDesVolets {
    private TableDesVolets() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS volet_paramedical (id UUID PRIMARY KEY, anticoagulant VARCHAR(100), center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE, debit_sang_ml_min INTEGER, duree_minutes INTEGER, incidents TEXT, "
                + "poids_apres_kg NUMERIC(38, 2), poids_avant_kg NUMERIC(38, 2), seance_id UUID NOT NULL UNIQUE, ta_apres VARCHAR(30), "
                + "ta_avant VARCHAR(30), type_dialysat VARCHAR(100), ultrafiltration_ml NUMERIC(38, 2), updated_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS volet_medical (id UUID PRIMARY KEY, ajustements_therapeutiques TEXT, center_id UUID NOT NULL, "
                + "conclusion_medicale TEXT, created_at TIMESTAMP WITH TIME ZONE, examen_clinique TEXT, prescription TEXT, "
                + "resultats_biologiques TEXT, seance_id UUID NOT NULL UNIQUE, tolerance_seance TEXT, updated_at TIMESTAMP WITH TIME ZONE)");
    }
}
