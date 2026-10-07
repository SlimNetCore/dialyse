-- Schema for referential tables not managed by JPA entities
-- These are created manually because they don't have @Entity classes

CREATE TABLE IF NOT EXISTS caisse_assurance (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    type_caisse VARCHAR(50) NOT NULL DEFAULT 'STANDARD'
);

CREATE TABLE IF NOT EXISTS agence (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    caisse_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS centre_payeur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    agence_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    adresse VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS medecin (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255),
    specialite VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS salle (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL
);

-- Les générateurs de dialyse sont l'agrégat GMAO Equipement (type GENERATEUR_DIALYSE, table gmao_equipements, gérée
-- par Hibernate ddl-auto). L'ancienne table plate "generateur" n'existe plus (supprimée par la migration V19).

CREATE TABLE IF NOT EXISTS position_creneau (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS transporteur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    telephone VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS categorie_transport (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS forfait (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    prix DECIMAL(10,2)
);

CREATE TABLE IF NOT EXISTS assure (
    numero_assurance VARCHAR(100) PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255),
    prenom VARCHAR(255),
    sexe VARCHAR(10),
    date_naissance DATE,
    tel_personnel VARCHAR(50),
    tel_mobile VARCHAR(50),
    tel_bureau VARCHAR(50),
    adresse VARCHAR(500),
    groupe_sanguin VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS assure_patient (
                                              id UUID NOT NULL DEFAULT RANDOM_UUID(),
    patient_id UUID NOT NULL,
    numero_assurance VARCHAR(100) NOT NULL,
    center_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    date_debut_affectation DATE,
    date_fin_affectation   DATE,
                                              date_affectation TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (id)
);
-- Index de recherche pour les affectations actives (la contrainte d'unicité est gérée applicativement)
CREATE INDEX IF NOT EXISTS idx_assure_patient_primary_active
    ON assure_patient (patient_id, center_id, is_primary, date_fin_affectation);

-- ═══ User & Role Management ═══

CREATE TABLE IF NOT EXISTS app_role (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    full_name VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS app_user_center (
    user_id UUID NOT NULL,
    center_id UUID NOT NULL,
    PRIMARY KEY (user_id, center_id)
);

CREATE TABLE IF NOT EXISTS auth_refresh_token
(
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL,
    center_id UUID,
    societe_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id ON auth_refresh_token (user_id);

-- Comptes « direction » : un utilisateur portant le rôle DIRECTION est rattaché à une société (sans centre).
CREATE TABLE IF NOT EXISTS app_user_societe
(
    user_id
    UUID
    NOT
    NULL,
    societe_id
    UUID
    NOT
    NULL,
    PRIMARY
    KEY
(
    user_id,
    societe_id
)
    );

-- Double authentification TOTP (facultative, par utilisateur). Le secret est chiffré (AES-GCM) ; les codes de
-- secours sont stockés hachés (BCrypt) et à usage unique.
CREATE TABLE IF NOT EXISTS app_user_mfa
(
    user_id
    UUID
    PRIMARY
    KEY,
    secret_cipher
    VARCHAR
(
    255
) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_step BIGINT NOT NULL DEFAULT 0,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                             );

CREATE TABLE IF NOT EXISTS app_user_mfa_recovery
(
    id
    UUID
    PRIMARY
    KEY,
    user_id
    UUID
    NOT
    NULL,
    code_hash
    VARCHAR
(
    100
) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
    );
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_user ON app_user_mfa_recovery (user_id);

-- Instantanés mensuels des tableaux de bord de la direction (immuables une fois créés).
CREATE TABLE IF NOT EXISTS direction_snapshot
(
    id
    UUID
    PRIMARY
    KEY,
    societe_id
    UUID
    NOT
    NULL,
    mois
    VARCHAR
(
    7
) NOT NULL,
    payload TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT uq_direction_snapshot UNIQUE (societe_id, mois)
    );

-- Historique des alertes du tableau de bord de la direction (apparition / résolution).
CREATE TABLE IF NOT EXISTS direction_alert_history
(
    id
    UUID
    PRIMARY
    KEY,
    societe_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    centre_nom
    VARCHAR
(
    200
) NOT NULL,
    code VARCHAR
(
    60
) NOT NULL,
    severity VARCHAR
(
    20
) NOT NULL,
    valeur NUMERIC
(
    14,
    2
),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
                              );
CREATE INDEX IF NOT EXISTS idx_direction_alert_history_societe
    ON direction_alert_history (societe_id, resolved_at, first_seen_at DESC);

-- Journal d'audit (traçabilité « qui a fait quoi ») : écrit par lot, jamais sur le thread de la requête.
CREATE TABLE IF NOT EXISTS audit_log
(
    id
    UUID
    PRIMARY
    KEY,
    occurred_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL,
    user_id
    UUID,
    username
    VARCHAR
(
    100
),
    roles VARCHAR
(
    200
),
    center_id UUID,
    societe_id UUID,
    action_code VARCHAR
(
    80
) NOT NULL,
    entity_type VARCHAR
(
    80
),
    entity_id VARCHAR
(
    100
),
    libelle VARCHAR
(
    500
),
    http_method VARCHAR
(
    10
),
    route_template VARCHAR
(
    300
),
    status_code INT,
    duration_ms BIGINT,
    ip_address VARCHAR
(
    64
)
    );
CREATE INDEX IF NOT EXISTS idx_audit_log_center ON audit_log (center_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_societe ON audit_log (societe_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log (user_id, occurred_at DESC);

-- ═══ Calendrier centre (jours fériés / fermetures) ═══

CREATE TABLE IF NOT EXISTS center_holiday
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    label     VARCHAR(255),
    UNIQUE (center_id, day_date)
);

CREATE TABLE IF NOT EXISTS center_closure_day
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    reason    VARCHAR(255),
    UNIQUE (center_id, day_date)
);

-- ═══ Modèles de documents (Jasper) ═══

CREATE TABLE IF NOT EXISTS modele_document (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    type_document VARCHAR(50) NOT NULL,
    chemin_jrxml VARCHAR(500) NOT NULL,
    format_impression VARCHAR(20) NOT NULL DEFAULT 'PDF',
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(center_id, code)
);

-- type_document : FICHE_PATIENT, ATTESTATION, PEC, FICHE_SIGNALETIQUE, CUSTOM
-- format_impression : PDF, EXCEL, HTML

-- ═══ Facturation ═══

-- Types TVA versionnés dans le temps (partagés entre facturation et comptabilité)
CREATE TABLE IF NOT EXISTS tva_types
(
    id                  UUID PRIMARY KEY,
    center_id           UUID                     NOT NULL,
    libelle             VARCHAR(255)             NOT NULL,
    taux                DECIMAL(5, 2)            NOT NULL,
    type_prestation     VARCHAR(120)             NOT NULL DEFAULT 'HEMODIALYSE',
    exonere             BOOLEAN                  NOT NULL DEFAULT FALSE,
    date_debut_validite DATE                     NOT NULL,
    date_fin_validite   DATE,
    texte_reference     VARCHAR(500),
    actif               BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_tva_types_center_prestation ON tva_types (center_id, type_prestation, date_debut_validite);
CREATE INDEX IF NOT EXISTS idx_tva_types_center_actif ON tva_types (center_id, actif);

-- Consommables proposés en un toucher à l'infirmier (poste infirmier), par centre et dans l'ordre choisi.
CREATE TABLE IF NOT EXISTS seance_raccourcis_articles
(
    center_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    ordre
    INTEGER
    NOT
    NULL,
    PRIMARY
    KEY
(
    center_id,
    article_id
)
    );

CREATE TABLE IF NOT EXISTS facturation_settings (
    center_id UUID PRIMARY KEY,
    tva_rate DECIMAL(5, 2) DEFAULT 0.00, -- Décommissionné : TVA gérée via tva_types (TypeTVA)
    code_format VARCHAR(120) NOT NULL,
    regroupement_multi_forfait BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS facture_sequence (
    center_id UUID NOT NULL,
    seq_year INTEGER NOT NULL,
    seq_value INTEGER NOT NULL,
    PRIMARY KEY (center_id, seq_year)
);

CREATE TABLE IF NOT EXISTS factures (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    numero_facture VARCHAR(120) NOT NULL,
    patient_code VARCHAR(80),
    patient_full_name VARCHAR(255),
    patient_status_snapshot VARCHAR(120),
    numero_immatriculation_snapshot VARCHAR(120),
    centre_payeur_id_snapshot UUID,
    agence_id_snapshot UUID,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    date_facturation DATE NOT NULL,
    tva_rate DECIMAL(5,2) NOT NULL,
    total_ht DECIMAL(14,2) NOT NULL,
    total_tva DECIMAL(14,2) NOT NULL,
    total_ttc DECIMAL(14,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (center_id, numero_facture)
);

CREATE INDEX IF NOT EXISTS idx_factures_center_period ON factures (center_id, date_facturation);
CREATE INDEX IF NOT EXISTS idx_factures_center_patient ON factures (center_id, patient_id);

CREATE TABLE IF NOT EXISTS facture_lignes (
    id UUID PRIMARY KEY,
    facture_id UUID NOT NULL,
    center_id UUID NOT NULL,
    forfait_id UUID,
    forfait_label VARCHAR(255) NOT NULL,
    unit_price_ht DECIMAL(14,2) NOT NULL,
    seance_count INTEGER NOT NULL,
    line_ht DECIMAL(14,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_facture_lignes_facture ON facture_lignes (facture_id, center_id);

CREATE TABLE IF NOT EXISTS facture_reglements
(
    id             UUID PRIMARY KEY,
    facture_id     UUID                     NOT NULL,
    center_id      UUID                     NOT NULL,
    montant        DECIMAL(14, 2)           NOT NULL,
    date_reglement DATE                     NOT NULL,
    saisi_par      VARCHAR(120),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_facture_reglements_facture ON facture_reglements (facture_id, center_id);
CREATE INDEX IF NOT EXISTS idx_facture_reglements_period ON facture_reglements (center_id, date_reglement);

ALTER TABLE IF EXISTS seances ADD COLUMN IF NOT EXISTS facture_id UUID;
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS code_reglement VARCHAR(30);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_id UUID;
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_code VARCHAR(50);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_nom VARCHAR(255);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_prix DECIMAL(14, 2);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_updated_by VARCHAR(100);

-- ••• Reprise des données d'un système existant (voir aussi migration/V3__reprise_donnees.sql) •••

CREATE TABLE IF NOT EXISTS migration_batch
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    libelle
    VARCHAR
(
    200
) NOT NULL,
    source_system VARCHAR
(
    100
),
    date_debut_reprise DATE,
    status VARCHAR
(
    20
) NOT NULL,
    created_by VARCHAR
(
    100
),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
                            );
CREATE INDEX IF NOT EXISTS idx_migration_batch_center ON migration_batch (center_id, status);

CREATE TABLE IF NOT EXISTS migration_entity_run
(
    id
    UUID
    PRIMARY
    KEY,
    batch_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    entity
    VARCHAR
(
    40
) NOT NULL,
    file_name VARCHAR
(
    255
),
    dry_run BOOLEAN NOT NULL,
    applied BOOLEAN NOT NULL,
    total_rows INTEGER NOT NULL,
    created_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    report_json TEXT,
    executed_by VARCHAR
(
    100
),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
                              );
CREATE INDEX IF NOT EXISTS idx_migration_run_batch ON migration_entity_run (batch_id, entity, executed_at);

CREATE TABLE IF NOT EXISTS migration_id_map
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    batch_id
    UUID
    NOT
    NULL,
    entity
    VARCHAR
(
    40
) NOT NULL,
    legacy_id VARCHAR
(
    150
) NOT NULL,
    target_id VARCHAR
(
    150
) NOT NULL,
    operation VARCHAR
(
    10
) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             CONSTRAINT uk_migration_id_map UNIQUE (center_id, entity, legacy_id)
    );
CREATE INDEX IF NOT EXISTS idx_migration_id_map_batch ON migration_id_map (batch_id);

CREATE TABLE IF NOT EXISTS migration_value_map
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    column_key
    VARCHAR
(
    60
) NOT NULL,
    source_value VARCHAR
(
    150
) NOT NULL,
    target_value VARCHAR
(
    150
) NOT NULL,
    CONSTRAINT uk_migration_value_map UNIQUE
(
    center_id,
    column_key,
    source_value
)
    );

-- Inventaire de stock (voir aussi migration/V4__inventaire_stock.sql)
CREATE TABLE IF NOT EXISTS inventaires
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    reference
    character
    varying
(
    40
) NOT NULL,
    date_inventaire date NOT NULL,
    statut character varying
(
    20
) NOT NULL,
    commentaire character varying
(
    1000
),
    created_by character varying
(
    100
),
    created_at timestamp with time zone NOT NULL,
                             closed_by character varying (100),
    closed_at timestamp
                         with time zone,
                             CONSTRAINT uk_inventaires_reference UNIQUE (center_id, reference)
    );
CREATE INDEX IF NOT EXISTS idx_inventaires_center_statut ON inventaires (center_id, statut, date_inventaire);

CREATE TABLE IF NOT EXISTS inventaire_lignes
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    inventaire_id
    uuid
    NOT
    NULL,
    center_id
    uuid
    NOT
    NULL,
    position_ligne
    integer
    NOT
    NULL,
    article_id
    uuid
    NOT
    NULL,
    article_code
    character
    varying
(
    100
),
    article_libelle character varying
(
    255
),
    unite character varying
(
    50
),
    lot_id uuid,
    numero_lot character varying
(
    100
),
    date_peremption date,
    quantite_theorique numeric
(
    14,
    3
) NOT NULL,
    quantite_comptee numeric
(
    14,
    3
),
    pmp numeric
(
    14,
    4
),
    motif_ecart character varying
(
    255
),
    compte_par character varying
(
    100
),
    compte_le timestamp with time zone,
                            ajoutee boolean NOT NULL DEFAULT FALSE
                            );
CREATE INDEX IF NOT EXISTS idx_inventaire_lignes_inventaire ON inventaire_lignes (inventaire_id, position_ligne);

ALTER TABLE IF EXISTS stock_movements ADD COLUMN IF NOT EXISTS inventaire_id UUID;
CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_inventaire ON stock_movements (center_id, inventaire_id, created_at);


-- ═══ Personnel soignant : infirmiers, roulement, absences et remplacements ═══

CREATE TABLE IF NOT EXISTS infirmier
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    matricule
    VARCHAR
(
    50
) NOT NULL,
    nom VARCHAR
(
    255
) NOT NULL,
    prenom VARCHAR
(
    255
),
    telephone VARCHAR
(
    50
),
    qualification VARCHAR
(
    20
) NOT NULL,
    habilite_isolement BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE
(
    center_id,
    matricule
)
    );

CREATE TABLE IF NOT EXISTS infirmier_affectation
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    infirmier_id
    UUID
    NOT
    NULL,
    salle_id
    UUID
    NOT
    NULL,
    creneau_id
    UUID
    NOT
    NULL,
    jours
    VARCHAR
(
    100
) NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_infirmier_affectation_infirmier ON infirmier_affectation (center_id, infirmier_id);

CREATE TABLE IF NOT EXISTS infirmier_absence
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    infirmier_id
    UUID
    NOT
    NULL,
    date_debut
    DATE
    NOT
    NULL,
    date_fin
    DATE
    NOT
    NULL,
    type
    VARCHAR
(
    20
) NOT NULL,
    motif VARCHAR
(
    255
)
    );
CREATE INDEX IF NOT EXISTS idx_infirmier_absence_periode ON infirmier_absence (center_id, date_debut, date_fin);

CREATE TABLE IF NOT EXISTS infirmier_remplacement
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    date_jour
    DATE
    NOT
    NULL,
    salle_id
    UUID
    NOT
    NULL,
    creneau_id
    UUID
    NOT
    NULL,
    infirmier_id
    UUID
    NOT
    NULL,
    remplace_infirmier_id
    UUID,
    UNIQUE
(
    center_id,
    date_jour,
    creneau_id,
    infirmier_id
)
    );

-- Lien facultatif d'un infirmier avec un compte utilisateur (un compte ne sert qu'une fiche par centre)
ALTER TABLE IF EXISTS infirmier ADD COLUMN IF NOT EXISTS user_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ux_infirmier_user ON infirmier (center_id, user_id);

-- Absences des patients : une par patient et par jour, valorisée au forfait de la prise en charge (TTC -> HT)
CREATE TABLE IF NOT EXISTS absence_patient
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    patient_id
    UUID
    NOT
    NULL,
    date_seance
    DATE
    NOT
    NULL,
    source
    VARCHAR
(
    20
) NOT NULL,
    statut VARCHAR
(
    20
) NOT NULL,
    motif VARCHAR
(
    30
),
    commentaire VARCHAR
(
    1000
),
    forfait_id UUID,
    forfait_libelle VARCHAR
(
    255
),
    prix_ttc NUMERIC
(
    14,
    2
) NOT NULL DEFAULT 0,
    taux_tva NUMERIC
(
    6,
    2
) NOT NULL DEFAULT 0,
    montant_ht NUMERIC
(
    14,
    2
) NOT NULL DEFAULT 0,
    declaree_par UUID,
    declaree_le TIMESTAMP WITH TIME ZONE,
    qualifiee_par UUID,
    qualifiee_le TIMESTAMP WITH TIME ZONE,
    date_rattrapage DATE,
    modifiee_par UUID,
    modifiee_le TIMESTAMP WITH TIME ZONE,
                              UNIQUE (center_id, patient_id, date_seance)
    );
CREATE INDEX IF NOT EXISTS idx_absence_patient_periode ON absence_patient (center_id, date_seance);
CREATE INDEX IF NOT EXISTS idx_absence_patient_statut ON absence_patient (center_id, statut);

-- Salle d'isolement : propriété de la salle (créée avec elle). 'OUI' = réservée aux patients à risque infectieux.
ALTER TABLE IF EXISTS salle ADD COLUMN IF NOT EXISTS isolement VARCHAR (3) NOT NULL DEFAULT 'NON';
-- Reprise des salles d'isolement de l'ancien paramétrage du planning (liste d'identifiants), une seule fois
UPDATE salle
SET isolement = 'OUI'
WHERE isolement = 'NON'
  AND EXISTS (SELECT 1
              FROM planning_parametres p
              WHERE p.center_id = salle.center_id
                AND p.salles_isolement LIKE '%' || CAST(salle.id AS VARCHAR(36)) || '%');
UPDATE planning_parametres
SET salles_isolement = NULL
WHERE salles_isolement IS NOT NULL;

-- Capacité d une salle : nombre maximal de générateurs affectés (NULL = illimitée)
ALTER TABLE IF EXISTS salle ADD COLUMN IF NOT EXISTS capacite INTEGER;

-- Historique des mouvements de patients (append-only) : admission, séjour temporaire, sorties, libération de la place
CREATE TABLE IF NOT EXISTS mouvement_patient
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    patient_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    30
) NOT NULL,
    date_effet DATE NOT NULL,
    etat_precedent VARCHAR
(
    30
),
    etat_nouveau VARCHAR
(
    30
),
    salle_id UUID,
    position_id UUID,
    generateur_id UUID,
    jours_dialyse VARCHAR
(
    80
),
    automatique BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_centre ON mouvement_patient (center_id, date_effet);
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_patient ON mouvement_patient (center_id, patient_id);

-- Optimisation du planning (Timefold) : historique des propositions d'un centre. parametres, resume et resultat sont
-- des documents JSON ; empreinte identifie l'état du centre lu au lancement (une proposition périmée n'est pas appliquée).
CREATE TABLE IF NOT EXISTS planification_optimisation
(
    id          UUID PRIMARY KEY,
    center_id   UUID        NOT NULL,
    statut      VARCHAR(20) NOT NULL,
    perimetre   VARCHAR(20) NOT NULL,
    parametres  TEXT        NOT NULL,
    cree_le     TIMESTAMP   NOT NULL,
    termine_le  TIMESTAMP,
    lance_par   VARCHAR(100),
    phase       VARCHAR(20),
    score       VARCHAR(255),
    empreinte   VARCHAR(64) NOT NULL,
    resume      TEXT,
    resultat    TEXT,
    erreur      TEXT,
    applique_le TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_planification_optimisation_centre ON planification_optimisation (center_id, cree_le);

-- Planning calendaire figé d'une proposition (une ligne par exécution, semaine, salle et créneau) : détail JSON des
-- sept jours et textes prêts à imprimer (entete_i / cell_i, i = 0 dimanche … 6 samedi).
CREATE TABLE IF NOT EXISTS planification_calendrier_case
(
    run_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    semaine_debut
    DATE
    NOT
    NULL,
    salle_id
    UUID
    NOT
    NULL,
    salle_nom
    VARCHAR
(
    255
) NOT NULL,
    salle_ordre INTEGER NOT NULL,
    creneau_id UUID NOT NULL,
    creneau_libelle VARCHAR
(
    255
) NOT NULL,
    creneau_ordre INTEGER NOT NULL,
    jours TEXT NOT NULL,
    entete_0 VARCHAR
(
    40
), entete_1 VARCHAR
(
    40
), entete_2 VARCHAR
(
    40
), entete_3 VARCHAR
(
    40
),
    entete_4 VARCHAR
(
    40
), entete_5 VARCHAR
(
    40
), entete_6 VARCHAR
(
    40
),
    cell_0 TEXT, cell_1 TEXT, cell_2 TEXT, cell_3 TEXT, cell_4 TEXT, cell_5 TEXT, cell_6 TEXT,
    PRIMARY KEY
(
    run_id,
    semaine_debut,
    salle_id,
    creneau_id
)
    );
CREATE INDEX IF NOT EXISTS idx_planification_calendrier_centre ON planification_calendrier_case (center_id, run_id);

-- Améliorations de l'optimisation du planning : préférences des patients, profils des infirmiers, réglages du centre
-- et déplacements temporaires de séances (générateur en maintenance un jour donné).
CREATE TABLE IF NOT EXISTS planning_preference_patient
(
    center_id          UUID    NOT NULL,
    patient_id         UUID    NOT NULL,
    creneau_prefere_id UUID,
    seances_par_semaine INTEGER,
    jours_a_choisir    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (center_id, patient_id)
);

CREATE TABLE IF NOT EXISTS infirmier_profil_planning
(
    center_id     UUID    NOT NULL,
    infirmier_id  UUID    NOT NULL,
    taux_activite INTEGER NOT NULL DEFAULT 100,
    competences   VARCHAR(100),
    PRIMARY KEY (center_id, infirmier_id)
);

CREATE TABLE IF NOT EXISTS planification_reglages
(
    center_id                UUID PRIMARY KEY,
    replanification_auto     BOOLEAN NOT NULL DEFAULT FALSE,
    heures_par_vacation      INTEGER NOT NULL DEFAULT 5,
    heures_hebdo_temps_plein INTEGER NOT NULL DEFAULT 40,
    repos_hebdo_min          INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS deplacement_temporaire
(
    id            UUID PRIMARY KEY,
    center_id     UUID      NOT NULL,
    patient_id    UUID      NOT NULL,
    date_seance   DATE      NOT NULL,
    salle_id      UUID      NOT NULL,
    creneau_id    UUID      NOT NULL,
    generateur_id UUID      NOT NULL,
    motif         VARCHAR(255),
    cree_le       TIMESTAMP NOT NULL,
    UNIQUE (center_id, patient_id, date_seance)
);
CREATE INDEX IF NOT EXISTS idx_deplacement_temporaire_date ON deplacement_temporaire (center_id, date_seance);

-- Journal des séances supprimées (voir migration V27)
CREATE TABLE IF NOT EXISTS seance_suppression
(
    id          UUID PRIMARY KEY,
    center_id   UUID                     NOT NULL,
    seance_id   UUID                     NOT NULL,
    patient_id  UUID                     NOT NULL,
    date_seance DATE                     NOT NULL,
    statut      VARCHAR(20)              NOT NULL,
    motif       VARCHAR(500)             NOT NULL,
    supprime_par VARCHAR(255)            NOT NULL,
    supprime_le TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seance_suppression_centre ON seance_suppression (center_id, supprime_le);
