# Plan de travail complet — Cahier de dialyse

## Document destiné à un agent d'exécution

---

## CONTEXTE ET OBJECTIF

Développer une application web de gestion du cahier de dialyse pour un centre d'hémodialyse. L'application comporte un
volet back-end (API REST + base de données) et un volet front-end (interface responsive intégrée au thème UI existant).

Le cahier de dialyse est organisé en **4 steps** présentés dans un stepper :

- **Step 1** : Fiche patient (fonctionnalité existante à intégrer)
- **Step 2** : Volet paramédical — lié à une séance d'hémodialyse
- **Step 3** : Volet médical — lié au patient (pas à la séance)
- **Step 4** : Statistiques patient (vue lecture seule générée automatiquement)

Chaque step est soumis à des **droits d'accès distincts** et peut être **enregistré indépendamment**.

---

## CONTRAINTES TECHNIQUES GLOBALES

- Le front-end doit s'intégrer dans le **thème UI existant** de l'application (réutiliser les tokens CSS, composants,
  couleurs, typographie et spacing déjà définis — ne pas créer de nouveau système de design).
- Toutes les interfaces doivent être **responsives** : mobile (< 768px), tablette (768–1024px), desktop (> 1024px).
- Sur mobile : sections de formulaires empilées verticalement en accordéon, grilles multi-relevés en scroll horizontal,
  boutons de validation épinglés en bas d'écran.
- Le back-end expose une **API REST versionnée** (`/api/v1/`).
- Authentification par **JWT** avec refresh token.
- Base de données : **PostgreSQL**.
- Les droits sont vérifiés **à la fois côté API (middleware guard)** et côté front (guard composant / désactivation UI).

---

## STRUCTURE DES RÔLES ET DROITS

| Rôle         | Libellé                                         |
|--------------|-------------------------------------------------|
| `ADMIN`      | Administrateur — accès complet                  |
| `INFIRMIER`  | Infirmier — saisie step 2                       |
| `MEDECIN`    | Médecin néphrologue / dialyseur — saisie step 3 |
| `SECRETAIRE` | Secrétaire — saisie step 1                      |

### Droits par step

| Step                   | Lecture | Écriture / Validation                     |
|------------------------|---------|-------------------------------------------|
| Step 1 — Admin patient | Tous    | ADMIN, SECRETAIRE                         |
| Step 2 — Paramédical   | Tous    | INFIRMIER                                 |
| Step 3 — Médical       | Tous    | MEDECIN                                   |
| Step 4 — Statistiques  | Tous    | Aucun (vue auto) — Export PDF/CSV : ADMIN |

---

## PHASE 1 — BASE DE DONNÉES ET MODÈLE DE DONNÉES

### 1.1 Tables principales à créer

#### Table `users`

```
id              UUID PRIMARY KEY
nom             VARCHAR
prenom          VARCHAR
email           VARCHAR UNIQUE
password_hash   VARCHAR
role            ENUM('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')
actif           BOOLEAN DEFAULT true
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

#### Table `patients`

```
id                  UUID PRIMARY KEY
nom                 VARCHAR
prenom              VARCHAR
date_naissance      DATE
sexe                ENUM('M','F')
num_dossier         VARCHAR UNIQUE
adresse             TEXT
telephone           VARCHAR
contact_urgence     VARCHAR
groupe_sanguin      ENUM('A+','A-','B+','B-','AB+','AB-','O+','O-')
statut_dossier      ENUM('ACTIF','INACTIF','TRANSFERE','DECEDE')
created_at          TIMESTAMP
updated_at          TIMESTAMP
```

#### Table `seances`

```
id                  UUID PRIMARY KEY
patient_id          UUID FK → patients.id
date_seance         DATE NOT NULL  -- initialisée à la date du jour à la création
statut              ENUM('BROUILLON','VALIDE','SIGNE') DEFAULT 'BROUILLON'
created_by          UUID FK → users.id
validated_by        UUID FK → users.id (infirmier qui valide)
validated_at        TIMESTAMP
created_at          TIMESTAMP
updated_at          TIMESTAMP
```

#### Table `volet_paramedical`

> Lié à une séance (relation 1-1 avec `seances`)

```
id                      UUID PRIMARY KEY
seance_id               UUID FK → seances.id UNIQUE

-- Pré-séance
poids_sec_cible_kg      DECIMAL(5,2)   -- prescrit par le médecin
poids_avant_kg          DECIMAL(5,2)   -- pesée à l'arrivée
surcharge_hydrique_kg   DECIMAL(5,2)   -- calculé auto : poids_avant - poids_sec_cible
ta_systolique_avant     SMALLINT       -- mmHg
ta_diastolique_avant    SMALLINT
fc_avant                SMALLINT       -- bpm
temperature_avant       DECIMAL(4,1)   -- °C
etat_general_score      SMALLINT       -- 1 à 5
oedemes                 BOOLEAN
oedemes_localisation    VARCHAR
dyspnee                 BOOLEAN

-- Paramètres machine
qb_ml_min               SMALLINT       -- débit sang (mL/min)
qd_ml_min               SMALLINT       -- débit dialysat (mL/min)
uf_cible_ml             SMALLINT       -- ultrafiltration cible
duree_prevue_min        SMALLINT
type_dialyseur          VARCHAR        -- référence catalogue
type_bain               ENUM('BICARBONATE_STD','PERSONNALISE')
conductivite            DECIMAL(4,2)   -- mS/cm
temperature_bain        DECIMAL(4,1)   -- °C

-- Anticoagulation
anticoag_type           ENUM('HNF','HBPM','CITRATE','AUCUN')
anticoag_dose_initiale  DECIMAL(8,2)
anticoag_dose_horaire   DECIMAL(8,2)
nb_rincages             SMALLINT
volume_rincage_ml       SMALLINT
heure_arret_heparine    TIME

-- Abord vasculaire (à la séance)
abord_type              ENUM('FAV','PTFE','KT_TUNNELISE','KT_AIGU')
abord_cote              ENUM('GAUCHE','DROIT')
aiguille_calibre        VARCHAR
ordre_ponction          ENUM('ANTEROGRADE','RETROGRADE','BUTTONHOLE')
aspect_site             ENUM('BON','DIFFICILE','SUINTEMENT','HEMATOME','AUTRE')
incident_ponction       BOOLEAN
incident_ponction_detail VARCHAR

-- Surveillance per-séance (tableau multi-relevés → table dédiée)
-- voir table `releves_per_seance`

-- Post-séance
poids_apres_kg          DECIMAL(5,2)
uf_reelle_ml            SMALLINT       -- calculé auto : (poids_avant - poids_apres) * 1000
ta_systolique_apres     SMALLINT
ta_diastolique_apres    SMALLINT
fc_apres                SMALLINT
kt_v_realise            DECIMAL(4,2)   -- si disponible sur moniteur

-- Incidents/complications (flags booléens)
incident_hypotension    BOOLEAN
incident_crampes        BOOLEAN
incident_cephalees      BOOLEAN
incident_frissons       BOOLEAN
incident_nausees        BOOLEAN
incident_thrombose      BOOLEAN
incident_autre          VARCHAR

-- Médicaments injectés fin de séance (liste → table dédiée)
-- voir table `medicaments_seance`

-- Signature infirmier
signature_infirmier_id  UUID FK → users.id
signature_at            TIMESTAMP

created_at              TIMESTAMP
updated_at              TIMESTAMP
```

#### Table `releves_per_seance`

> Grille de relevés toutes les 30-60 min durant la séance

```
id                      UUID PRIMARY KEY
volet_paramedical_id    UUID FK → volet_paramedical.id
heure_releve            TIME NOT NULL
ta_systolique           SMALLINT
ta_diastolique          SMALLINT
fc                      SMALLINT
pression_veineuse       SMALLINT       -- mmHg
pression_arterielle     SMALLINT       -- mmHg
created_at              TIMESTAMP
```

#### Table `medicaments_seance`

```
id                      UUID PRIMARY KEY
volet_paramedical_id    UUID FK → volet_paramedical.id
nom_medicament          VARCHAR
dose                    VARCHAR
voie                    VARCHAR
heure_injection         TIME
created_at              TIMESTAMP
```

#### Table `articles_consommes_seance`

> Articles consommés lors de la séance (déclenche sortie de stock à la validation)

```
id                      UUID PRIMARY KEY
volet_paramedical_id    UUID FK → volet_paramedical.id
article_id              UUID FK → articles.id
quantite                DECIMAL(8,2)
lot                     VARCHAR        -- numéro de lot si traçabilité
stock_mouvement_id      UUID FK → stock_mouvements.id  -- créé à la validation
created_at              TIMESTAMP
```

#### Table `dossier_medical_patient`

> Lié au patient (1-1), PAS à la séance

```
id                          UUID PRIMARY KEY
patient_id                  UUID FK → patients.id UNIQUE
nephropathie_initiale       VARCHAR        -- ex: GNMP, HTA, diabète, polykystose...
date_mise_en_dialyse        DATE
hepatite_b_statut           ENUM('NEGATIF','PORTEUR','VACCINE','IMMUNE','INCONNU')
hepatite_c_statut           ENUM('NEGATIF','POSITIF','TRAITE','INCONNU')
observation_globale         TEXT           -- seul champ texte libre
created_at                  TIMESTAMP
updated_at                  TIMESTAMP
```

#### Table `abords_vasculaires_historique`

> Historique complet des abords vasculaires du patient

```
id                  UUID PRIMARY KEY
patient_id          UUID FK → patients.id
type_abord          ENUM('FAV','PTFE','KT_TUNNELISE','KT_AIGU')
cote                ENUM('GAUCHE','DROIT')
localisation        VARCHAR
date_creation       DATE
date_fin            DATE               -- NULL si actif
actif               BOOLEAN
complications       VARCHAR
created_at          TIMESTAMP
```

#### Table `prescriptions_medicales`

> Prescriptions datées du médecin (1-N avec patient)

```
id                          UUID PRIMARY KEY
patient_id                  UUID FK → patients.id
date_prescription           DATE NOT NULL
medecin_id                  UUID FK → users.id

-- Paramètres cible dialyse
qb_cible                    SMALLINT
qd_cible                    SMALLINT
uf_max_ml                   SMALLINT
duree_cible_min             SMALLINT
type_dialyseur_prescrit     VARCHAR
anticoag_type_prescrit      ENUM('HNF','HBPM','CITRATE','AUCUN')

-- Traitement de l'anémie — EPO
epo_molecule                VARCHAR        -- ex: Darbépoétine, Époétine alfa...
epo_dose_ui                 INTEGER
epo_voie                    ENUM('SC','IV')
epo_frequence               VARCHAR        -- ex: 1x/semaine, 2x/mois...

-- Traitement de l'anémie — Fer injectable
fer_molecule                VARCHAR        -- ex: Fer saccharose, Ferric carboxymaltose...
fer_dose_mg                 INTEGER
fer_voie                    ENUM('IV')
fer_frequence               VARCHAR

-- Autres traitements (liste → table dédiée)
-- voir table `autres_traitements_prescription`

created_at                  TIMESTAMP
updated_at                  TIMESTAMP
```

#### Table `autres_traitements_prescription`

```
id                      UUID PRIMARY KEY
prescription_id         UUID FK → prescriptions_medicales.id
nom_medicament          VARCHAR
dose                    VARCHAR
voie                    VARCHAR
frequence               VARCHAR
```

#### Table `resultats_analyses`

> Résultats biologiques datés (1-N avec patient) — tous champs numériques typés

```
id                  UUID PRIMARY KEY
patient_id          UUID FK → patients.id
date_prelevement    DATE NOT NULL

-- Numération formule sanguine
hb_g_dl             DECIMAL(4,1)   -- hémoglobine
ht_pct              DECIMAL(5,2)   -- hématocrite
plaquettes          INTEGER        -- G/L

-- Bilan martial
ferritine_ng_ml     DECIMAL(8,1)
cstf_pct            DECIMAL(5,2)   -- coefficient de saturation de la transferrine
epo_endogene_mui_ml DECIMAL(8,2)   -- si dosé

-- Dialyse adequacy
uree_pre_mg_dl      DECIMAL(7,2)
uree_post_mg_dl     DECIMAL(7,2)
creatinine_mg_dl    DECIMAL(7,2)
kt_v_mensuel        DECIMAL(4,2)   -- calculé ou saisi

-- Bilan phospho-calcique
phosphore_mg_dl     DECIMAL(6,2)
calcium_mg_dl       DECIMAL(6,2)
pth_pg_ml           DECIMAL(8,1)   -- parathormone

-- Bilan nutritionnel / inflammatoire
albumine_g_dl       DECIMAL(4,1)
proteines_g_dl      DECIMAL(4,1)
crp_mg_l            DECIMAL(7,2)

created_at          TIMESTAMP
```

#### Table `articles`

```
id              UUID PRIMARY KEY
code_article    VARCHAR UNIQUE
designation     VARCHAR
unite           VARCHAR           -- ex: pièce, boîte, flacon
stock_actuel    DECIMAL(10,2)
seuil_alerte    DECIMAL(10,2)
actif           BOOLEAN DEFAULT true
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

#### Table `stock_mouvements`

```
id                  UUID PRIMARY KEY
article_id          UUID FK → articles.id
type_mouvement      ENUM('ENTREE','SORTIE')
quantite            DECIMAL(10,2)
motif               VARCHAR         -- ex: 'Séance dialyse', 'Inventaire', 'Retour'
seance_id           UUID FK → seances.id  -- NULL si mouvement manuel
created_by          UUID FK → users.id
created_at          TIMESTAMP
```

---

## PHASE 2 — API REST BACK-END

### 2.1 Conventions générales

- Base URL : `/api/v1/`
- Toutes les réponses en JSON
- Format date ISO 8601 : `YYYY-MM-DD`, datetime : `YYYY-MM-DDTHH:mm:ssZ`
- Pagination : query params `?page=1&limit=20`
- Erreurs : `{ "error": "message", "code": "ERROR_CODE" }`
- Chaque endpoint protégé vérifie le JWT et le rôle via middleware

### 2.2 Endpoints — Authentification

```
POST   /api/v1/auth/login          Corps: { email, password } → { access_token, refresh_token, user }
POST   /api/v1/auth/refresh        Corps: { refresh_token } → { access_token }
POST   /api/v1/auth/logout         Invalide le refresh token
GET    /api/v1/auth/me             Retourne l'utilisateur connecté
```

### 2.3 Endpoints — Patients

```
GET    /api/v1/patients            Liste paginée, filtres: ?search=nom&statut=ACTIF
POST   /api/v1/patients            Créer un patient (ADMIN, SECRETAIRE)
GET    /api/v1/patients/:id        Détail patient (tous rôles)
PUT    /api/v1/patients/:id        Modifier patient (ADMIN, SECRETAIRE)
DELETE /api/v1/patients/:id        Désactiver (ADMIN uniquement)
```

### 2.4 Endpoints — Séances

```
GET    /api/v1/patients/:id/seances          Liste séances d'un patient
POST   /api/v1/patients/:id/seances          Créer une séance (date = aujourd'hui auto) (INFIRMIER, ADMIN)
GET    /api/v1/seances/:id                   Détail séance avec volet paramédical
PUT    /api/v1/seances/:id/valider           Valider + signer la séance (INFIRMIER)
                                              → déclenche sortie de stock
                                              → verrouille le volet paramédical
```

### 2.5 Endpoints — Volet paramédical

```
GET    /api/v1/seances/:id/volet-paramedical           Lire le volet
POST   /api/v1/seances/:id/volet-paramedical           Créer le volet (INFIRMIER)
PUT    /api/v1/seances/:id/volet-paramedical           Modifier (INFIRMIER, si statut BROUILLON)

-- Relevés per-séance
POST   /api/v1/seances/:id/volet-paramedical/releves   Ajouter un relevé (INFIRMIER)
DELETE /api/v1/seances/:id/volet-paramedical/releves/:releve_id

-- Articles consommés
POST   /api/v1/seances/:id/volet-paramedical/articles  Ajouter un article
DELETE /api/v1/seances/:id/volet-paramedical/articles/:article_id
```

### 2.6 Endpoints — Volet médical patient

```
GET    /api/v1/patients/:id/dossier-medical            Lire le dossier médical (tous rôles)
POST   /api/v1/patients/:id/dossier-medical            Créer le dossier (MEDECIN)
PUT    /api/v1/patients/:id/dossier-medical            Modifier (MEDECIN)

-- Abords vasculaires
GET    /api/v1/patients/:id/abords-vasculaires
POST   /api/v1/patients/:id/abords-vasculaires         (MEDECIN)
PUT    /api/v1/patients/:id/abords-vasculaires/:abord_id

-- Prescriptions
GET    /api/v1/patients/:id/prescriptions              ?from=YYYY-MM-DD&to=YYYY-MM-DD
POST   /api/v1/patients/:id/prescriptions              (MEDECIN)
PUT    /api/v1/patients/:id/prescriptions/:prescription_id
DELETE /api/v1/patients/:id/prescriptions/:prescription_id

-- Résultats d'analyses
GET    /api/v1/patients/:id/analyses                   ?from=YYYY-MM-DD&to=YYYY-MM-DD
POST   /api/v1/patients/:id/analyses                   (MEDECIN)
PUT    /api/v1/patients/:id/analyses/:analyse_id
DELETE /api/v1/patients/:id/analyses/:analyse_id
```

### 2.7 Endpoints — Statistiques

```
GET    /api/v1/patients/:id/stats/paramedical          Agrégats séances : poids, TA, UF, incidents
GET    /api/v1/patients/:id/stats/medical              Évolution Hb, ferritine, Kt/V, phospho-calcique, EPO
GET    /api/v1/patients/:id/stats/export               ?format=pdf|csv (ADMIN uniquement)
```

### 2.8 Endpoints — Stock

```
GET    /api/v1/articles                                Liste articles (filtre ?search=)
POST   /api/v1/articles                                Créer article (ADMIN)
PUT    /api/v1/articles/:id                            Modifier article (ADMIN)
GET    /api/v1/stock/mouvements                        Historique mouvements (?article_id, ?seance_id)
POST   /api/v1/stock/mouvements                        Mouvement manuel (ADMIN)
GET    /api/v1/stock/alertes                           Articles sous seuil d'alerte
GET    /api/v1/stock/rapport                           Consommation par période (?from=&to=)
```

### 2.9 Logique métier critique — Validation séance (step 2)

Lorsque `PUT /api/v1/seances/:id/valider` est appelé :

1. Vérifier que le rôle est `INFIRMIER`.
2. Vérifier que le volet paramédical est complet (champs obligatoires renseignés).
3. Calculer automatiquement : `surcharge_hydrique_kg = poids_avant - poids_sec_cible`,
   `uf_reelle_ml = (poids_avant - poids_apres) * 1000`.
4. Créer un `stock_mouvement` de type `SORTIE` pour chaque article dans `articles_consommes_seance` : décrémenter
   `articles.stock_actuel`, lier `stock_mouvement.seance_id`.
5. Enregistrer `validated_by = user_id`, `validated_at = NOW()`, `statut = 'VALIDE'`.
6. Verrouiller le volet : toute tentative de modification ultérieure retourne `403 Forbidden`.
7. Vérifier les seuils d'alerte stock et générer des alertes si dépassés.

---

## PHASE 3 — FRONT-END : SOCLE UX

### 3.1 Intégration thème existant

- Récupérer et réutiliser sans modification les tokens CSS/variables déjà définis dans l'application (couleurs
  primaires, secondaires, états, typographie, border-radius, spacing, ombres).
- Réutiliser les composants UI existants : boutons, champs de saisie, selects, toggles, badges, modales,
  toasts/notifications.
- Ne créer de nouveaux composants que si aucun composant existant ne couvre le besoin (ex : grille de relevés
  per-séance, stepper, graphiques statistiques).

### 3.2 Responsive — règles par breakpoint

| Contexte | Breakpoint | Comportement                                                                             |
|----------|------------|------------------------------------------------------------------------------------------|
| Mobile   | < 768px    | Formulaires en colonne unique, accordéon par section, bouton de validation sticky bottom |
| Tablette | 768–1024px | 2 colonnes, stepper horizontal condensé                                                  |
| Desktop  | > 1024px   | 3 colonnes, stepper horizontal complet avec libellés                                     |

### 3.3 Composant Stepper global

Le stepper est le conteneur principal du cahier de dialyse. Il doit :

- Afficher 4 onglets/pills horizontaux : Step 1, Step 2, Step 3, Step 4
- Sur chaque pill, afficher un badge de statut : `brouillon` (gris) / `enregistré` (bleu) / `validé` (vert) / `signé` (
  vert foncé)
- Si l'utilisateur connecté n'a pas le droit d'écriture sur un step : afficher le pill en grisé, désactiver le clic,
  afficher une infobulle "Accès restreint — lecture seule"
- Permettre la navigation libre entre les steps (un step accessible en lecture peut être consulté sans restriction)
- Chaque step a son propre bouton "Enregistrer" indépendant — la sauvegarde d'un step ne déclenche pas la sauvegarde des
  autres
- Sur mobile : le stepper devient un sélecteur déroulant ou des pills scrollables horizontalement

---

## PHASE 4 — FRONT-END : STEP 1 — FICHE PATIENT

### 4.1 Objectif

Intégrer la fonctionnalité de fiche patient existante dans le stepper, sans la refaire. Adapter uniquement :

- L'état de sauvegarde pour alimenter le badge du stepper
- Le bouton de navigation vers le step suivant
- L'affichage en lecture seule si le rôle n'est pas ADMIN ou SECRETAIRE

### 4.2 Données affichées

Identité complète, date de naissance, numéro de dossier, groupe sanguin, contact d'urgence, adresse, statut dossier,
documents joints.

---

## PHASE 5 — FRONT-END : STEP 2 — VOLET PARAMÉDICAL

### 5.1 Structure de l'écran

L'écran est divisé en **sections accordéon** (chacune peut être ouverte/fermée indépendamment) :

#### Section A — Pré-séance

| Champ                             | Type UI                                            | Règles                                                      |
|-----------------------------------|----------------------------------------------------|-------------------------------------------------------------|
| Poids sec cible (kg)              | Champ numérique décimal                            | Pré-rempli depuis la dernière prescription si disponible    |
| Poids réel avant (kg)             | Champ numérique décimal                            | Obligatoire                                                 |
| Surcharge hydrique (kg)           | Champ calculé automatiquement (lecture seule)      | = poids_avant − poids_sec_cible, affiché en rouge si > 3 kg |
| TA systolique / diastolique avant | Double champ numérique entier (mmHg)               | Obligatoire                                                 |
| FC avant (bpm)                    | Champ numérique entier                             | Obligatoire                                                 |
| Température (°C)                  | Champ numérique décimal                            |                                                             |
| État général                      | Select ou rating 1–5 (étoiles ou chips)            |                                                             |
| Œdèmes                            | Toggle Oui/Non → si Oui : champ texte localisation |                                                             |
| Dyspnée                           | Toggle Oui/Non                                     |                                                             |

#### Section B — Paramètres machine

| Champ                      | Type UI                                      | Règles                                       |
|----------------------------|----------------------------------------------|----------------------------------------------|
| Débit sang Qb (mL/min)     | Champ numérique entier                       | Obligatoire                                  |
| Débit dialysat Qd (mL/min) | Champ numérique entier                       |                                              |
| UF cible (mL)              | Champ numérique entier                       | Pré-rempli depuis prescription si disponible |
| Durée prévue (min)         | Champ numérique entier                       |                                              |
| Type de dialyseur          | Select (catalogue)                           |                                              |
| Type de bain               | Select : Bicarbonate standard / Personnalisé |                                              |
| Conductivité (mS/cm)       | Champ numérique décimal                      |                                              |
| Température bain (°C)      | Champ numérique décimal                      |                                              |

#### Section C — Anticoagulation

| Champ                | Type UI                               | Règles                 |
|----------------------|---------------------------------------|------------------------|
| Type                 | Select : HNF / HBPM / Citrate / Aucun |                        |
| Dose initiale        | Champ numérique décimal               | Masqué si type = Aucun |
| Dose horaire         | Champ numérique décimal               |                        |
| Nombre de rinçages   | Champ numérique entier                |                        |
| Volume rinçage (mL)  | Champ numérique entier                |                        |
| Heure arrêt héparine | Champ heure (HH:mm)                   |                        |

#### Section D — Abord vasculaire (à la séance)

| Champ                 | Type UI                                                  | Règles                              |
|-----------------------|----------------------------------------------------------|-------------------------------------|
| Type d'abord          | Select : FAV / PTFE / KT tunnelisé / KT aigu             | Pré-rempli depuis historique abords |
| Côté                  | Select : Gauche / Droit                                  |                                     |
| Calibre des aiguilles | Champ texte court                                        |                                     |
| Ordre de ponction     | Select : Antérograde / Rétrograde / Buttonhole           |                                     |
| Aspect du site        | Select : Bon / Difficile / Suintement / Hématome / Autre |                                     |
| Incident de ponction  | Toggle Oui/Non → si Oui : champ texte détail             |                                     |

#### Section E — Surveillance per-séance

- Tableau éditable avec colonnes : Heure / TA sys / TA dias / FC / Pression veineuse / Pression artérielle
- Bouton "+ Ajouter un relevé" ajoute une ligne avec l'heure courante pré-remplie
- Chaque ligne est supprimable
- Sur mobile : tableau en scroll horizontal

#### Section F — Post-séance

| Champ                             | Type UI                                                                                                                            | Règles                               |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| Poids après (kg)                  | Champ numérique décimal                                                                                                            | Obligatoire pour validation          |
| UF réelle (mL)                    | Champ calculé auto (lecture seule)                                                                                                 | = (poids_avant − poids_apres) × 1000 |
| TA systolique / diastolique après | Double champ numérique entier                                                                                                      |                                      |
| FC après (bpm)                    | Champ numérique entier                                                                                                             |                                      |
| Kt/V réalisé                      | Champ numérique décimal                                                                                                            |                                      |
| Incidents / complications         | Groupe de checkboxes : Hypotension / Crampes / Céphalées / Frissons / Nausées / Thrombose circuit / Autre → si Autre : champ texte |                                      |

#### Section G — Médicaments injectés

- Liste dynamique : pour chaque médicament : nom (autocomplete sur catalogue), dose, voie, heure
- Bouton "+ Ajouter un médicament"
- Chaque entrée est supprimable

#### Section H — Articles consommés

- Champ de recherche avec autocomplete sur le catalogue `articles`
- Pour chaque article sélectionné : désignation (lecture seule), quantité (numérique), numéro de lot (optionnel)
- Bouton "+ Ajouter un article"
- Chaque entrée est supprimable
- **Note importante** : les articles ne sont décrementés du stock qu'à la validation (bouton "Valider et signer"), pas à
  la saisie

### 5.2 Actions disponibles (step 2)

- **Enregistrer** : sauvegarde le brouillon (API PUT volet-paramedical), mise à jour du badge stepper
- **Valider et signer** : disponible uniquement si rôle = INFIRMIER, uniquement si volet = BROUILLON
    - Modale de confirmation : "Vous êtes sur le point de valider la séance du [date]. Cette action est irréversible.
      Les articles consommés seront déduits du stock."
    - Après confirmation : appel API valider, verrouillage de tous les champs, badge passe à "Validé"
    - Gestion d'erreur : si stock insuffisant pour un article, afficher une alerte non bloquante (la validation continue
      mais le mouvement est marqué en anomalie)

---

## PHASE 6 — FRONT-END : STEP 3 — VOLET MÉDICAL PATIENT

### 6.1 Spécificité importante

Ce volet est lié **au patient**, pas à la séance. Il s'affiche de façon identique quel que soit le contexte de
navigation (depuis une séance ou depuis la liste patients). Il permet de consulter et enrichir le dossier médical
longitudinal du patient.

### 6.2 Structure de l'écran

#### Section A — Dossier de base (données stables)

Formulaire simple avec champs en lecture seule par défaut, bouton "Modifier" pour MEDECIN :

- Néphropathie initiale (champ texte court, select ou autocomplete sur nomenclature)
- Date de mise en dialyse
- Statut hépatite B : select (Négatif / Porteur / Vacciné / Immunisé / Inconnu)
- Statut hépatite C : select (Négatif / Positif / Traité / Inconnu)
- Abords vasculaires (tableau historique avec colonnes : Type / Côté / Localisation / Date création / Date fin / Actif)
    - Bouton "+ Ajouter un abord" (MEDECIN)
    - Toggle "Actif / Inactif" sur chaque ligne
- Observation globale : textarea (seul champ texte libre du volet médical)

#### Section B — Prescriptions médicales (timeline datée)

- Affichage sous forme de **timeline verticale**, triée par date décroissante
- Chaque entrée de prescription affiche : date, prescripteur, paramètres dialyse, EPO (molécule + dose + voie +
  fréquence), fer (molécule + dose + voie + fréquence), autres traitements
- Bouton "+ Nouvelle prescription à la date du jour" (MEDECIN) → ouvre un formulaire de saisie avec tous les champs en *
  *selects et champs numériques** (aucun champ texte libre sauf "nom médicament" pour les autres traitements)
- Chaque prescription peut être modifiée ou supprimée (MEDECIN) uniquement si elle est la plus récente (les
  prescriptions antérieures sont en lecture seule pour l'historique)

**Champs de prescription — détail UI** :

| Champ                   | Type UI                                              |
|-------------------------|------------------------------------------------------|
| Qb cible (mL/min)       | Champ numérique entier                               |
| Qd cible (mL/min)       | Champ numérique entier                               |
| UF max (mL)             | Champ numérique entier                               |
| Durée cible (min)       | Champ numérique entier                               |
| Type dialyseur prescrit | Select (catalogue)                                   |
| Anticoagulation type    | Select : HNF / HBPM / Citrate / Aucun                |
| EPO molécule            | Select (nomenclature des EPO disponibles)            |
| EPO dose (UI)           | Champ numérique entier                               |
| EPO voie                | Select : SC / IV                                     |
| EPO fréquence           | Select : 1x/sem / 2x/sem / 1x/2sem / 1x/mois / Autre |
| Fer molécule            | Select (nomenclature des fers injectables)           |
| Fer dose (mg)           | Champ numérique entier                               |
| Fer fréquence           | Select                                               |
| Autres traitements      | Liste dynamique : nom + dose + voie + fréquence      |

#### Section C — Résultats d'analyses (tableau daté)

- Tableau paginé trié par date décroissante avec toutes les valeurs biologiques en colonnes
- Bouton "+ Saisir des résultats" (MEDECIN) → formulaire avec tous les champs en **champs numériques typés** (aucun
  texte libre)
- Valeurs hors normes affichées en rouge (seuils configurables ou seuils standard codés en dur)
- Chaque ligne modifiable ou supprimable (MEDECIN)

**Colonnes du tableau** : Date / Hb (g/dL) / Ht (%) / Plaquettes (G/L) / Ferritine (ng/mL) / CSTf (%) / Urée pré (
mg/dL) / Kt/V / Phosphore (mg/dL) / Calcium (mg/dL) / PTH (pg/mL) / Albumine (g/dL) / CRP (mg/L)

### 6.3 Actions disponibles (step 3)

- **Enregistrer dossier de base** : sauvegarde indépendante (MEDECIN)
- **Enregistrer prescription** : sauvegarde la prescription en cours de saisie (MEDECIN)
- **Enregistrer résultats** : sauvegarde les résultats en cours de saisie (MEDECIN)
- Pas de "validation" globale sur ce volet — chaque sous-section se sauvegarde indépendamment

---

## PHASE 7 — FRONT-END : STEP 4 — STATISTIQUES PATIENT

### 7.1 Principe

Step en lecture seule pour tous les rôles. Les données sont calculées côté API et affichées sous forme de graphiques et
indicateurs. Aucune saisie dans ce step.

### 7.2 Statistiques paramédicales (alimentées par le step 2)

| Graphique            | Type                 | Données                                                |
|----------------------|----------------------|--------------------------------------------------------|
| Évolution du poids   | Courbe temporelle    | Poids avant / Poids après / Poids sec cible par séance |
| Évolution de la TA   | Courbe multi-séries  | TA systolique + diastolique avant et après par séance  |
| UF réalisée vs cible | Graphique en barres  | UF cible vs UF réelle par séance                       |
| Incidents            | Histogramme par type | Nombre d'incidents par type sur la période             |
| Consommables         | Tableau ou barres    | Quantité/coût par article sur la période               |

### 7.3 Statistiques médicales (alimentées par le step 3)

| Graphique                  | Type                | Données                                                            |
|----------------------------|---------------------|--------------------------------------------------------------------|
| Évolution de l'hémoglobine | Courbe temporelle   | Hb (g/dL) avec zone cible grisée (ex: 10–12 g/dL)                  |
| Suivi EPO                  | Courbe ou barres    | Dose EPO dans le temps, avec superposition Hb                      |
| Suivi fer injectable       | Barres              | Doses de fer injectées dans le temps, avec superposition ferritine |
| Kt/V mensuel               | Courbe              | Valeurs avec seuil cible (ligne pointillée à 1.2)                  |
| Bilan phospho-calcique     | Courbe multi-séries | Phosphore + Calcium + PTH dans le temps                            |
| Bilan martial              | Courbe multi-séries | Ferritine + CSTf dans le temps                                     |

### 7.4 Filtres de la vue stats

- Sélecteur de période : 1 mois / 3 mois / 6 mois / 1 an / Personnalisé
- Filtre par type de données : Paramédicales / Médicales / Toutes

### 7.5 Export

Bouton "Exporter" visible uniquement pour le rôle ADMIN :

- Format PDF : rapport patient complet (fiche + stats avec graphiques)
- Format CSV : données brutes tabulées

---

## PHASE 8 — BACK-END : GESTION DU STOCK

### 8.1 Référentiel articles

Interface de gestion du catalogue (ADMIN uniquement) :

- Liste des articles avec colonnes : Code / Désignation / Unité / Stock actuel / Seuil alerte / Statut
- Actions : Créer / Modifier / Activer-Désactiver un article
- Mise en évidence visuelle des articles sous seuil d'alerte

### 8.2 Mouvements de stock

- Historique complet des entrées et sorties avec filtres : article, type de mouvement, période, séance
- Mouvement manuel (ADMIN) : formulaire entrée/sortie avec motif
- Les sorties automatiques (liées à une validation séance) affichent le lien vers la séance concernée

### 8.3 Tableau de bord stock

- Carte récapitulative : articles en alerte / valeur totale estimée du stock
- Consommation par période : top articles les plus consommés
- Export CSV du rapport de consommation (ADMIN)

---

## PHASE 9 — QUALITÉ, SÉCURITÉ ET DÉPLOIEMENT

### 9.1 Sécurité

- Toutes les routes API sont protégées par middleware JWT
- Le middleware vérifie le rôle pour chaque endpoint sensible (voir tableau droits § intro)
- Les volets verrouillés (séance validée) retournent `403` sur toute tentative de modification
- Les mots de passe sont hashés (bcrypt, cost factor ≥ 12)
- Les tokens JWT expirent en 15 min, refresh token en 7 jours
- Audit log : toute création, modification, validation ou signature est enregistrée avec `user_id`, `action`,
  `timestamp`, `ip`

### 9.2 Validation des données

- Côté API : validation stricte de tous les champs entrants (types, plages, valeurs autorisées)
- Les champs calculés (`surcharge_hydrique`, `uf_reelle`) ne sont jamais acceptés en entrée — ils sont toujours
  recalculés côté serveur
- Les énumérations sont validées contre les valeurs autorisées (ex: groupe sanguin, type d'abord)

### 9.3 Tests à prévoir

- Tests unitaires : logique de calcul automatique, logique de validation séance, logique de sortie de stock
- Tests d'intégration : flux complet d'une séance de la création à la validation
- Tests de droits : vérifier qu'un INFIRMIER ne peut pas modifier le step 3, qu'un MEDECIN ne peut pas valider une
  séance, etc.

### 9.4 Déploiement

- Dockeriser l'application back-end et la base de données
- Variables d'environnement pour : DATABASE_URL, JWT_SECRET, JWT_REFRESH_SECRET, SMTP (si emails d'alerte stock)
- Migrations de base de données versionnées et exécutées automatiquement au démarrage

---

## ORDRE D'EXÉCUTION RECOMMANDÉ POUR L'AGENT

1. Créer le schéma de base de données complet (migrations) — Phase 1
2. Mettre en place l'authentification et le système de rôles — Phase 2.1 et 2.2
3. Implémenter les endpoints patients et séances — Phase 2.3 et 2.4
4. Mettre en place le socle UX : stepper, thème, responsive — Phase 3
5. Intégrer le step 1 (fiche patient existante) dans le stepper — Phase 4
6. Implémenter le volet paramédical côté API puis côté front — Phases 2.5 et 5
7. Implémenter la logique de validation séance + sortie de stock — Phase 2.9 et 8
8. Implémenter le volet médical côté API puis côté front — Phases 2.6 et 6
9. Implémenter les statistiques côté API puis côté front — Phases 2.7 et 7
10. Implémenter le tableau de bord stock — Phase 8
11. Tests, sécurité, déploiement — Phase 9

---

*Fin du plan de travail — version destinée à l'agent d'exécution*
