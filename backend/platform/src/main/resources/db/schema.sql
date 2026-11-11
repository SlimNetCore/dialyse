-- Schema for referential tables not managed by JPA entities
-- These are created manually because they don't have @Entity classes

-- Table des patients (S6c, ADR 0133) : l'entité JPA PatientJpaEntity qui la faisait créer par Hibernate est retirée ; mêmes colonnes,
-- mêmes largeurs (VARCHAR(255), TEXT = VARCHAR(1000000000) en H2), mêmes nullités, aucune valeur par défaut, comme le DDL généré.
-- H2 seulement : en production la table vient de Flyway.
CREATE TABLE IF NOT EXISTS patients (
    id                      UUID PRIMARY KEY,
    adresse                 VARCHAR(255),
    assure_adresse          VARCHAR(255),
    assure_date_naissance   DATE,
    assure_groupe_sanguin   VARCHAR(255),
    assure_history_json     TEXT,
    assure_nom              VARCHAR(255),
    assure_numero_assurance VARCHAR(255),
    assure_prenom           VARCHAR(255),
    assure_sexe             VARCHAR(255),
    assure_tel_bureau       VARCHAR(255),
    assure_tel_mobile       VARCHAR(255),
    assure_tel_personnel    VARCHAR(255),
    categorie_transport_id  UUID,
    center_id               UUID         NOT NULL,
    centre_payeur_id        UUID,
    civilite                VARCHAR(255),
    code_patient            VARCHAR(255),
    created_at              TIMESTAMP(6) WITH TIME ZONE,
    date_admission          DATE         NOT NULL,
    date_evenement_etat     DATE,
    date_naissance          DATE,
    email                   VARCHAR(255),
    en_sommeil              BOOLEAN,
    epo_date                DATE,
    epo_enabled             BOOLEAN,
    etat_patient            VARCHAR(255),
    fer_date                DATE,
    fer_enabled             BOOLEAN,
    generateur_id           UUID,
    groupe_sanguin          VARCHAR(255),
    jour_dimanche           BOOLEAN,
    jour_jeudi              BOOLEAN,
    jour_lundi              BOOLEAN,
    jour_mardi              BOOLEAN,
    jour_mercredi           BOOLEAN,
    jour_samedi             BOOLEAN,
    jour_vendredi           BOOLEAN,
    lieu_naissance          VARCHAR(255),
    medecin_traitant_id     UUID,
    nom                     VARCHAR(255) NOT NULL,
    nombre_enfants          INTEGER,
    numero_assurance        VARCHAR(255) NOT NULL,
    observation             TEXT,
    photo_base64            TEXT,
    pieces_jointes_json     TEXT,
    position_id             UUID,
    prenom                  VARCHAR(255) NOT NULL,
    profession1             VARCHAR(255),
    qualite_assure          VARCHAR(255),
    salle_id                UUID,
    sexe                    VARCHAR(255) NOT NULL,
    situation_familiale     VARCHAR(255),
    sous_kt                 BOOLEAN,
    tel_bureau              VARCHAR(255),
    tel_mobile              VARCHAR(255),
    tel_personnel           VARCHAR(255),
    transporteur_aller_id   UUID,
    transporteur_retour_id  UUID,
    type_patient            VARCHAR(255) NOT NULL
);

-- PL1 (a) : les trois entités JPA des payeurs sont retirées ; ce DDL reproduit EXACTEMENT ce que Hibernate (ddl-auto: update) créait
-- (colonnes nullables et longueurs 255 : l'ancien DDL, jamais appliqué car Hibernate passait avant, disait 50 / 500 / NOT NULL).
CREATE TABLE IF NOT EXISTS caisse_assurance (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255),
    type_caisse VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS agence (
    id UUID NOT NULL PRIMARY KEY,
    caisse_id UUID,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS centre_payeur (
    id UUID NOT NULL PRIMARY KEY,
    adresse VARCHAR(255),
    agence_id UUID,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255)
);

-- PL1 (b) : tables du stock, lues et écrites en JDBC par le contexte stock. Les cinq entités JPA (Article, Lot, StockMovement, Emplacement,
-- Fournisseur) sont retirées ; ce DDL reproduit EXACTEMENT ce que Hibernate (ddl-auto: update) créait (comparé par INFORMATION_SCHEMA avant /
-- après : colonnes, types, longueurs, nullabilité, défauts, clés, index). Doit rester AVANT les ALTER de stock_movements (inventaire_id, saisi_le).
CREATE TABLE IF NOT EXISTS emplacements (
    id UUID NOT NULL PRIMARY KEY,
    actif BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    libelle VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_emplacement_center ON emplacements (center_id);

CREATE TABLE IF NOT EXISTS fournisseurs (
    id UUID NOT NULL PRIMARY KEY,
    actif BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    contact VARCHAR(255),
    email VARCHAR(255),
    raison_sociale VARCHAR(255) NOT NULL,
    telephone VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_fournisseur_center ON fournisseurs (center_id);

CREATE TABLE IF NOT EXISTS articles (
    id UUID NOT NULL PRIMARY KEY,
    active BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255) NOT NULL,
    code_barres VARCHAR(64),
    coefficient_achat NUMERIC(14, 4),
    compte_charge VARCHAR(20),
    compte_stock VARCHAR(20),
    condition_conservation VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE,
    dci VARCHAR(150),
    dechet_dasri BOOLEAN DEFAULT FALSE NOT NULL,
    dosage_par_unite NUMERIC(14, 4),
    forme_galenique VARCHAR(80),
    fournisseur_id UUID,
    gere_par_lot BOOLEAN NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    peremption_obligatoire BOOLEAN DEFAULT FALSE NOT NULL,
    pmp_courant NUMERIC(38, 2),
    prix_achat NUMERIC(14, 4),
    produit_dangereux BOOLEAN DEFAULT FALSE NOT NULL,
    reference_fabricant VARCHAR(80),
    seuil_alerte NUMERIC(38, 2) NOT NULL,
    stock_max NUMERIC(14, 4),
    stock_quantity NUMERIC(38, 2) NOT NULL,
    tva_type_id UUID,
    type_traitement_anemie VARCHAR(20),
    unite VARCHAR(255) NOT NULL,
    unite_achat VARCHAR(30),
    unite_dosage VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS lots (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_reception_id UUID,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_peremption DATE,
    emplacement_id UUID,
    numero_lot VARCHAR(255) NOT NULL,
    pmp NUMERIC(38, 2),
    quantite_initiale NUMERIC(38, 2) NOT NULL,
    quantite_restante NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lot_article_peremption ON lots (article_id, date_peremption);
CREATE INDEX IF NOT EXISTS idx_lot_article ON lots (article_id);
CREATE INDEX IF NOT EXISTS idx_lot_center ON lots (center_id);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    lot_id UUID,
    mouvement_type VARCHAR(255) NOT NULL,
    pmp_apres NUMERIC(38, 2),
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL,
    seance_id UUID
);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_article_date ON stock_movements (article_id, created_at);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_lot ON stock_movements (lot_id);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_seance ON stock_movements (seance_id);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_date ON stock_movements (center_id, created_at);

-- PL1 (c) : bons de commande, de réception et de sortie et leurs lignes (même méthode que (b) ; entités et six dépôts JPA retirés).
CREATE TABLE IF NOT EXISTS bons_commande (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    fournisseur_id UUID,
    reference VARCHAR(255) NOT NULL,
    statut VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bl_center ON bons_commande (center_id);
CREATE INDEX IF NOT EXISTS idx_bl_reference ON bons_commande (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_commande_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_commande_id UUID NOT NULL,
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bl_ligne_bon ON bons_commande_lignes (bon_commande_id);

CREATE TABLE IF NOT EXISTS bons_reception (
    id UUID NOT NULL PRIMARY KEY,
    bon_commande_id UUID,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    date_reception DATE,
    fournisseur_id UUID,
    reference VARCHAR(255) NOT NULL,
    statut VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_br_center ON bons_reception (center_id);
CREATE INDEX IF NOT EXISTS idx_br_reference ON bons_reception (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_reception_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_reception_id UUID NOT NULL,
    date_peremption DATE,
    emplacement_id UUID,
    lot_id UUID,
    numero_lot VARCHAR(255),
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_br_ligne_bon ON bons_reception_lignes (bon_reception_id);

CREATE TABLE IF NOT EXISTS bons_sortie (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    date_sortie DATE,
    patient_id UUID,
    poste VARCHAR(255),
    reference VARCHAR(255) NOT NULL,
    seance_id UUID NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bs_center ON bons_sortie (center_id);
CREATE INDEX IF NOT EXISTS idx_bs_seance ON bons_sortie (seance_id);
CREATE INDEX IF NOT EXISTS idx_bs_reference ON bons_sortie (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_sortie_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_sortie_id UUID NOT NULL,
    lot_id UUID,
    pmp_applique NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bs_ligne_bon ON bons_sortie_lignes (bon_sortie_id);
CREATE INDEX IF NOT EXISTS idx_bs_ligne_lot ON bons_sortie_lignes (lot_id);

-- PL1 (d) : compteurs de numérotation des pièces (JDBC : SequenceDesNumerosDePiecesJdbc) et affectations utilisateur / centre / rôle
-- (lues par seed.sql). Entités AppSetting, AppSettingId et UserCenterAssignment retirées.
CREATE TABLE IF NOT EXISTS app_settings (
    center_id UUID NOT NULL,
    cle VARCHAR(50) NOT NULL,
    dernier_compteur BIGINT NOT NULL,
    prefixe VARCHAR(20) NOT NULL,
    PRIMARY KEY (center_id, cle)
);

CREATE TABLE IF NOT EXISTS user_center_assignment (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    role_code VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    UNIQUE (user_id, center_id, role_code)
);

CREATE TABLE IF NOT EXISTS medecin (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255),
    specialite VARCHAR(255),
    actif BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS machine
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    code
    VARCHAR
(
    100
) NOT NULL,
    libelle VARCHAR
(
    255
) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE
    );

CREATE TABLE IF NOT EXISTS protocole
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    code
    VARCHAR
(
    100
) NOT NULL,
    libelle VARCHAR
(
    255
) NOT NULL
    );

CREATE TABLE IF NOT EXISTS salle (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR
(
    255
) NOT NULL,
    ouverte BOOLEAN NOT NULL DEFAULT TRUE
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
    numero_assurance VARCHAR(255) PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255),
    prenom VARCHAR(255),
    sexe VARCHAR(255),
    date_naissance DATE,
    tel_personnel VARCHAR(255),
    tel_mobile VARCHAR(255),
    tel_bureau VARCHAR(255),
    adresse VARCHAR(255),
    groupe_sanguin VARCHAR(255),
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

-- Prise en charge (contexts/patient-couverture, PC3) : mêmes colonnes que Flyway V1 ; en H2 la table n'est plus créée par une entité JPA.
CREATE TABLE IF NOT EXISTS prise_en_charge (
    id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_debut_demande DATE,
    date_debut_effectif DATE,
    date_fin_demande DATE,
    date_fin_effectif DATE,
    forfait_demande_id UUID,
    forfait_effectif_id UUID,
    patient_id UUID NOT NULL,
    statut VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

-- Attestation de droits (contexts/patient-couverture, PC4) : mêmes colonnes que Flyway V1 ; en H2 la table n'est plus créée par une entité JPA.
CREATE TABLE IF NOT EXISTS attestation_droit (
    id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    patient_id UUID NOT NULL,
    PRIMARY KEY (id)
);

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

-- ═══ Séance : séances et volets (contexte Séance, SE7f, ADR 0100) ; résultats d'analyses (Dossier médical) ═══
-- Jusqu'à SE7f ces quatre tables H2 étaient générées par les entités JPA de la plateforme (retirées avec bc-seance) :
-- mêmes colonnes, mêmes types et mêmes contraintes que ces entités (et que Flyway V1, V21, V23, V26, V39 pour seances).

CREATE TABLE IF NOT EXISTS seances
(
    id                              UUID PRIMARY KEY,
    patient_id                      UUID         NOT NULL,
    center_id                       UUID         NOT NULL,
    date_seance                     DATE         NOT NULL,
    statut                          VARCHAR(255) NOT NULL,
    created_at                      TIMESTAMP WITH TIME ZONE,
    validated_at                    TIMESTAMP WITH TIME ZONE,
    signed_infirmier_at             TIMESTAMP WITH TIME ZONE,
    signed_infirmier_by             VARCHAR(100),
    signed_medecin_at               TIMESTAMP WITH TIME ZONE,
    signed_medecin_by               VARCHAR(100),
    facture_id                      UUID,
    forfait_override_id             UUID,
    forfait_override_code           VARCHAR(50),
    forfait_override_nom            VARCHAR(255),
    forfait_override_prix           NUMERIC(14, 2),
    forfait_override_updated_at     TIMESTAMP WITH TIME ZONE,
    forfait_override_updated_by     VARCHAR(100),
    regularisation_deverrouillee_at TIMESTAMP WITH TIME ZONE,
    regularisation_deverrouillee_by VARCHAR(100),
    hors_planning                   BOOLEAN DEFAULT FALSE NOT NULL,
    motif_hors_planning             VARCHAR(20),
    precision_hors_planning         VARCHAR(255),
    salle_id                        UUID,
    creneau_id                      UUID,
    generateur_id                   UUID,
    statut_avant_exclusion          VARCHAR(20),
    exclusion_absence_id            UUID
);
CREATE INDEX IF NOT EXISTS idx_seances_center_date ON seances (center_id, date_seance);
CREATE INDEX IF NOT EXISTS idx_seances_center_patient_date ON seances (center_id, patient_id, date_seance);

CREATE TABLE IF NOT EXISTS volet_paramedical
(
    id                 UUID PRIMARY KEY,
    seance_id          UUID NOT NULL UNIQUE,
    center_id          UUID NOT NULL,
    poids_avant_kg     NUMERIC(38, 2),
    poids_apres_kg     NUMERIC(38, 2),
    ta_avant           VARCHAR(30),
    ta_apres           VARCHAR(30),
    duree_minutes      INTEGER,
    debit_sang_ml_min  INTEGER,
    ultrafiltration_ml NUMERIC(38, 2),
    anticoagulant      VARCHAR(100),
    type_dialysat      VARCHAR(100),
    incidents          TEXT,
    created_at         TIMESTAMP WITH TIME ZONE,
    updated_at         TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS volet_medical
(
    id                         UUID PRIMARY KEY,
    seance_id                  UUID NOT NULL UNIQUE,
    center_id                  UUID NOT NULL,
    prescription               TEXT,
    tolerance_seance           TEXT,
    examen_clinique            TEXT,
    resultats_biologiques      TEXT,
    ajustements_therapeutiques TEXT,
    conclusion_medicale        TEXT,
    created_at                 TIMESTAMP WITH TIME ZONE,
    updated_at                 TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS resultats_analyses
(
    id                  UUID PRIMARY KEY,
    patient_id          UUID NOT NULL,
    center_id           UUID NOT NULL,
    date_prelevement    DATE NOT NULL,
    hb_g_dl             NUMERIC(38, 2),
    ht_pct              NUMERIC(38, 2),
    plaquettes          INTEGER,
    ferritine_ng_ml     NUMERIC(38, 2),
    cstf_pct            NUMERIC(38, 2),
    epo_endogene_mui_ml NUMERIC(38, 2),
    uree_pre_mg_dl      NUMERIC(38, 2),
    uree_post_mg_dl     NUMERIC(38, 2),
    creatinine_mg_dl    NUMERIC(38, 2),
    kt_v_mensuel        NUMERIC(38, 2),
    phosphore_mg_dl     NUMERIC(38, 2),
    calcium_mg_dl       NUMERIC(38, 2),
    pth_pg_ml           NUMERIC(38, 2),
    albumine_g_dl       NUMERIC(38, 2),
    proteines_g_dl      NUMERIC(38, 2),
    crp_mg_l            NUMERIC(38, 2),
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_resultats_patient_date ON resultats_analyses (patient_id, date_prelevement);

-- ═══ Dossier médical : les 17 tables du contexte (DM9, ADR 0101) ═══
-- Jusqu'à DM9 ces tables H2 étaient générées par les entités JPA de la plateforme (retirées avec bc-medical) : colonnes, types et clés
-- primaires de Flyway V1 ; poids sec et sa contrainte de V2 ; index de V31 ; colonnes de détail des alertes de V53. Écart connu :
-- dossier_medical_patient porte l'unicité GLOBALE de patient_id dans V1 comme dans l'entité retirée (identiques, ADR 0087).

CREATE TABLE IF NOT EXISTS abords_vasculaires
(
    id            UUID PRIMARY KEY,
    actif         BOOLEAN     NOT NULL,
    center_id     UUID        NOT NULL,
    complications VARCHAR(500),
    cote          VARCHAR(10),
    created_at    TIMESTAMP WITH TIME ZONE,
    date_creation DATE,
    date_fin      DATE,
    localisation  VARCHAR(255),
    patient_id    UUID        NOT NULL,
    type_abord    VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS administrations_anemie
(
    id                       UUID PRIMARY KEY,
    administre_par           VARCHAR(100),
    administree              BOOLEAN                  NOT NULL,
    article_id               UUID,
    center_id                UUID                     NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    date_administration      DATE                     NOT NULL,
    dose                     NUMERIC(10, 3),
    molecule                 VARCHAR(100),
    motif_non_administration VARCHAR(500),
    patient_id               UUID                     NOT NULL,
    prescription_medicale_id UUID,
    quantite_article         NUMERIC(10, 3),
    seance_id                UUID,
    type_traitement          VARCHAR(20)              NOT NULL,
    unite_dose               VARCHAR(20),
    voie                     VARCHAR(10)
);
CREATE INDEX IF NOT EXISTS idx_admin_anemie_patient ON administrations_anemie (center_id, patient_id, date_administration);

CREATE TABLE IF NOT EXISTS alertes_observance
(
    id                 UUID PRIMARY KEY,
    center_id          UUID                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    doses_administrees INTEGER                  NOT NULL,
    doses_attendues    INTEGER                  NOT NULL,
    message            VARCHAR(500),
    patient_id         UUID                     NOT NULL,
    periode_debut      DATE                     NOT NULL,
    periode_fin        DATE                     NOT NULL,
    resolved_at        TIMESTAMP WITH TIME ZONE,
    type_alerte        VARCHAR(20)              NOT NULL,
    type_traitement    VARCHAR(20)              NOT NULL,
    unite_dose         VARCHAR(10),
    dose_prescrite     INTEGER,
    frequence_valeur   INTEGER,
    frequence_unite    VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS allergies_patient
(
    id                     UUID PRIMARY KEY,
    categorie              VARCHAR(20)              NOT NULL,
    center_id              UUID                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    criticite              VARCHAR(10)              NOT NULL,
    date_constatation      DATE                     NOT NULL,
    manifestations         TEXT,
    patient_id             UUID                     NOT NULL,
    statut_verification    VARCHAR(15)              NOT NULL,
    substance_code         VARCHAR(20)              NOT NULL,
    substance_code_display VARCHAR(255),
    substance_code_system  VARCHAR(10),
    type_reaction          VARCHAR(15)              NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS antecedents_medicaux
(
    id                      UUID PRIMARY KEY,
    center_id               UUID                     NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    date_debut              DATE                     NOT NULL,
    date_fin                DATE,
    diagnostic_code         VARCHAR(20),
    diagnostic_code_display VARCHAR(255),
    diagnostic_code_system  VARCHAR(10),
    libelle_libre           VARCHAR(255),
    note                    TEXT,
    patient_id              UUID                     NOT NULL,
    severite                VARCHAR(50),
    statut_clinique         VARCHAR(20)              NOT NULL,
    type_antecedent         VARCHAR(20)              NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS bilans_pre_greffe
(
    id                             UUID PRIMARY KEY,
    center_id                      UUID                     NOT NULL,
    conclusion_nephrologue         TEXT,
    contre_indications             TEXT,
    created_at                     TIMESTAMP WITH TIME ZONE NOT NULL,
    date_debut_bilan               DATE,
    date_greffe                    DATE,
    date_inscription_liste_attente DATE,
    groupe_sanguin_confirme        VARCHAR(5),
    patient_id                     UUID                     NOT NULL,
    pra_classe_i                   NUMERIC(5, 2),
    pra_classe_ii                  NUMERIC(5, 2),
    statut                         VARCHAR(30)              NOT NULL,
    typage_hla                     VARCHAR(500),
    updated_at                     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS decisions_rcp
(
    id                   UUID PRIMARY KEY,
    avis                 VARCHAR(15) NOT NULL,
    bilan_id             UUID        NOT NULL,
    compte_rendu         TEXT,
    date_reunion         DATE        NOT NULL,
    prochaine_date_revue DATE
);

CREATE TABLE IF NOT EXISTS demandes_examen
(
    id              UUID PRIMARY KEY,
    categorie       VARCHAR(15)              NOT NULL,
    center_id       UUID                     NOT NULL,
    conclusion      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    date_demande    DATE                     NOT NULL,
    motif           VARCHAR(500),
    patient_id      UUID                     NOT NULL,
    prescripteur_id VARCHAR(100),
    statut          VARCHAR(20)              NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    urgent          BOOLEAN                  NOT NULL
);

CREATE TABLE IF NOT EXISTS donneurs_vivants
(
    id                  UUID PRIMARY KEY,
    bilan_realise       TEXT,
    center_id           UUID                     NOT NULL,
    contre_indications  TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    crossmatch_resultat VARCHAR(15)              NOT NULL,
    date_crossmatch     DATE,
    date_decision       DATE,
    date_naissance      DATE,
    decision_finale     TEXT,
    groupe_sanguin      VARCHAR(5),
    lien_parente        VARCHAR(20)              NOT NULL,
    nom                 VARCHAR(100)             NOT NULL,
    patient_id          UUID                     NOT NULL,
    prenom              VARCHAR(100),
    statut_bilan        VARCHAR(20)              NOT NULL,
    telephone           VARCHAR(30),
    typage_hla          VARCHAR(500),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS dossier_medical_patient
(
    id                    UUID PRIMARY KEY,
    center_id             UUID NOT NULL,
    conclusion_medicale   TEXT,
    created_at            TIMESTAMP WITH TIME ZONE,
    date_mise_en_dialyse  DATE,
    hepatite_b_statut     VARCHAR(20),
    hepatite_c_statut     VARCHAR(20),
    nephropathie_initiale VARCHAR(255),
    observation_globale   TEXT,
    patient_id            UUID NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk5obbasyg8v5vdwb90d6lotno2 UNIQUE (patient_id)
);

CREATE TABLE IF NOT EXISTS etapes_bilan_pre_greffe
(
    id                UUID PRIMARY KEY,
    categorie         VARCHAR(20)              NOT NULL,
    center_id         UUID                     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    date_expiration   DATE,
    date_realisation  DATE,
    demande_examen_id UUID,
    libelle           VARCHAR(255)             NOT NULL,
    patient_id        UUID                     NOT NULL,
    resultat          TEXT,
    serologie_id      UUID,
    statut            VARCHAR(20)              NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS lignes_demande_examen
(
    id                   UUID PRIMARY KEY,
    analyte_code         VARCHAR(20),
    analyte_code_display VARCHAR(255),
    analyte_code_system  VARCHAR(10),
    commentaire          VARCHAR(500),
    demande_id           UUID NOT NULL,
    libelle              VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS lignes_ordonnance
(
    id                      UUID PRIMARY KEY,
    duree_jours             INTEGER,
    instructions            VARCHAR(500),
    libelle                 VARCHAR(255),
    medicament_code         VARCHAR(20),
    medicament_code_display VARCHAR(255),
    medicament_code_system  VARCHAR(10),
    ordonnance_id           UUID         NOT NULL,
    posologie               VARCHAR(255) NOT NULL,
    quantite                INTEGER,
    voie                    VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS observations_biologiques
(
    id                   UUID PRIMARY KEY,
    analyte_code         VARCHAR(20)              NOT NULL,
    analyte_code_display VARCHAR(255),
    analyte_code_system  VARCHAR(10)              NOT NULL,
    center_id            UUID                     NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prelevement     DATE                     NOT NULL,
    demande_examen_id    UUID,
    patient_id           UUID                     NOT NULL,
    source               VARCHAR(20)              NOT NULL,
    statut               VARCHAR(15)              NOT NULL,
    unite                VARCHAR(20),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    valeur_num           NUMERIC(14, 4),
    valeur_texte         VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_obs_bio_patient_date ON observations_biologiques (center_id, patient_id, date_prelevement);

CREATE TABLE IF NOT EXISTS ordonnances
(
    id                UUID PRIMARY KEY,
    center_id         UUID                     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prescription DATE                     NOT NULL,
    medecin_id        VARCHAR(100),
    numero            VARCHAR(30),
    patient_id        UUID                     NOT NULL,
    signed_at         TIMESTAMP WITH TIME ZONE,
    statut            VARCHAR(20)              NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS prescriptions_medicales
(
    id                      UUID PRIMARY KEY,
    anticoag_type_prescrit  VARCHAR(20),
    center_id               UUID NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE,
    date_prescription       DATE NOT NULL,
    duree_cible_min         INTEGER,
    epo_article_id          UUID,
    epo_dose_ui             INTEGER,
    epo_frequence_unite     VARCHAR(10),
    epo_frequence_valeur    INTEGER,
    epo_voie                VARCHAR(10),
    fer_article_id          UUID,
    fer_dose_mg             INTEGER,
    fer_frequence_unite     VARCHAR(10),
    fer_frequence_valeur    INTEGER,
    fer_voie                VARCHAR(10),
    medecin_id              UUID,
    patient_id              UUID NOT NULL,
    qb_cible                INTEGER,
    qd_cible                INTEGER,
    type_dialyseur_prescrit VARCHAR(100),
    uf_max_ml               INTEGER,
    updated_at              TIMESTAMP WITH TIME ZONE,
    poids_sec_cible_kg      NUMERIC(5, 2),
    CONSTRAINT ck_prescription_poids_sec_cible CHECK (poids_sec_cible_kg IS NULL OR poids_sec_cible_kg BETWEEN 20 AND 300)
);
CREATE INDEX IF NOT EXISTS idx_prescriptions_patient ON prescriptions_medicales (center_id, patient_id, date_prescription);

CREATE TABLE IF NOT EXISTS serologies_patient
(
    id                     UUID PRIMARY KEY,
    center_id              UUID                     NOT NULL,
    conduite_a_tenir       TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prelevement       DATE                     NOT NULL,
    date_prochain_controle DATE,
    laboratoire            VARCHAR(255),
    marqueur               VARCHAR(15)              NOT NULL,
    patient_id             UUID                     NOT NULL,
    resultat               VARCHAR(15)              NOT NULL,
    titre                  NUMERIC(10, 3),
    unite                  VARCHAR(20),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

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
-- Mémoire de l'exclusion d'une séance de la facturation (migration V39, ADR 0031)
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS statut_avant_exclusion VARCHAR (20);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS exclusion_absence_id UUID;

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
-- S6b-3 (ADR 0134, KB-S6a-2) : état d'un assuré rapproché avant la reprise, restauré à l'annulation du lot (ajout, nullable).
ALTER TABLE IF EXISTS migration_id_map ADD COLUMN IF NOT EXISTS etat_origine TEXT;

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
-- Heure réelle de saisie (KB-STK-01, miroir de la migration V38)
ALTER TABLE IF EXISTS stock_movements ADD COLUMN IF NOT EXISTS saisi_le TIMESTAMP WITH TIME ZONE;


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
-- Compte qui a saisi l'absence (V47) : nul pour les absences antérieures
ALTER TABLE IF EXISTS infirmier_absence ADD COLUMN IF NOT EXISTS declaree_par UUID;

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

-- Paramétrage du planning d'un centre (contexte Planification, slice P1). Auparavant créée par Hibernate (entité retirée) ; en
-- production le schéma vient de Flyway (V10, V11, V16).
CREATE TABLE IF NOT EXISTS planning_parametres (
    center_id UUID NOT NULL PRIMARY KEY,
    jours_ouverts VARCHAR(100) NOT NULL,
    salles_isolement TEXT,
    patients_par_infirmier INTEGER,
    patients_par_poste_serie INTEGER,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

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
-- Reprise des calculs orphelins instance par instance (V48, revue P8 B1) : jeton d'instance et battement de coeur.
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS instance_id VARCHAR (64);
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS battement_le TIMESTAMP;

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

-- Journal durable des alertes d'un centre : une alerte envoyée en temps réel y est aussi enregistrée pour être retrouvée
-- à la connexion. target_roles : rôles ciblés entre virgules (« ,ADMIN,SECRETAIRE, »), vide = tout le monde.
CREATE TABLE IF NOT EXISTS notification_evenement
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    60
) NOT NULL,
    payload TEXT NOT NULL,
    target_roles VARCHAR
(
    255
) NOT NULL,
    cree_le TIMESTAMP NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_notification_evenement_centre ON notification_evenement (center_id, cree_le);
-- Alerte personnelle : compte destinataire (V49, revue P8 M2).
ALTER TABLE IF EXISTS notification_evenement ADD COLUMN IF NOT EXISTS target_user_id VARCHAR (64);

-- Lecture d'une alerte par un utilisateur.
CREATE TABLE IF NOT EXISTS notification_lecture
(
    notification_id
    UUID
    NOT
    NULL,
    user_id
    VARCHAR
(
    100
) NOT NULL,
    lue_le TIMESTAMP NOT NULL,
    PRIMARY KEY
(
    notification_id,
    user_id
)
    );

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

-- Paramétrage comptable sans développement (voir migration V33) : plan comptable du centre, compte client de chaque
-- payeur, modèles de pièces et leurs lignes.
CREATE TABLE IF NOT EXISTS comptes_comptables
(
    id
    UUID
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    numero
    VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    150
) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_compte_comptable_centre_numero UNIQUE
(
    center_id,
    numero
)
    );

CREATE TABLE IF NOT EXISTS comptes_payeurs
(
    center_id
    UUID
    NOT
    NULL,
    payeur_id
    UUID
    NOT
    NULL,
    compte
    VARCHAR
(
    20
) NOT NULL,
    CONSTRAINT pk_comptes_payeurs PRIMARY KEY
(
    center_id,
    payeur_id
)
    );

CREATE TABLE IF NOT EXISTS modeles_piece
(
    id
    UUID
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    code
    VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    100
) NOT NULL,
    journal_code VARCHAR
(
    10
) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_modele_piece_centre_code UNIQUE
(
    center_id,
    code
)
    );

CREATE TABLE IF NOT EXISTS modeles_piece_lignes
(
    modele_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    position_ligne
    INTEGER
    NOT
    NULL,
    sens
    VARCHAR
(
    6
) NOT NULL,
    compte VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    100
),
    CONSTRAINT pk_modeles_piece_lignes PRIMARY KEY
(
    modele_id,
    position_ligne
)
    );
CREATE INDEX IF NOT EXISTS idx_modeles_piece_lignes_compte ON modeles_piece_lignes (center_id, compte);

-- Auteur de l'émission d'une facture (migration V40, slice F3, KB-FAC-30)
ALTER TABLE IF EXISTS factures
    ADD COLUMN IF NOT EXISTS emise_par UUID;

-- Mode, référence de paiement et clé d'idempotence d'un règlement (migration V41, slice F4, KB-FAC-39/40)
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS mode_reglement VARCHAR(20);
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS reference_paiement VARCHAR(120);
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS cle_idempotence VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_facture_reglements_cle_idempotence
    ON facture_reglements (center_id, facture_id, cle_idempotence);

-- Registre de publication d'evenements Spring Modulith (outbox, ADR 0013, ADR 0036), cree ici pour H2 : le DDL officiel de
-- Modulith limite serialized_event a VARCHAR(4000), trop court pour un evenement d'integration. En production, c'est V35 (TEXT).
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(1000000)         NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Clôture mensuelle de facturation (migration V42, slice F7, ADR 0037)
CREATE TABLE IF NOT EXISTS periodes_facturation
(
    center_id         UUID                     NOT NULL,
    annee             INTEGER                  NOT NULL,
    mois              INTEGER                  NOT NULL,
    statut            VARCHAR(10)              NOT NULL DEFAULT 'OUVERT',
    cloturee_par      UUID,
    cloturee_le       TIMESTAMP WITH TIME ZONE,
    rouverte_par      UUID,
    rouverte_le       TIMESTAMP WITH TIME ZONE,
    motif_reouverture VARCHAR(500),
    PRIMARY KEY (center_id, annee, mois),
    CONSTRAINT ck_periodes_facturation_mois CHECK (mois BETWEEN 1 AND 12),
    CONSTRAINT ck_periodes_facturation_statut CHECK (statut IN ('OUVERT', 'CLOTURE'))
);

CREATE TABLE IF NOT EXISTS periodes_facturation_journal
(
    id                            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    center_id                     UUID                     NOT NULL,
    annee                         INTEGER                  NOT NULL,
    mois                          INTEGER                  NOT NULL,
    type_acte                     VARCHAR(12)              NOT NULL,
    effectue_par                  UUID                     NOT NULL,
    effectue_le                   TIMESTAMP WITH TIME ZONE NOT NULL,
    motif                         VARCHAR(500),
    seances_eligibles_restantes   INTEGER,
    CONSTRAINT ck_periodes_facturation_journal_type CHECK (type_acte IN ('CLOTURE', 'REOUVERTURE'))
);

CREATE INDEX IF NOT EXISTS idx_periodes_facturation_journal_mois
    ON periodes_facturation_journal (center_id, annee, mois, id);

-- Clôture mensuelle des absences de patients (migration V44, slice A6, ADR 0042)
CREATE TABLE IF NOT EXISTS mois_absences
(
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    statut
    VARCHAR
(
    10
) NOT NULL DEFAULT 'OUVERT',
    cloture_par UUID,
    cloture_le TIMESTAMP WITH TIME ZONE,
    rouvert_par UUID,
    rouvert_le TIMESTAMP WITH TIME ZONE,
                             motif_reouverture VARCHAR (500),
    PRIMARY KEY
(
    center_id,
    annee,
    mois
),
    CONSTRAINT ck_mois_absences_mois CHECK
(
    mois
    BETWEEN
    1
    AND
    12
),
    CONSTRAINT ck_mois_absences_statut CHECK
(
    statut
    IN
(
    'OUVERT',
    'CLOTURE'
))
    );

CREATE TABLE IF NOT EXISTS mois_absences_journal
(
    id
    BIGINT
    GENERATED
    ALWAYS AS
    IDENTITY
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    type_acte
    VARCHAR
(
    12
) NOT NULL,
    effectue_par UUID NOT NULL,
    effectue_le TIMESTAMP WITH TIME ZONE NOT NULL,
                              motif VARCHAR (500),
    a_qualifier_restantes BIGINT,
    en_retard BIGINT,
    CONSTRAINT ck_mois_absences_journal_type CHECK
(
    type_acte
    IN
(
    'CLOTURE',
    'REOUVERTURE'
))
    );

CREATE INDEX IF NOT EXISTS idx_mois_absences_journal_mois
    ON mois_absences_journal (center_id, annee, mois, id);

-- Groupes d'articles (migration V9 ; tables lues et écrites en JDBC par le contexte stock depuis la slice S2 : l'entité
-- JPA qui les créait en H2 est retirée)
CREATE TABLE IF NOT EXISTS groupes_articles
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    nom
    VARCHAR
(
    100
) NOT NULL,
    nom_cle VARCHAR
(
    100
) NOT NULL,
    description VARCHAR
(
    500
),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             CONSTRAINT uk_groupes_articles_centre_nom UNIQUE (center_id, nom_cle)
    );

CREATE TABLE IF NOT EXISTS groupes_articles_items
(
    groupe_id
    UUID
    NOT
    NULL
    REFERENCES
    groupes_articles
(
    id
) ON DELETE CASCADE,
    article_id UUID NOT NULL,
    PRIMARY KEY
(
    groupe_id,
    article_id
)
    );

CREATE INDEX IF NOT EXISTS idx_groupes_articles_centre ON groupes_articles (center_id);

-- Sortie pour seances (migration V45, slice S6b, ADR 0045)
CREATE TABLE IF NOT EXISTS stock_parametres
(
    center_id
    UUID
    NOT
    NULL,
    sortie_pour_seances_activee
    BOOLEAN
    NOT
    NULL
    DEFAULT
    FALSE,
    PRIMARY
    KEY
(
    center_id
)
    );
CREATE TABLE IF NOT EXISTS sorties_pour_seances
(
    id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    reference
    VARCHAR
(
    255
) NOT NULL,
    jour DATE NOT NULL,
    salle VARCHAR
(
    255
),
    demi_journee VARCHAR
(
    20
),
    statut VARCHAR
(
    20
) NOT NULL,
    enregistree_le TIMESTAMP WITH TIME ZONE NOT NULL,
                                 enregistree_par VARCHAR (255),
    validee_le TIMESTAMP
                             WITH TIME ZONE,
                                 validee_par VARCHAR (255),
    PRIMARY KEY
(
    id
),
    CONSTRAINT ck_sorties_pour_seances_statut CHECK
(
    statut
    IN
(
    'ENREGISTREE',
    'RAPPROCHEE'
)),
    CONSTRAINT ck_sorties_pour_seances_demi_journee CHECK
(
    demi_journee
    IS
    NULL
    OR
    demi_journee
    IN
(
    'MATIN',
    'APRES_MIDI'
))
    );
CREATE INDEX IF NOT EXISTS idx_sorties_pour_seances_centre ON sorties_pour_seances (center_id, jour);
CREATE TABLE IF NOT EXISTS sorties_pour_seances_lignes
(
    id
    UUID
    NOT
    NULL,
    sortie_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    lot_id
    UUID,
    quantite_sortie
    NUMERIC
(
    38,
    2
) NOT NULL,
    valeur_unitaire NUMERIC
(
    38,
    2
) NOT NULL,
    quantite_consommee NUMERIC
(
    38,
    2
),
    ecart NUMERIC
(
    38,
    2
),
    motif VARCHAR
(
    500
),
    PRIMARY KEY
(
    id
)
    );
CREATE INDEX IF NOT EXISTS idx_sorties_pour_seances_lignes_sortie ON sorties_pour_seances_lignes (sortie_id);
CREATE TABLE IF NOT EXISTS consommations_de_seance
(
    seance_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    patient_id
    UUID,
    sortie_id
    UUID,
    mise_a_jour
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL,
    PRIMARY
    KEY
(
    seance_id
)
    );
CREATE INDEX IF NOT EXISTS idx_consommations_de_seance_sortie ON consommations_de_seance (sortie_id);
CREATE TABLE IF NOT EXISTS consommations_de_seance_imputations
(
    id
    UUID
    NOT
    NULL,
    seance_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    lot_id
    UUID,
    quantite
    NUMERIC
(
    38,
    2
) NOT NULL,
    alerte VARCHAR
(
    40
),
    PRIMARY KEY
(
    id
)
    );
CREATE INDEX IF NOT EXISTS idx_consommations_imputations_seance ON consommations_de_seance_imputations (seance_id);
