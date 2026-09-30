-- ============================================================
-- V1 : schéma de référence (baseline Flyway)
-- Généré depuis la base de production (pg_dump --schema-only) le 2026-09-30.
-- Les bases existantes (sans historique Flyway) sont marquées à cette version
-- via baselineOnMigrate : seules les bases VIDES exécutent ce script.
-- NE JAMAIS MODIFIER ce fichier : toute évolution passe par V2__..., V3__...
-- ============================================================

--
-- Name: abords_vasculaires; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abords_vasculaires
(
    id            uuid                  NOT NULL,
    actif         boolean               NOT NULL,
    center_id     uuid                  NOT NULL,
    complications character varying(500),
    cote          character varying(10),
    created_at    timestamp with time zone,
    date_creation date,
    date_fin      date,
    localisation  character varying(255),
    patient_id    uuid                  NOT NULL,
    type_abord    character varying(20) NOT NULL
);

--
-- Name: administrations_anemie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.administrations_anemie
(
    id                       uuid                     NOT NULL,
    administre_par           character varying(100),
    administree              boolean                  NOT NULL,
    article_id               uuid,
    center_id                uuid                     NOT NULL,
    created_at               timestamp with time zone NOT NULL,
    date_administration      date                     NOT NULL,
    dose                     numeric(10, 3),
    molecule                 character varying(100),
    motif_non_administration character varying(500),
    patient_id               uuid                     NOT NULL,
    prescription_medicale_id uuid,
    quantite_article         numeric(10, 3),
    seance_id                uuid,
    type_traitement          character varying(20)    NOT NULL,
    unite_dose               character varying(20),
    voie                     character varying(10)
);

--
-- Name: agence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agence
(
    id        uuid NOT NULL,
    caisse_id uuid,
    center_id uuid NOT NULL,
    code      character varying(255),
    nom       character varying(255)
);

--
-- Name: alertes_observance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.alertes_observance
(
    id                 uuid                     NOT NULL,
    center_id          uuid                     NOT NULL,
    created_at         timestamp with time zone NOT NULL,
    doses_administrees integer                  NOT NULL,
    doses_attendues    integer                  NOT NULL,
    message            character varying(500),
    patient_id         uuid                     NOT NULL,
    periode_debut      date                     NOT NULL,
    periode_fin        date                     NOT NULL,
    resolved_at        timestamp with time zone,
    type_alerte        character varying(20)    NOT NULL,
    type_traitement    character varying(20)    NOT NULL
);

--
-- Name: allergies_patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.allergies_patient
(
    id                     uuid                     NOT NULL,
    categorie              character varying(20)    NOT NULL,
    center_id              uuid                     NOT NULL,
    created_at             timestamp with time zone NOT NULL,
    criticite              character varying(10)    NOT NULL,
    date_constatation      date                     NOT NULL,
    manifestations         text,
    patient_id             uuid                     NOT NULL,
    statut_verification    character varying(15)    NOT NULL,
    substance_code         character varying(20)    NOT NULL,
    substance_code_display character varying(255),
    substance_code_system  character varying(10),
    type_reaction          character varying(15)    NOT NULL,
    updated_at             timestamp with time zone NOT NULL
);

--
-- Name: antecedents_medicaux; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.antecedents_medicaux
(
    id                      uuid                     NOT NULL,
    center_id               uuid                     NOT NULL,
    created_at              timestamp with time zone NOT NULL,
    date_debut              date                     NOT NULL,
    date_fin                date,
    diagnostic_code         character varying(20),
    diagnostic_code_display character varying(255),
    diagnostic_code_system  character varying(10),
    libelle_libre           character varying(255),
    note                    text,
    patient_id              uuid                     NOT NULL,
    severite                character varying(50),
    statut_clinique         character varying(20)    NOT NULL,
    type_antecedent         character varying(20)    NOT NULL,
    updated_at              timestamp with time zone NOT NULL
);

--
-- Name: app_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_role
(
    id          uuid                   NOT NULL,
    code        character varying(255) NOT NULL,
    description character varying(255),
    name        character varying(255) NOT NULL
);

--
-- Name: app_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_settings
(
    center_id        uuid                  NOT NULL,
    cle              character varying(50) NOT NULL,
    dernier_compteur bigint                NOT NULL,
    prefixe          character varying(20) NOT NULL
);

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user
(
    id            uuid                   NOT NULL,
    active        boolean                NOT NULL,
    created_at    timestamp(6) with time zone,
    email         character varying(255),
    full_name     character varying(255),
    password_hash character varying(255) NOT NULL,
    username      character varying(255) NOT NULL
);

--
-- Name: app_user_center; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_center
(
    user_id   uuid NOT NULL,
    center_id uuid NOT NULL
);

--
-- Name: app_user_mfa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_mfa
(
    user_id         uuid                                               NOT NULL,
    secret_cipher   character varying(255)                             NOT NULL,
    enabled         boolean                  DEFAULT false             NOT NULL,
    last_step       bigint                   DEFAULT 0                 NOT NULL,
    failed_attempts integer                  DEFAULT 0                 NOT NULL,
    locked_until    timestamp with time zone,
    created_at      timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: app_user_mfa_recovery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_mfa_recovery
(
    id        uuid                   NOT NULL,
    user_id   uuid                   NOT NULL,
    code_hash character varying(100) NOT NULL,
    used      boolean DEFAULT false  NOT NULL
);

--
-- Name: app_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_role
(
    user_id uuid NOT NULL,
    role_id uuid NOT NULL
);

--
-- Name: app_user_societe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_societe
(
    user_id    uuid NOT NULL,
    societe_id uuid NOT NULL
);

--
-- Name: articles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.articles
(
    id                     uuid                   NOT NULL,
    active                 boolean                NOT NULL,
    center_id              uuid                   NOT NULL,
    code                   character varying(255) NOT NULL,
    created_at             timestamp with time zone,
    gere_par_lot           boolean                NOT NULL,
    libelle                character varying(255) NOT NULL,
    pmp_courant            numeric(38, 2),
    seuil_alerte           numeric(38, 2)         NOT NULL,
    stock_quantity         numeric(38, 2)         NOT NULL,
    type_traitement_anemie character varying(20),
    unite                  character varying(255) NOT NULL
);

--
-- Name: assure; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assure
(
    numero_assurance character varying(255) NOT NULL,
    adresse          character varying(255),
    center_id        uuid                   NOT NULL,
    created_at       timestamp(6) with time zone,
    date_naissance   date,
    groupe_sanguin   character varying(255),
    nom              character varying(255),
    prenom           character varying(255),
    sexe             character varying(255),
    tel_bureau       character varying(255),
    tel_mobile       character varying(255),
    tel_personnel    character varying(255)
);

--
-- Name: assure_patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assure_patient
(
    id                     uuid                        NOT NULL,
    center_id              uuid                        NOT NULL,
    date_affectation       timestamp(6) with time zone NOT NULL,
    date_debut_affectation date,
    date_fin_affectation   date,
    is_primary             boolean                     NOT NULL,
    numero_assurance       character varying(255)      NOT NULL,
    patient_id             uuid                        NOT NULL
);

--
-- Name: attestation_droit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attestation_droit
(
    id         uuid NOT NULL,
    center_id  uuid NOT NULL,
    created_at timestamp with time zone,
    date_debut date NOT NULL,
    date_fin   date NOT NULL,
    patient_id uuid NOT NULL
);

--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log
(
    id             uuid                     NOT NULL,
    occurred_at    timestamp with time zone NOT NULL,
    user_id        uuid,
    username       character varying(100),
    roles          character varying(200),
    center_id      uuid,
    societe_id     uuid,
    action_code    character varying(80)    NOT NULL,
    entity_type    character varying(80),
    entity_id      character varying(100),
    libelle        character varying(500),
    http_method    character varying(10),
    route_template character varying(300),
    status_code    integer,
    duration_ms    bigint,
    ip_address     character varying(64)
);

--
-- Name: auth_refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_refresh_token
(
    id         uuid                                               NOT NULL,
    token_hash character varying(128)                             NOT NULL,
    user_id    uuid                                               NOT NULL,
    center_id  uuid,
    societe_id uuid,
    expires_at timestamp with time zone                           NOT NULL,
    revoked    boolean                  DEFAULT false             NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at timestamp with time zone
);

--
-- Name: bilans_pre_greffe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bilans_pre_greffe
(
    id                             uuid                     NOT NULL,
    center_id                      uuid                     NOT NULL,
    conclusion_nephrologue         text,
    contre_indications             text,
    created_at                     timestamp with time zone NOT NULL,
    date_debut_bilan               date,
    date_greffe                    date,
    date_inscription_liste_attente date,
    groupe_sanguin_confirme        character varying(5),
    patient_id                     uuid                     NOT NULL,
    pra_classe_i                   numeric(5, 2),
    pra_classe_ii                  numeric(5, 2),
    statut                         character varying(30)    NOT NULL,
    typage_hla                     character varying(500),
    updated_at                     timestamp with time zone NOT NULL
);

--
-- Name: bons_commande; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_commande
(
    id             uuid                   NOT NULL,
    center_id      uuid                   NOT NULL,
    created_at     timestamp with time zone,
    created_by     character varying(255),
    fournisseur_id uuid,
    reference      character varying(255) NOT NULL,
    statut         character varying(255) NOT NULL
);

--
-- Name: bons_commande_lignes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_commande_lignes
(
    id              uuid           NOT NULL,
    article_id      uuid           NOT NULL,
    bon_commande_id uuid           NOT NULL,
    prix_unitaire   numeric(38, 2),
    quantite        numeric(38, 2) NOT NULL
);

--
-- Name: bons_reception; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_reception
(
    id              uuid                   NOT NULL,
    bon_commande_id uuid,
    center_id       uuid                   NOT NULL,
    created_at      timestamp with time zone,
    created_by      character varying(255),
    date_reception  date,
    fournisseur_id  uuid,
    reference       character varying(255) NOT NULL,
    statut          character varying(255) NOT NULL
);

--
-- Name: bons_reception_lignes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_reception_lignes
(
    id               uuid           NOT NULL,
    article_id       uuid           NOT NULL,
    bon_reception_id uuid           NOT NULL,
    date_peremption  date,
    emplacement_id   uuid,
    lot_id           uuid,
    numero_lot       character varying(255),
    prix_unitaire    numeric(38, 2),
    quantite         numeric(38, 2) NOT NULL
);

--
-- Name: bons_sortie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_sortie
(
    id          uuid                   NOT NULL,
    center_id   uuid                   NOT NULL,
    created_at  timestamp with time zone,
    created_by  character varying(255),
    date_sortie date,
    patient_id  uuid,
    poste       character varying(255),
    reference   character varying(255) NOT NULL,
    seance_id   uuid                   NOT NULL
);

--
-- Name: bons_sortie_lignes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bons_sortie_lignes
(
    id            uuid           NOT NULL,
    article_id    uuid           NOT NULL,
    bon_sortie_id uuid           NOT NULL,
    lot_id        uuid,
    pmp_applique  numeric(38, 2),
    quantite      numeric(38, 2) NOT NULL
);

--
-- Name: caisse_assurance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.caisse_assurance
(
    id          uuid NOT NULL,
    center_id   uuid NOT NULL,
    code        character varying(255),
    nom         character varying(255),
    type_caisse character varying(255)
);

--
-- Name: categorie_transport; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categorie_transport
(
    id        uuid                   NOT NULL,
    center_id uuid                   NOT NULL,
    libelle   character varying(255) NOT NULL
);

--
-- Name: center_closure_day; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.center_closure_day
(
    id        uuid NOT NULL,
    center_id uuid NOT NULL,
    day_date  date NOT NULL,
    reason    character varying(255)
);

--
-- Name: center_holiday; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.center_holiday
(
    id        uuid NOT NULL,
    center_id uuid NOT NULL,
    day_date  date NOT NULL,
    label     character varying(255)
);

--
-- Name: centers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.centers
(
    id         uuid NOT NULL,
    actif      boolean,
    adresse    character varying(250),
    code       character varying(255),
    email      character varying(150),
    name       character varying(255),
    site_web   character varying(200),
    societe_id uuid NOT NULL,
    telephone  character varying(30),
    ville      character varying(100),
    wilaya     character varying(100)
);

--
-- Name: centre_payeur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.centre_payeur
(
    id        uuid NOT NULL,
    adresse   character varying(255),
    agence_id uuid,
    center_id uuid NOT NULL,
    code      character varying(255),
    nom       character varying(255)
);

--
-- Name: decisions_rcp; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.decisions_rcp
(
    id                   uuid                  NOT NULL,
    avis                 character varying(15) NOT NULL,
    bilan_id             uuid                  NOT NULL,
    compte_rendu         text,
    date_reunion         date                  NOT NULL,
    prochaine_date_revue date
);

--
-- Name: demandes_examen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.demandes_examen
(
    id              uuid                     NOT NULL,
    categorie       character varying(15)    NOT NULL,
    center_id       uuid                     NOT NULL,
    conclusion      text,
    created_at      timestamp with time zone NOT NULL,
    date_demande    date                     NOT NULL,
    motif           character varying(500),
    patient_id      uuid                     NOT NULL,
    prescripteur_id character varying(100),
    statut          character varying(20)    NOT NULL,
    updated_at      timestamp with time zone NOT NULL,
    urgent          boolean                  NOT NULL
);

--
-- Name: direction_alert_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.direction_alert_history
(
    id            uuid                                               NOT NULL,
    societe_id    uuid                                               NOT NULL,
    center_id     uuid                                               NOT NULL,
    centre_nom    character varying(200)                             NOT NULL,
    code          character varying(60)                              NOT NULL,
    severity      character varying(20)                              NOT NULL,
    valeur        numeric(14, 2),
    first_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at   timestamp with time zone
);

--
-- Name: direction_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.direction_snapshot
(
    id           uuid                                               NOT NULL,
    societe_id   uuid                                               NOT NULL,
    mois         character varying(7)                               NOT NULL,
    payload      text                                               NOT NULL,
    generated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: donneurs_vivants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.donneurs_vivants
(
    id                  uuid                     NOT NULL,
    bilan_realise       text,
    center_id           uuid                     NOT NULL,
    contre_indications  text,
    created_at          timestamp with time zone NOT NULL,
    crossmatch_resultat character varying(15)    NOT NULL,
    date_crossmatch     date,
    date_decision       date,
    date_naissance      date,
    decision_finale     text,
    groupe_sanguin      character varying(5),
    lien_parente        character varying(20)    NOT NULL,
    nom                 character varying(100)   NOT NULL,
    patient_id          uuid                     NOT NULL,
    prenom              character varying(100),
    statut_bilan        character varying(20)    NOT NULL,
    telephone           character varying(30),
    typage_hla          character varying(500),
    updated_at          timestamp with time zone NOT NULL
);

--
-- Name: dossier_medical_patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dossier_medical_patient
(
    id                    uuid NOT NULL,
    center_id             uuid NOT NULL,
    conclusion_medicale   text,
    created_at            timestamp with time zone,
    date_mise_en_dialyse  date,
    hepatite_b_statut     character varying(20),
    hepatite_c_statut     character varying(20),
    nephropathie_initiale character varying(255),
    observation_globale   text,
    patient_id            uuid NOT NULL,
    updated_at            timestamp with time zone
);

--
-- Name: ecritures_comptables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ecritures_comptables
(
    id            uuid                        NOT NULL,
    center_id     uuid                        NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    date_ecriture date                        NOT NULL,
    date_piece    date                        NOT NULL,
    journal_code  character varying(10)       NOT NULL,
    libelle       character varying(255),
    numero_piece  character varying(30)       NOT NULL,
    source_id     uuid,
    statut        character varying(20)       NOT NULL,
    updated_at    timestamp(6) with time zone
);

--
-- Name: emplacements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.emplacements
(
    id        uuid                   NOT NULL,
    actif     boolean                NOT NULL,
    center_id uuid                   NOT NULL,
    code      character varying(255),
    libelle   character varying(255) NOT NULL
);

--
-- Name: etapes_bilan_pre_greffe; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etapes_bilan_pre_greffe
(
    id                uuid                     NOT NULL,
    categorie         character varying(20)    NOT NULL,
    center_id         uuid                     NOT NULL,
    created_at        timestamp with time zone NOT NULL,
    date_expiration   date,
    date_realisation  date,
    demande_examen_id uuid,
    libelle           character varying(255)   NOT NULL,
    patient_id        uuid                     NOT NULL,
    resultat          text,
    serologie_id      uuid,
    statut            character varying(20)    NOT NULL,
    updated_at        timestamp with time zone NOT NULL
);

--
-- Name: facturation_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facturation_settings
(
    center_id                  uuid                       NOT NULL,
    tva_rate                   numeric(5, 2) DEFAULT 0.00,
    code_format                character varying(120)     NOT NULL,
    regroupement_multi_forfait boolean       DEFAULT true NOT NULL,
    updated_at                 timestamp with time zone,
    updated_by                 character varying(120)
);

--
-- Name: facture_lignes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facture_lignes
(
    id            uuid                   NOT NULL,
    facture_id    uuid                   NOT NULL,
    center_id     uuid                   NOT NULL,
    forfait_id    uuid,
    forfait_label character varying(255) NOT NULL,
    unit_price_ht numeric(14, 2)         NOT NULL,
    seance_count  integer                NOT NULL,
    line_ht       numeric(14, 2)         NOT NULL
);

--
-- Name: facture_reglements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facture_reglements
(
    id             uuid                                               NOT NULL,
    facture_id     uuid                                               NOT NULL,
    center_id      uuid                                               NOT NULL,
    montant        numeric(14, 2)                                     NOT NULL,
    date_reglement date                                               NOT NULL,
    saisi_par      character varying(120),
    created_at     timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    code_reglement character varying(30)
);

--
-- Name: facture_sequence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facture_sequence
(
    center_id uuid    NOT NULL,
    seq_year  integer NOT NULL,
    seq_value integer NOT NULL
);

--
-- Name: factures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.factures
(
    id                              uuid                                               NOT NULL,
    center_id                       uuid                                               NOT NULL,
    patient_id                      uuid                                               NOT NULL,
    numero_facture                  character varying(120)                             NOT NULL,
    patient_code                    character varying(80),
    patient_full_name               character varying(255),
    patient_status_snapshot         character varying(120),
    numero_immatriculation_snapshot character varying(120),
    centre_payeur_id_snapshot       uuid,
    agence_id_snapshot              uuid,
    period_start                    date                                               NOT NULL,
    period_end                      date                                               NOT NULL,
    date_facturation                date                                               NOT NULL,
    tva_rate                        numeric(5, 2)                                      NOT NULL,
    total_ht                        numeric(14, 2)                                     NOT NULL,
    total_tva                       numeric(14, 2)                                     NOT NULL,
    total_ttc                       numeric(14, 2)                                     NOT NULL,
    created_at                      timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: forfait; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forfait
(
    id        uuid                   NOT NULL,
    center_id uuid                   NOT NULL,
    code      character varying(50)  NOT NULL,
    libelle   character varying(255) NOT NULL,
    prix      numeric(10, 2)
);

--
-- Name: fournisseurs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fournisseurs
(
    id             uuid                   NOT NULL,
    actif          boolean                NOT NULL,
    center_id      uuid                   NOT NULL,
    code           character varying(255),
    contact        character varying(255),
    email          character varying(255),
    raison_sociale character varying(255) NOT NULL,
    telephone      character varying(255)
);

--
-- Name: generateur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.generateur
(
    id        uuid                  NOT NULL,
    salle_id  uuid                  NOT NULL,
    center_id uuid                  NOT NULL,
    numero    character varying(50) NOT NULL,
    marque    character varying(100),
    modele    character varying(100),
    etat      character varying(30) DEFAULT 'FONCTIONNEL'::character varying NOT NULL
);

--
-- Name: license; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.license
(
    id                   uuid                        NOT NULL,
    activated_at         timestamp(6) with time zone,
    center_id            uuid                        NOT NULL,
    created_at           timestamp(6) with time zone NOT NULL,
    created_by           uuid,
    jti                  character varying(255)      NOT NULL,
    last_online_check_at timestamp(6) with time zone,
    license_key          text                        NOT NULL,
    max_users            integer                     NOT NULL,
    revoked_reason       character varying(255),
    status               character varying(255)      NOT NULL,
    type                 character varying(255)      NOT NULL,
    valid_from           timestamp(6) with time zone NOT NULL,
    valid_until          timestamp(6) with time zone NOT NULL
);

--
-- Name: lignes_demande_examen; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignes_demande_examen
(
    id                   uuid NOT NULL,
    analyte_code         character varying(20),
    analyte_code_display character varying(255),
    analyte_code_system  character varying(10),
    commentaire          character varying(500),
    demande_id           uuid NOT NULL,
    libelle              character varying(255)
);

--
-- Name: lignes_ecriture; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignes_ecriture
(
    id               uuid                  NOT NULL,
    axes_analytiques character varying(500),
    compte_scf       character varying(20) NOT NULL,
    libelle_ligne    character varying(255),
    montant_credit   numeric(14, 2),
    montant_debit    numeric(14, 2),
    tiers_id         uuid,
    ecriture_id      uuid                  NOT NULL
);

--
-- Name: lignes_ordonnance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignes_ordonnance
(
    id                      uuid                   NOT NULL,
    duree_jours             integer,
    instructions            character varying(500),
    libelle                 character varying(255),
    medicament_code         character varying(20),
    medicament_code_display character varying(255),
    medicament_code_system  character varying(10),
    ordonnance_id           uuid                   NOT NULL,
    posologie               character varying(255) NOT NULL,
    quantite                integer,
    voie                    character varying(50)
);

--
-- Name: lots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lots
(
    id                uuid                   NOT NULL,
    article_id        uuid                   NOT NULL,
    bon_reception_id  uuid,
    center_id         uuid                   NOT NULL,
    created_at        timestamp with time zone,
    date_peremption   date,
    emplacement_id    uuid,
    numero_lot        character varying(255) NOT NULL,
    pmp               numeric(38, 2),
    quantite_initiale numeric(38, 2)         NOT NULL,
    quantite_restante numeric(38, 2)         NOT NULL
);

--
-- Name: mapping_comptable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mapping_comptable
(
    id                     uuid                  NOT NULL,
    center_id              uuid                  NOT NULL,
    compte_banque          character varying(20) NOT NULL,
    compte_caisse          character varying(20) NOT NULL,
    compte_client_autre    character varying(20) NOT NULL,
    compte_client_casnos   character varying(20) NOT NULL,
    compte_client_cnas     character varying(20) NOT NULL,
    compte_client_mutuelle character varying(20) NOT NULL,
    compte_client_patient  character varying(20) NOT NULL,
    compte_tva_collectee   character varying(20),
    compte_ventes          character varying(20) NOT NULL,
    updated_at             timestamp(6) with time zone,
    updated_by             character varying(120)
);

--
-- Name: medecin; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medecin
(
    id         uuid                   NOT NULL,
    center_id  uuid                   NOT NULL,
    nom        character varying(255) NOT NULL,
    prenom     character varying(255),
    specialite character varying(255)
);

--
-- Name: modele_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modele_document
(
    id                uuid                                               NOT NULL,
    center_id         uuid                                               NOT NULL,
    code              character varying(50)                              NOT NULL,
    libelle           character varying(255)                             NOT NULL,
    type_document     character varying(50)                              NOT NULL,
    chemin_jrxml      character varying(500)                             NOT NULL,
    format_impression character varying(20)    DEFAULT 'PDF'::character varying NOT NULL,
    description       character varying(500),
    active            boolean                  DEFAULT true              NOT NULL,
    created_at        timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: modele_document_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modele_document_version
(
    id            uuid                        NOT NULL,
    actif         boolean                     NOT NULL,
    center_id     uuid                        NOT NULL,
    commentaire   character varying(500),
    contenu       text                        NOT NULL,
    modele_id     uuid                        NOT NULL,
    sha256        character varying(64)       NOT NULL,
    taille_octets integer                     NOT NULL,
    uploaded_at   timestamp(6) with time zone NOT NULL,
    uploaded_by   character varying(100)      NOT NULL,
    version       integer                     NOT NULL
);

--
-- Name: observations_biologiques; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.observations_biologiques
(
    id                   uuid                     NOT NULL,
    analyte_code         character varying(20)    NOT NULL,
    analyte_code_display character varying(255),
    analyte_code_system  character varying(10)    NOT NULL,
    center_id            uuid                     NOT NULL,
    created_at           timestamp with time zone NOT NULL,
    date_prelevement     date                     NOT NULL,
    demande_examen_id    uuid,
    patient_id           uuid                     NOT NULL,
    source               character varying(20)    NOT NULL,
    statut               character varying(15)    NOT NULL,
    unite                character varying(20),
    updated_at           timestamp with time zone NOT NULL,
    valeur_num           numeric(14, 4),
    valeur_texte         character varying(500)
);

--
-- Name: ordonnances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ordonnances
(
    id                uuid                     NOT NULL,
    center_id         uuid                     NOT NULL,
    created_at        timestamp with time zone NOT NULL,
    date_prescription date                     NOT NULL,
    medecin_id        character varying(100),
    numero            character varying(30),
    patient_id        uuid                     NOT NULL,
    signed_at         timestamp with time zone,
    statut            character varying(20)    NOT NULL,
    updated_at        timestamp with time zone NOT NULL
);

--
-- Name: patients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patients
(
    id                      uuid                   NOT NULL,
    adresse                 character varying(255),
    assure_adresse          character varying(255),
    assure_date_naissance   date,
    assure_groupe_sanguin   character varying(255),
    assure_history_json     text,
    assure_nom              character varying(255),
    assure_numero_assurance character varying(255),
    assure_prenom           character varying(255),
    assure_sexe             character varying(255),
    assure_tel_bureau       character varying(255),
    assure_tel_mobile       character varying(255),
    assure_tel_personnel    character varying(255),
    categorie_transport_id  uuid,
    center_id               uuid                   NOT NULL,
    centre_payeur_id        uuid,
    civilite                character varying(255),
    code_patient            character varying(255),
    created_at              timestamp(6) with time zone,
    date_admission          date                   NOT NULL,
    date_evenement_etat     date,
    date_naissance          date,
    email                   character varying(255),
    en_sommeil              boolean,
    epo_date                date,
    epo_enabled             boolean,
    etat_patient            character varying(255),
    fer_date                date,
    fer_enabled             boolean,
    generateur_id           uuid,
    groupe_sanguin          character varying(255),
    jour_dimanche           boolean,
    jour_jeudi              boolean,
    jour_lundi              boolean,
    jour_mardi              boolean,
    jour_mercredi           boolean,
    jour_samedi             boolean,
    jour_vendredi           boolean,
    lieu_naissance          character varying(255),
    medecin_traitant_id     uuid,
    nom                     character varying(255) NOT NULL,
    nombre_enfants          integer,
    numero_assurance        character varying(255) NOT NULL,
    observation             text,
    photo_base64            text,
    pieces_jointes_json     text,
    position_id             uuid,
    prenom                  character varying(255) NOT NULL,
    profession1             character varying(255),
    qualite_assure          character varying(255),
    salle_id                uuid,
    sexe                    character varying(255) NOT NULL,
    situation_familiale     character varying(255),
    sous_kt                 boolean,
    tel_bureau              character varying(255),
    tel_mobile              character varying(255),
    tel_personnel           character varying(255),
    transporteur_aller_id   uuid,
    transporteur_retour_id  uuid,
    type_patient            character varying(255) NOT NULL
);

--
-- Name: periodes_comptables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.periodes_comptables
(
    id          uuid    NOT NULL,
    annee       integer NOT NULL,
    center_id   uuid    NOT NULL,
    cloturee    boolean NOT NULL,
    cloturee_at timestamp(6) with time zone,
    cloturee_by character varying(120),
    mois        integer NOT NULL
);

--
-- Name: position_creneau; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.position_creneau
(
    id        uuid                   NOT NULL,
    center_id uuid                   NOT NULL,
    code      character varying(50)  NOT NULL,
    libelle   character varying(255) NOT NULL
);

--
-- Name: prescriptions_medicales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prescriptions_medicales
(
    id                      uuid NOT NULL,
    anticoag_type_prescrit  character varying(20),
    center_id               uuid NOT NULL,
    created_at              timestamp with time zone,
    date_prescription       date NOT NULL,
    duree_cible_min         integer,
    epo_article_id          uuid,
    epo_dose_ui             integer,
    epo_frequence_unite     character varying(10),
    epo_frequence_valeur    integer,
    epo_voie                character varying(10),
    fer_article_id          uuid,
    fer_dose_mg             integer,
    fer_frequence_unite     character varying(10),
    fer_frequence_valeur    integer,
    fer_voie                character varying(10),
    medecin_id              uuid,
    patient_id              uuid NOT NULL,
    qb_cible                integer,
    qd_cible                integer,
    type_dialyseur_prescrit character varying(100),
    uf_max_ml               integer,
    updated_at              timestamp with time zone
);

--
-- Name: prise_en_charge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prise_en_charge
(
    id                  uuid                   NOT NULL,
    center_id           uuid                   NOT NULL,
    created_at          timestamp with time zone,
    date_debut_demande  date,
    date_debut_effectif date,
    date_fin_demande    date,
    date_fin_effectif   date,
    forfait_demande_id  uuid,
    forfait_effectif_id uuid,
    patient_id          uuid                   NOT NULL,
    statut              character varying(255) NOT NULL
);

--
-- Name: resultats_analyses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resultats_analyses
(
    id                  uuid NOT NULL,
    albumine_g_dl       numeric(38, 2),
    calcium_mg_dl       numeric(38, 2),
    center_id           uuid NOT NULL,
    created_at          timestamp with time zone,
    creatinine_mg_dl    numeric(38, 2),
    crp_mg_l            numeric(38, 2),
    cstf_pct            numeric(38, 2),
    date_prelevement    date NOT NULL,
    epo_endogene_mui_ml numeric(38, 2),
    ferritine_ng_ml     numeric(38, 2),
    hb_g_dl             numeric(38, 2),
    ht_pct              numeric(38, 2),
    kt_v_mensuel        numeric(38, 2),
    patient_id          uuid NOT NULL,
    phosphore_mg_dl     numeric(38, 2),
    plaquettes          integer,
    proteines_g_dl      numeric(38, 2),
    pth_pg_ml           numeric(38, 2),
    updated_at          timestamp with time zone,
    uree_post_mg_dl     numeric(38, 2),
    uree_pre_mg_dl      numeric(38, 2)
);

--
-- Name: salle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salle
(
    id        uuid                   NOT NULL,
    center_id uuid                   NOT NULL,
    code      character varying(50)  NOT NULL,
    nom       character varying(255) NOT NULL
);

--
-- Name: seances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seances
(
    id                          uuid                   NOT NULL,
    center_id                   uuid                   NOT NULL,
    created_at                  timestamp with time zone,
    date_seance                 date                   NOT NULL,
    facture_id                  uuid,
    forfait_override_code       character varying(50),
    forfait_override_id         uuid,
    forfait_override_nom        character varying(255),
    forfait_override_prix       numeric(14, 2),
    forfait_override_updated_at timestamp with time zone,
    forfait_override_updated_by character varying(100),
    patient_id                  uuid                   NOT NULL,
    signed_infirmier_at         timestamp with time zone,
    signed_infirmier_by         character varying(100),
    signed_medecin_at           timestamp with time zone,
    signed_medecin_by           character varying(100),
    statut                      character varying(255) NOT NULL,
    validated_at                timestamp with time zone
);

--
-- Name: serologies_patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.serologies_patient
(
    id                     uuid                     NOT NULL,
    center_id              uuid                     NOT NULL,
    conduite_a_tenir       text,
    created_at             timestamp with time zone NOT NULL,
    date_prelevement       date                     NOT NULL,
    date_prochain_controle date,
    laboratoire            character varying(255),
    marqueur               character varying(15)    NOT NULL,
    patient_id             uuid                     NOT NULL,
    resultat               character varying(15)    NOT NULL,
    titre                  numeric(10, 3),
    unite                  character varying(20),
    updated_at             timestamp with time zone NOT NULL
);

--
-- Name: societes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.societes
(
    id                uuid                        NOT NULL,
    actif             boolean                     NOT NULL,
    adresse           character varying(250),
    code              character varying(30)       NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    email             character varying(150),
    logo              bytea,
    logo_content_type character varying(30),
    nif               character varying(40),
    nis               character varying(40),
    pied_page         character varying(500),
    raison_sociale    character varying(200)      NOT NULL,
    rc                character varying(40),
    site_web          character varying(200),
    telephone         character varying(30),
    ville             character varying(100),
    wilaya            character varying(100)
);

--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_movements
(
    id             uuid                   NOT NULL,
    article_id     uuid                   NOT NULL,
    center_id      uuid                   NOT NULL,
    created_at     timestamp with time zone,
    created_by     character varying(255),
    lot_id         uuid,
    mouvement_type character varying(255) NOT NULL,
    pmp_apres      numeric(38, 2),
    prix_unitaire  numeric(38, 2),
    quantite       numeric(38, 2)         NOT NULL,
    seance_id      uuid
);

--
-- Name: transporteur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transporteur
(
    id        uuid                   NOT NULL,
    center_id uuid                   NOT NULL,
    nom       character varying(255) NOT NULL,
    telephone character varying(50)
);

--
-- Name: tva_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tva_types
(
    id                  uuid                                               NOT NULL,
    center_id           uuid                                               NOT NULL,
    libelle             character varying(255)                             NOT NULL,
    taux                numeric(5, 2)                                      NOT NULL,
    type_prestation     character varying(120)   DEFAULT 'HEMODIALYSE'::character varying NOT NULL,
    exonere             boolean                  DEFAULT false             NOT NULL,
    date_debut_validite date                                               NOT NULL,
    date_fin_validite   date,
    texte_reference     character varying(500),
    actif               boolean                  DEFAULT true              NOT NULL,
    created_at          timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          character varying(120)
);

--
-- Name: user_center_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_center_assignment
(
    id         uuid                   NOT NULL,
    center_id  uuid                   NOT NULL,
    created_at timestamp(6) with time zone,
    role_code  character varying(255) NOT NULL,
    user_id    character varying(255) NOT NULL
);

--
-- Name: volet_medical; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.volet_medical
(
    id                         uuid NOT NULL,
    ajustements_therapeutiques text,
    center_id                  uuid NOT NULL,
    conclusion_medicale        text,
    created_at                 timestamp with time zone,
    examen_clinique            text,
    prescription               text,
    resultats_biologiques      text,
    seance_id                  uuid NOT NULL,
    tolerance_seance           text,
    updated_at                 timestamp with time zone
);

--
-- Name: volet_paramedical; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.volet_paramedical
(
    id                 uuid NOT NULL,
    anticoagulant      character varying(100),
    center_id          uuid NOT NULL,
    created_at         timestamp with time zone,
    debit_sang_ml_min  integer,
    duree_minutes      integer,
    incidents          text,
    poids_apres_kg     numeric(38, 2),
    poids_avant_kg     numeric(38, 2),
    seance_id          uuid NOT NULL,
    ta_apres           character varying(30),
    ta_avant           character varying(30),
    type_dialysat      character varying(100),
    ultrafiltration_ml numeric(38, 2),
    updated_at         timestamp with time zone
);

--
-- Name: abords_vasculaires abords_vasculaires_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abords_vasculaires
    ADD CONSTRAINT abords_vasculaires_pkey PRIMARY KEY (id);

--
-- Name: administrations_anemie administrations_anemie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.administrations_anemie
    ADD CONSTRAINT administrations_anemie_pkey PRIMARY KEY (id);

--
-- Name: agence agence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agence
    ADD CONSTRAINT agence_pkey PRIMARY KEY (id);

--
-- Name: alertes_observance alertes_observance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.alertes_observance
    ADD CONSTRAINT alertes_observance_pkey PRIMARY KEY (id);

--
-- Name: allergies_patient allergies_patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allergies_patient
    ADD CONSTRAINT allergies_patient_pkey PRIMARY KEY (id);

--
-- Name: antecedents_medicaux antecedents_medicaux_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.antecedents_medicaux
    ADD CONSTRAINT antecedents_medicaux_pkey PRIMARY KEY (id);

--
-- Name: app_role app_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_role
    ADD CONSTRAINT app_role_pkey PRIMARY KEY (id);

--
-- Name: app_settings app_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT app_settings_pkey PRIMARY KEY (center_id, cle);

--
-- Name: app_user_center app_user_center_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_center
    ADD CONSTRAINT app_user_center_pkey PRIMARY KEY (user_id, center_id);

--
-- Name: app_user_mfa app_user_mfa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_mfa
    ADD CONSTRAINT app_user_mfa_pkey PRIMARY KEY (user_id);

--
-- Name: app_user_mfa_recovery app_user_mfa_recovery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_mfa_recovery
    ADD CONSTRAINT app_user_mfa_recovery_pkey PRIMARY KEY (id);

--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);

--
-- Name: app_user_role app_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_role
    ADD CONSTRAINT app_user_role_pkey PRIMARY KEY (user_id, role_id);

--
-- Name: app_user_societe app_user_societe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_societe
    ADD CONSTRAINT app_user_societe_pkey PRIMARY KEY (user_id, societe_id);

--
-- Name: articles articles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_pkey PRIMARY KEY (id);

--
-- Name: assure_patient assure_patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assure_patient
    ADD CONSTRAINT assure_patient_pkey PRIMARY KEY (id);

--
-- Name: assure assure_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assure
    ADD CONSTRAINT assure_pkey PRIMARY KEY (numero_assurance);

--
-- Name: attestation_droit attestation_droit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attestation_droit
    ADD CONSTRAINT attestation_droit_pkey PRIMARY KEY (id);

--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);

--
-- Name: auth_refresh_token auth_refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_refresh_token
    ADD CONSTRAINT auth_refresh_token_pkey PRIMARY KEY (id);

--
-- Name: auth_refresh_token auth_refresh_token_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_refresh_token
    ADD CONSTRAINT auth_refresh_token_token_hash_key UNIQUE (token_hash);

--
-- Name: bilans_pre_greffe bilans_pre_greffe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bilans_pre_greffe
    ADD CONSTRAINT bilans_pre_greffe_pkey PRIMARY KEY (id);

--
-- Name: bons_commande_lignes bons_commande_lignes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_commande_lignes
    ADD CONSTRAINT bons_commande_lignes_pkey PRIMARY KEY (id);

--
-- Name: bons_commande bons_commande_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_commande
    ADD CONSTRAINT bons_commande_pkey PRIMARY KEY (id);

--
-- Name: bons_reception_lignes bons_reception_lignes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_reception_lignes
    ADD CONSTRAINT bons_reception_lignes_pkey PRIMARY KEY (id);

--
-- Name: bons_reception bons_reception_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_reception
    ADD CONSTRAINT bons_reception_pkey PRIMARY KEY (id);

--
-- Name: bons_sortie_lignes bons_sortie_lignes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_sortie_lignes
    ADD CONSTRAINT bons_sortie_lignes_pkey PRIMARY KEY (id);

--
-- Name: bons_sortie bons_sortie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bons_sortie
    ADD CONSTRAINT bons_sortie_pkey PRIMARY KEY (id);

--
-- Name: caisse_assurance caisse_assurance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.caisse_assurance
    ADD CONSTRAINT caisse_assurance_pkey PRIMARY KEY (id);

--
-- Name: categorie_transport categorie_transport_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie_transport
    ADD CONSTRAINT categorie_transport_pkey PRIMARY KEY (id);

--
-- Name: center_closure_day center_closure_day_center_id_day_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.center_closure_day
    ADD CONSTRAINT center_closure_day_center_id_day_date_key UNIQUE (center_id, day_date);

--
-- Name: center_closure_day center_closure_day_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.center_closure_day
    ADD CONSTRAINT center_closure_day_pkey PRIMARY KEY (id);

--
-- Name: center_holiday center_holiday_center_id_day_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.center_holiday
    ADD CONSTRAINT center_holiday_center_id_day_date_key UNIQUE (center_id, day_date);

--
-- Name: center_holiday center_holiday_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.center_holiday
    ADD CONSTRAINT center_holiday_pkey PRIMARY KEY (id);

--
-- Name: centers centers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.centers
    ADD CONSTRAINT centers_pkey PRIMARY KEY (id);

--
-- Name: centre_payeur centre_payeur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.centre_payeur
    ADD CONSTRAINT centre_payeur_pkey PRIMARY KEY (id);

--
-- Name: decisions_rcp decisions_rcp_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decisions_rcp
    ADD CONSTRAINT decisions_rcp_pkey PRIMARY KEY (id);

--
-- Name: demandes_examen demandes_examen_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demandes_examen
    ADD CONSTRAINT demandes_examen_pkey PRIMARY KEY (id);

--
-- Name: direction_alert_history direction_alert_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direction_alert_history
    ADD CONSTRAINT direction_alert_history_pkey PRIMARY KEY (id);

--
-- Name: direction_snapshot direction_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direction_snapshot
    ADD CONSTRAINT direction_snapshot_pkey PRIMARY KEY (id);

--
-- Name: donneurs_vivants donneurs_vivants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donneurs_vivants
    ADD CONSTRAINT donneurs_vivants_pkey PRIMARY KEY (id);

--
-- Name: dossier_medical_patient dossier_medical_patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_medical_patient
    ADD CONSTRAINT dossier_medical_patient_pkey PRIMARY KEY (id);

--
-- Name: ecritures_comptables ecritures_comptables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ecritures_comptables
    ADD CONSTRAINT ecritures_comptables_pkey PRIMARY KEY (id);

--
-- Name: emplacements emplacements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.emplacements
    ADD CONSTRAINT emplacements_pkey PRIMARY KEY (id);

--
-- Name: etapes_bilan_pre_greffe etapes_bilan_pre_greffe_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etapes_bilan_pre_greffe
    ADD CONSTRAINT etapes_bilan_pre_greffe_pkey PRIMARY KEY (id);

--
-- Name: facturation_settings facturation_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturation_settings
    ADD CONSTRAINT facturation_settings_pkey PRIMARY KEY (center_id);

--
-- Name: facture_lignes facture_lignes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture_lignes
    ADD CONSTRAINT facture_lignes_pkey PRIMARY KEY (id);

--
-- Name: facture_reglements facture_reglements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture_reglements
    ADD CONSTRAINT facture_reglements_pkey PRIMARY KEY (id);

--
-- Name: facture_sequence facture_sequence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture_sequence
    ADD CONSTRAINT facture_sequence_pkey PRIMARY KEY (center_id, seq_year);

--
-- Name: factures factures_center_id_numero_facture_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factures
    ADD CONSTRAINT factures_center_id_numero_facture_key UNIQUE (center_id, numero_facture);

--
-- Name: factures factures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.factures
    ADD CONSTRAINT factures_pkey PRIMARY KEY (id);

--
-- Name: forfait forfait_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forfait
    ADD CONSTRAINT forfait_pkey PRIMARY KEY (id);

--
-- Name: fournisseurs fournisseurs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fournisseurs
    ADD CONSTRAINT fournisseurs_pkey PRIMARY KEY (id);

--
-- Name: generateur generateur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generateur
    ADD CONSTRAINT generateur_pkey PRIMARY KEY (id);

--
-- Name: license license_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.license
    ADD CONSTRAINT license_pkey PRIMARY KEY (id);

--
-- Name: lignes_demande_examen lignes_demande_examen_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_demande_examen
    ADD CONSTRAINT lignes_demande_examen_pkey PRIMARY KEY (id);

--
-- Name: lignes_ecriture lignes_ecriture_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_ecriture
    ADD CONSTRAINT lignes_ecriture_pkey PRIMARY KEY (id);

--
-- Name: lignes_ordonnance lignes_ordonnance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_ordonnance
    ADD CONSTRAINT lignes_ordonnance_pkey PRIMARY KEY (id);

--
-- Name: lots lots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lots
    ADD CONSTRAINT lots_pkey PRIMARY KEY (id);

--
-- Name: mapping_comptable mapping_comptable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mapping_comptable
    ADD CONSTRAINT mapping_comptable_pkey PRIMARY KEY (id);

--
-- Name: medecin medecin_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medecin
    ADD CONSTRAINT medecin_pkey PRIMARY KEY (id);

--
-- Name: modele_document modele_document_center_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modele_document
    ADD CONSTRAINT modele_document_center_id_code_key UNIQUE (center_id, code);

--
-- Name: modele_document modele_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modele_document
    ADD CONSTRAINT modele_document_pkey PRIMARY KEY (id);

--
-- Name: modele_document_version modele_document_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modele_document_version
    ADD CONSTRAINT modele_document_version_pkey PRIMARY KEY (id);

--
-- Name: observations_biologiques observations_biologiques_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observations_biologiques
    ADD CONSTRAINT observations_biologiques_pkey PRIMARY KEY (id);

--
-- Name: ordonnances ordonnances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ordonnances
    ADD CONSTRAINT ordonnances_pkey PRIMARY KEY (id);

--
-- Name: patients patients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_pkey PRIMARY KEY (id);

--
-- Name: periodes_comptables periodes_comptables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodes_comptables
    ADD CONSTRAINT periodes_comptables_pkey PRIMARY KEY (id);

--
-- Name: position_creneau position_creneau_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.position_creneau
    ADD CONSTRAINT position_creneau_pkey PRIMARY KEY (id);

--
-- Name: prescriptions_medicales prescriptions_medicales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescriptions_medicales
    ADD CONSTRAINT prescriptions_medicales_pkey PRIMARY KEY (id);

--
-- Name: prise_en_charge prise_en_charge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prise_en_charge
    ADD CONSTRAINT prise_en_charge_pkey PRIMARY KEY (id);

--
-- Name: resultats_analyses resultats_analyses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resultats_analyses
    ADD CONSTRAINT resultats_analyses_pkey PRIMARY KEY (id);

--
-- Name: salle salle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salle
    ADD CONSTRAINT salle_pkey PRIMARY KEY (id);

--
-- Name: seances seances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seances
    ADD CONSTRAINT seances_pkey PRIMARY KEY (id);

--
-- Name: serologies_patient serologies_patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.serologies_patient
    ADD CONSTRAINT serologies_patient_pkey PRIMARY KEY (id);

--
-- Name: societes societes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.societes
    ADD CONSTRAINT societes_pkey PRIMARY KEY (id);

--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);

--
-- Name: transporteur transporteur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transporteur
    ADD CONSTRAINT transporteur_pkey PRIMARY KEY (id);

--
-- Name: tva_types tva_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tva_types
    ADD CONSTRAINT tva_types_pkey PRIMARY KEY (id);

--
-- Name: user_center_assignment uk10sv9i2qtxwdf50k23b14vwty; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_center_assignment
    ADD CONSTRAINT uk10sv9i2qtxwdf50k23b14vwty UNIQUE (user_id, center_id, role_code);

--
-- Name: dossier_medical_patient uk5obbasyg8v5vdwb90d6lotno2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_medical_patient
    ADD CONSTRAINT uk5obbasyg8v5vdwb90d6lotno2 UNIQUE (patient_id);

--
-- Name: license uk5pg5gxali83qfvegsy7fiwlbf; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.license
    ADD CONSTRAINT uk5pg5gxali83qfvegsy7fiwlbf UNIQUE (jti);

--
-- Name: volet_medical uk79yluk7c36asnij13dkuvvy4m; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.volet_medical
    ADD CONSTRAINT uk79yluk7c36asnij13dkuvvy4m UNIQUE (seance_id);

--
-- Name: modele_document_version uk_modele_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modele_document_version
    ADD CONSTRAINT uk_modele_version UNIQUE (modele_id, version);

--
-- Name: societes uke5kbinrarjmrqnuu03ddvv4vp; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.societes
    ADD CONSTRAINT uke5kbinrarjmrqnuu03ddvv4vp UNIQUE (code);

--
-- Name: volet_paramedical ukspqvmrqafu5yltmj68d0r39w8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.volet_paramedical
    ADD CONSTRAINT ukspqvmrqafu5yltmj68d0r39w8 UNIQUE (seance_id);

--
-- Name: direction_snapshot uq_direction_snapshot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.direction_snapshot
    ADD CONSTRAINT uq_direction_snapshot UNIQUE (societe_id, mois);

--
-- Name: mapping_comptable uq_mapping_center; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mapping_comptable
    ADD CONSTRAINT uq_mapping_center UNIQUE (center_id);

--
-- Name: periodes_comptables uq_periode_center; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodes_comptables
    ADD CONSTRAINT uq_periode_center UNIQUE (center_id, annee, mois);

--
-- Name: user_center_assignment user_center_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_center_assignment
    ADD CONSTRAINT user_center_assignment_pkey PRIMARY KEY (id);

--
-- Name: volet_medical volet_medical_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.volet_medical
    ADD CONSTRAINT volet_medical_pkey PRIMARY KEY (id);

--
-- Name: volet_paramedical volet_paramedical_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.volet_paramedical
    ADD CONSTRAINT volet_paramedical_pkey PRIMARY KEY (id);

--
-- Name: idx_assure_patient_primary_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assure_patient_primary_active ON public.assure_patient USING btree (patient_id, center_id, is_primary, date_fin_affectation);

--
-- Name: idx_audit_log_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_center ON public.audit_log USING btree (center_id, occurred_at DESC);

--
-- Name: idx_audit_log_societe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_societe ON public.audit_log USING btree (societe_id, occurred_at DESC);

--
-- Name: idx_audit_log_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_user ON public.audit_log USING btree (user_id, occurred_at DESC);

--
-- Name: idx_auth_refresh_token_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_refresh_token_user_id ON public.auth_refresh_token USING btree (user_id);

--
-- Name: idx_bl_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bl_center ON public.bons_commande USING btree (center_id);

--
-- Name: idx_bl_ligne_bon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bl_ligne_bon ON public.bons_commande_lignes USING btree (bon_commande_id);

--
-- Name: idx_bl_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bl_reference ON public.bons_commande USING btree (center_id, reference);

--
-- Name: idx_br_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_br_center ON public.bons_reception USING btree (center_id);

--
-- Name: idx_br_ligne_bon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_br_ligne_bon ON public.bons_reception_lignes USING btree (bon_reception_id);

--
-- Name: idx_br_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_br_reference ON public.bons_reception USING btree (center_id, reference);

--
-- Name: idx_bs_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bs_center ON public.bons_sortie USING btree (center_id);

--
-- Name: idx_bs_ligne_bon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bs_ligne_bon ON public.bons_sortie_lignes USING btree (bon_sortie_id);

--
-- Name: idx_bs_ligne_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bs_ligne_lot ON public.bons_sortie_lignes USING btree (lot_id);

--
-- Name: idx_bs_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bs_reference ON public.bons_sortie USING btree (center_id, reference);

--
-- Name: idx_bs_seance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bs_seance ON public.bons_sortie USING btree (seance_id);

--
-- Name: idx_direction_alert_history_societe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_direction_alert_history_societe ON public.direction_alert_history USING btree (societe_id, resolved_at, first_seen_at DESC);

--
-- Name: idx_ecriture_center_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ecriture_center_period ON public.ecritures_comptables USING btree (center_id, journal_code, date_ecriture);

--
-- Name: idx_emplacement_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_emplacement_center ON public.emplacements USING btree (center_id);

--
-- Name: idx_facture_lignes_facture; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_facture_lignes_facture ON public.facture_lignes USING btree (facture_id, center_id);

--
-- Name: idx_facture_reglements_facture; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_facture_reglements_facture ON public.facture_reglements USING btree (facture_id, center_id);

--
-- Name: idx_facture_reglements_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_facture_reglements_period ON public.facture_reglements USING btree (center_id, date_reglement);

--
-- Name: idx_factures_center_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_factures_center_patient ON public.factures USING btree (center_id, patient_id);

--
-- Name: idx_factures_center_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_factures_center_period ON public.factures USING btree (center_id, date_facturation);

--
-- Name: idx_fournisseur_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fournisseur_center ON public.fournisseurs USING btree (center_id);

--
-- Name: idx_lot_article; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_article ON public.lots USING btree (article_id);

--
-- Name: idx_lot_article_peremption; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_article_peremption ON public.lots USING btree (article_id, date_peremption);

--
-- Name: idx_lot_center; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_center ON public.lots USING btree (center_id);

--
-- Name: idx_mfa_recovery_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mfa_recovery_user ON public.app_user_mfa_recovery USING btree (user_id);

--
-- Name: idx_modele_version_modele; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modele_version_modele ON public.modele_document_version USING btree (modele_id, center_id);

--
-- Name: idx_seances_center_facture; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seances_center_facture ON public.seances USING btree (center_id, facture_id);

--
-- Name: idx_stock_mvt_article_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_mvt_article_date ON public.stock_movements USING btree (article_id, created_at);

--
-- Name: idx_stock_mvt_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_mvt_lot ON public.stock_movements USING btree (lot_id);

--
-- Name: idx_stock_mvt_seance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_mvt_seance ON public.stock_movements USING btree (seance_id);

--
-- Name: idx_tva_types_center_actif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tva_types_center_actif ON public.tva_types USING btree (center_id, actif);

--
-- Name: idx_tva_types_center_prestation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tva_types_center_prestation ON public.tva_types USING btree (center_id, type_prestation, date_debut_validite);

--
-- Name: app_user_role fk6hkq1uibwvjsusnnxrsm1gh83; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_role
    ADD CONSTRAINT fk6hkq1uibwvjsusnnxrsm1gh83 FOREIGN KEY (role_id) REFERENCES public.app_role(id);

--
-- Name: lignes_ecriture fk9pgmc5p85dwqukxkw1cpyjnwp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignes_ecriture
    ADD CONSTRAINT fk9pgmc5p85dwqukxkw1cpyjnwp FOREIGN KEY (ecriture_id) REFERENCES public.ecritures_comptables(id);

--
-- Name: app_user_role fkfnlxi1bmv5ao8u3nf30ymq7xa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_role
    ADD CONSTRAINT fkfnlxi1bmv5ao8u3nf30ymq7xa FOREIGN KEY (user_id) REFERENCES public.app_user(id);

--
-- Name: app_user_center fki6qgw3v5dwrjgqui853022uak; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_center
    ADD CONSTRAINT fki6qgw3v5dwrjgqui853022uak FOREIGN KEY (user_id) REFERENCES public.app_user(id);

--
-- Name: app_user_center fkmv99a8gj9jnmekj819t8j219w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_center
    ADD CONSTRAINT fkmv99a8gj9jnmekj819t8j219w FOREIGN KEY (center_id) REFERENCES public.centers(id);

--
--

