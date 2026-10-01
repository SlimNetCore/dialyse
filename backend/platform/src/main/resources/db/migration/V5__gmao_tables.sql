-- Migration V5: Création des tables GMAO (Gestion de Maintenance Assistée par Ordinateur)
-- Tables pour gérer la maintenance des générateurs de dialyse et stations de traitement d'eau

-- Table des équipements
CREATE TABLE IF NOT EXISTS gmao_equipements
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    code
    VARCHAR
(
    50
) NOT NULL UNIQUE,
    designation VARCHAR
(
    255
) NOT NULL,
    type VARCHAR
(
    50
) NOT NULL,
    fabricant VARCHAR
(
    100
),
    modele VARCHAR
(
    100
),
    numero_serie VARCHAR
(
    100
),
    date_installation TIMESTAMP NOT NULL,
    centre_id UUID NOT NULL,
    statut VARCHAR
(
    50
) NOT NULL DEFAULT 'EN_SERVICE',
    localisation VARCHAR
(
    255
),
    observations TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    cree_par UUID NOT NULL,
    modifie_par UUID,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_gmao_eq_centre FOREIGN KEY
(
    centre_id
) REFERENCES centres
(
    id
),
    CONSTRAINT fk_gmao_eq_cree_par FOREIGN KEY
(
    cree_par
) REFERENCES utilisateurs
(
    id
),
    CONSTRAINT fk_gmao_eq_modifie_par FOREIGN KEY
(
    modifie_par
) REFERENCES utilisateurs
(
    id
)
    );

CREATE INDEX IF NOT EXISTS idx_gmao_equipements_centre_id ON gmao_equipements(centre_id);
CREATE INDEX IF NOT EXISTS idx_gmao_equipements_statut ON gmao_equipements(statut);
CREATE INDEX IF NOT EXISTS idx_gmao_equipements_code ON gmao_equipements(code);

-- Table des interventions
CREATE TABLE IF NOT EXISTS gmao_interventions
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    equipement_id
    UUID
    NOT
    NULL,
    centre_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    50
) NOT NULL,
    statut VARCHAR
(
    50
) NOT NULL DEFAULT 'PLANIFIEE',
    date_debut TIMESTAMP NOT NULL,
    date_fin TIMESTAMP,
    technicien_id UUID,
    description TEXT NOT NULL,
    actions TEXT,
    piece_remplacee VARCHAR
(
    255
),
    cout DECIMAL
(
    10,
    2
),
    observations TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    cree_par UUID NOT NULL,
    modifie_par UUID,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_gmao_int_equipement FOREIGN KEY
(
    equipement_id
) REFERENCES gmao_equipements
(
    id
),
    CONSTRAINT fk_gmao_int_centre FOREIGN KEY
(
    centre_id
) REFERENCES centres
(
    id
),
    CONSTRAINT fk_gmao_int_technicien FOREIGN KEY
(
    technicien_id
) REFERENCES utilisateurs
(
    id
),
    CONSTRAINT fk_gmao_int_cree_par FOREIGN KEY
(
    cree_par
) REFERENCES utilisateurs
(
    id
),
    CONSTRAINT fk_gmao_int_modifie_par FOREIGN KEY
(
    modifie_par
) REFERENCES utilisateurs
(
    id
)
    );

CREATE INDEX IF NOT EXISTS idx_gmao_interventions_equipement_id ON gmao_interventions(equipement_id);
CREATE INDEX IF NOT EXISTS idx_gmao_interventions_centre_id ON gmao_interventions(centre_id);
CREATE INDEX IF NOT EXISTS idx_gmao_interventions_statut ON gmao_interventions(statut);
CREATE INDEX IF NOT EXISTS idx_gmao_interventions_date_debut ON gmao_interventions(date_debut);
CREATE INDEX IF NOT EXISTS idx_gmao_interventions_technicien_id ON gmao_interventions(technicien_id);

-- Table des tâches d'intervention
CREATE TABLE IF NOT EXISTS gmao_taches_intervention
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    intervention_id
    UUID
    NOT
    NULL,
    description
    TEXT
    NOT
    NULL,
    statut
    VARCHAR
(
    50
) NOT NULL DEFAULT 'A_FAIRE',
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_completion TIMESTAMP,
    CONSTRAINT fk_gmao_tache_intervention FOREIGN KEY
(
    intervention_id
) REFERENCES gmao_interventions
(
    id
) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_gmao_taches_intervention_intervention_id ON gmao_taches_intervention(intervention_id);
CREATE INDEX IF NOT EXISTS idx_gmao_taches_statut ON gmao_taches_intervention(statut);

-- Table des plans de maintenance
CREATE TABLE IF NOT EXISTS gmao_plans_maintenance
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    equipement_id
    UUID
    NOT
    NULL,
    centre_id
    UUID
    NOT
    NULL,
    designation
    VARCHAR
(
    255
) NOT NULL,
    description TEXT,
    frequence VARCHAR
(
    50
) NOT NULL,
    statut VARCHAR
(
    50
) NOT NULL DEFAULT 'ACTIF',
    prochaine_date_prevue TIMESTAMP NOT NULL,
    derniere_date_execution TIMESTAMP,
    nombre_executions INT NOT NULL DEFAULT 0,
    taches_a_effectuer TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    cree_par UUID NOT NULL,
    modifie_par UUID,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_gmao_plan_equipement FOREIGN KEY
(
    equipement_id
) REFERENCES gmao_equipements
(
    id
),
    CONSTRAINT fk_gmao_plan_centre FOREIGN KEY
(
    centre_id
) REFERENCES centres
(
    id
),
    CONSTRAINT fk_gmao_plan_cree_par FOREIGN KEY
(
    cree_par
) REFERENCES utilisateurs
(
    id
),
    CONSTRAINT fk_gmao_plan_modifie_par FOREIGN KEY
(
    modifie_par
) REFERENCES utilisateurs
(
    id
)
    );

CREATE INDEX IF NOT EXISTS idx_gmao_plans_equipement_id ON gmao_plans_maintenance(equipement_id);
CREATE INDEX IF NOT EXISTS idx_gmao_plans_centre_id ON gmao_plans_maintenance(centre_id);
CREATE INDEX IF NOT EXISTS idx_gmao_plans_statut ON gmao_plans_maintenance(statut);
CREATE INDEX IF NOT EXISTS idx_gmao_plans_prochaine_date ON gmao_plans_maintenance(prochaine_date_prevue);

-- Table des historiques de maintenance (audit trail)
CREATE TABLE IF NOT EXISTS gmao_historique_maintenance
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    equipement_id
    UUID
    NOT
    NULL,
    centre_id
    UUID
    NOT
    NULL,
    type_action
    VARCHAR
(
    100
) NOT NULL,
    description TEXT,
    ancien_statut VARCHAR
(
    50
),
    nouveau_statut VARCHAR
(
    50
),
    utilisateur_id UUID NOT NULL,
    date_action TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gmao_histo_equipement FOREIGN KEY
(
    equipement_id
) REFERENCES gmao_equipements
(
    id
),
    CONSTRAINT fk_gmao_histo_centre FOREIGN KEY
(
    centre_id
) REFERENCES centres
(
    id
),
    CONSTRAINT fk_gmao_histo_utilisateur FOREIGN KEY
(
    utilisateur_id
) REFERENCES utilisateurs
(
    id
)
    );

CREATE INDEX IF NOT EXISTS idx_gmao_historique_equipement_id ON gmao_historique_maintenance(equipement_id);
CREATE INDEX IF NOT EXISTS idx_gmao_historique_centre_id ON gmao_historique_maintenance(centre_id);
CREATE INDEX IF NOT EXISTS idx_gmao_historique_date_action ON gmao_historique_maintenance(date_action);

-- Table des alertes maintenance
CREATE TABLE IF NOT EXISTS gmao_alertes_maintenance
(
    id
    UUID
    PRIMARY
    KEY
    NOT
    NULL,
    equipement_id
    UUID
    NOT
    NULL,
    centre_id
    UUID
    NOT
    NULL,
    type_alerte
    VARCHAR
(
    50
) NOT NULL,
    titre VARCHAR
(
    255
) NOT NULL,
    description TEXT,
    statut VARCHAR
(
    50
) NOT NULL DEFAULT 'ACTIVE',
    date_alerte TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_resolution TIMESTAMP,
    utilisateur_id UUID NOT NULL,
    CONSTRAINT fk_gmao_alerte_equipement FOREIGN KEY
(
    equipement_id
) REFERENCES gmao_equipements
(
    id
),
    CONSTRAINT fk_gmao_alerte_centre FOREIGN KEY
(
    centre_id
) REFERENCES centres
(
    id
),
    CONSTRAINT fk_gmao_alerte_utilisateur FOREIGN KEY
(
    utilisateur_id
) REFERENCES utilisateurs
(
    id
)
    );

CREATE INDEX IF NOT EXISTS idx_gmao_alertes_equipement_id ON gmao_alertes_maintenance(equipement_id);
CREATE INDEX IF NOT EXISTS idx_gmao_alertes_centre_id ON gmao_alertes_maintenance(centre_id);
CREATE INDEX IF NOT EXISTS idx_gmao_alertes_statut ON gmao_alertes_maintenance(statut);
CREATE INDEX IF NOT EXISTS idx_gmao_alertes_date ON gmao_alertes_maintenance(date_alerte);

