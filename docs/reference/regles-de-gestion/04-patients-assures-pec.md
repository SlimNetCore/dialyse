# 04 — Patients, assurés, attestations de droits, prises en charge

> Préfixes `RG-PAT` (patient), `RG-ASS` (assuré), `RG-ATT` (attestation de droits), `RG-PEC` (prise en charge).
> Sources : `Patient`, `PatientDomainService`, `PatientApplicationService`, `PatientRestController`, `Assure`,
> `AssurePatientAssignment`, `AttestationDroit`, `AttestationDomainService`, `PriseEnCharge`, `PecDomainService`,
> `PecRestController`, `PatientListQueryService`.

## 4.1 Fiche patient

- **RG-PAT-001** — Un patient appartient à un centre (`centerId`) ; sa fiche n'est lisible/modifiable que dans ce centre
  (RG-TRV-001/002). Un patient inconnu du centre : « Patient introuvable » (400).
- **RG-PAT-002** — Champs obligatoires à la création : nom, prénom, sexe, date d'admission, date de naissance, numéro
  d'assurance (valeur non vide). Une valeur manquante est refusée (400).
- **RG-PAT-003** — Le code patient est généré par le système (`PAT-` + 8 caractères hexadécimaux majuscules) et n'est
  jamais
  saisi ni modifié.
- **RG-PAT-004** — Le **numéro d'assurance est unique par centre** : créer un patient avec un numéro déjà utilisé dans
  le centre
  est refusé (« Numero assurance deja utilise pour ce centre »).
- **RG-PAT-005** — État du patient : `PERMANENT` (défaut à la création), `OCCASIONNEL`, `TRANSFERE`, `DECEDE`, `GREFFE`,
  `GUERRI`, `VACANCIER_LOCAL`, `VACANCIER_ETRANGER`, avec une date de l'évènement d'état. À la modification, l'état
  fourni
  remplace l'état courant ; non fourni, l'état courant est conservé.
- **RG-PAT-006** — Type de patient dérivé de l'état : `VACANCIER` si l'état est `VACANCIER_LOCAL` ou
  `VACANCIER_ETRANGER`,
  sinon `NON_VACANCIER`.
- **RG-PAT-007** — Un patient **non vacancier** doit avoir une attestation de droits : à la création, dates de début et
  de fin
  d'attestation obligatoires ; à la modification, soit fournies, soit déjà existantes (« Attestation obligatoire pour un
  patient
  non-vacancier »). Un vacancier en est dispensé.
- **RG-PAT-008** — Un patient « en sommeil » (`enSommeil`) est conservé mais n'est plus considéré en file active active
  (voir
  indicateurs).
- **RG-PAT-009** — Téléphones (personnel, mobile, bureau) : normalisés et validés selon RG-TRV-034 (8 à 15 chiffres) ;
  email
  validé et mis en minuscules (RG-TRV-033) ; champ vide = absent. Un format invalide est refusé.
- **RG-PAT-010** — La fiche porte aussi : civilité, groupe sanguin, nombre d'enfants, lieu de naissance, situation
  familiale,
  profession, adresse, photo, observation, indicateurs « sous KT », EPO (activé + date) et fer (activé + date), pièces
  jointes.
- **RG-PAT-011** — Affectation clinique et logistique de la fiche : médecin traitant, salle, créneau (position),
  générateur,
  transporteurs aller/retour, catégorie de transport, centre payeur, et **jours de dialyse** (dimanche à samedi, cases à
  cocher). Un patient sans jour coché n'a pas de séance planifiée.
- **RG-PAT-012** — Le générateur affecté est un équipement du parc GMAO de type générateur de dialyse du même centre ;
  son nom,
  sa marque et son état (statut GMAO) sont lus en temps réel à la consultation de la fiche.
- **RG-PAT-013** — L'écriture de la fiche (création, modification, assurés) est refusée au médecin « seul » (il consulte
  sans
  modifier) ; autorisée à `ADMIN`, `SECRETAIRE`, `INFIRMIER` et aux rôles personnalisés.
- **RG-PAT-014** — Création ou modification : tout le placement (salle, créneau, générateur, jours) est contrôlé côté
  serveur par
  la planification (voir RG-PLN-050 à 053). Une fiche sans placement est acceptée ; une fiche dont le placement n'a pas
  changé
  n'est pas recontrôlée (une anomalie héritée ne bloque pas la modification d'autres données).
- **RG-PAT-015** — Création et modification notifient en temps réel le centre (évènements « patient créé/modifié ») et
  vident les
  caches de listes et de synthèse patient (RG-TRV-004/040).
- **RG-PAT-016** — Les affectations de jours/salles saisies dans l'assistant sont verrouillées côté interface
  lorsqu'elles
  dépendent du planning ; seul le planificateur les modifie (voir chapitre planification).

## 4.2 Liste, recherche, synthèse

- **RG-PAT-020** — La recherche de patients est paginée (taille 1 à 200, 20 par défaut), toujours limitée au centre, et
  filtrable
  par code, nom, prénom, sexe, période d'admission, numéro d'assurance, état, statut « non facturable » et, par texte du
  libellé
  affiché, médecin traitant, créneau, transporteurs aller/retour et forfait.
- **RG-PAT-021** — Tri autorisé uniquement sur code, nom, prénom, sexe, date d'admission, numéro d'assurance, état ; un
  tri demandé
  sur une autre colonne retombe sur l'ordre par défaut (jamais d'erreur, jamais de tri SQL arbitraire).
- **RG-PAT-022** — Un patient est **« non facturable »** tant qu'il n'a aucune prise en charge validée ; le statut et le
  forfait de
  sa dernière PEC validée apparaissent dans la liste.
- **RG-PAT-023** — Les libellés de référentiels (médecin, créneau, transporteurs, forfait) sont résolus par lot pour la
  page (pas de requête par ligne).
- **RG-PAT-024** — La synthèse mensuelle des patients (comptages par état, etc.) est calculée pour le mois demandé (mois
  courant par
  défaut), mise en cache **par centre et par mois** (3 min) ; ses détails de même.

## 4.3 Assurés (ayants droit)

- **RG-ASS-001** — Qualité d'assuré : `ASSURE_LUI_MEME` (défaut), `ENFANT`, `CONJOINT`, `ASCENDANT`, `AUTRE`.
- **RG-ASS-002** — Patient **assuré lui-même** : ses propres nom, prénom, sexe, date de naissance, téléphones, adresse
  et groupe
  sanguin tiennent lieu d'informations d'assuré, son numéro d'assurance est celui de l'assuré, et toute affectation
  d'assuré
  principale antérieure est effacée.
- **RG-ASS-003** — Patient **ayant droit** : le numéro d'assurance de l'assuré est obligatoire (« N° assurance assuré
  obligatoire
  pour cette qualité d'assuré ») ; l'assuré est créé ou mis à jour (clé : numéro d'assurance) avec ses coordonnées, puis
  affecté
  comme assuré **principal** du patient (l'ancien principal est clôturé).
- **RG-ASS-004** — Un même assuré peut être lié à plusieurs patients (fratrie, famille) ; son historique d'affectation
  est
  conservé et consultable (numéro, principal ou non, actif, dates).
- **RG-ASS-005** — Affecter explicitement un assuré existant à un patient : impossible si le patient est assuré lui-même
  (« Impossible
  d'affecter un assuré si le patient est assuré lui-même ») ; assuré inconnu : « Assuré introuvable » ; la date de début
  est **automatiquement aujourd'hui**, l'affectation principale précédente est clôturée à cette même date, la nouvelle
  est active (sans date
  de fin).
- **RG-ASS-006** — Correction des dates d'une affectation : la date de fin ne peut précéder la date de début (« La date
  de fin doit
  être >= à la date de début ») ; l'affectation doit appartenir au patient et au centre sinon 403.
- **RG-ASS-007** — Modification d'un assuré : l'assuré doit appartenir au centre (sinon 403) ; téléphones validés selon
  RG-TRV-034.
- **RG-ASS-008** — Une affectation est **active** tant qu'elle n'a pas de date de fin.

## 4.4 Attestation de droits

- **RG-ATT-001** — Une attestation couvre une période : dates de début et de fin obligatoires (« Dates attestation
  obligatoires »)
  et la fin ne peut précéder le début (« Date fin avant date debut »).
- **RG-ATT-002** — Une attestation est rattachée à un patient et à un centre ; plusieurs attestations successives sont
  conservées.
- **RG-ATT-003** — Une attestation est **valide** à une date donnée si cette date est comprise dans sa période ; sa
  validité à la
  date du jour conditionne la création de PEC et de séances pour un non-vacancier (RG-PEC-002, RG-PEC-005).
- **RG-ATT-004** — Création et suppression d'une attestation notifient le centre en temps réel. La recherche des
  attestations du
  centre est paginée.

## 4.5 Prise en charge (PEC)

- **RG-PEC-001** — Cycle de vie : `CREE` → `VALIDEE` → `CLOTUREE`. Toute autre transition est refusée (« Transition
  invalide vers
  VALIDEE » / « …vers CLOTUREE »).
- **RG-PEC-002** — Création : le patient doit exister dans le centre ; pour un **non-vacancier**, une attestation valide
  à la date du
  jour est obligatoire (« Attestation valide obligatoire pour PEC non-vacancier »). La PEC enregistre la période et le
  forfait **demandés**.
- **RG-PEC-003** — Validation (réservée à `ADMIN`) : renseigne la période et le forfait **effectifs** accordés ;
  possible
  uniquement depuis `CREE`. Elle notifie le centre.
- **RG-PEC-004** — Clôture : possible uniquement depuis `VALIDEE`. Elle notifie le centre.
- **RG-PEC-005** — Une séance ne peut être créée que si la PEC est **validée** et, pour un non-vacancier, si une
  attestation est valide à
  la date du jour (`session-allowed`).
- **RG-PEC-006** — Un patient n'ayant aucune PEC validée est « non facturable » (RG-PAT-022) : aucune séance ne peut lui
  être facturée.
- **RG-PEC-007** — Saisie dans l'assistant patient : une PEC (période et forfait demandés) et une attestation peuvent
  être créées en même
  temps que la fiche, dans la même transaction que le patient et l'assuré.
- **RG-PEC-008** — La suppression d'une PEC exige qu'elle existe dans le centre ; elle notifie le centre. La recherche
  des PEC du centre
  est paginée.
- **RG-PEC-010** — **Tableau de bord administratif du centre** (`/api/v1/dashboard/stats`, mois facultatif au format
  `AAAA-MM`, ignoré s'il est invalide) : nombre de PEC créées et validées (du mois
  par date de création, ou au total), d'attestations (idem), de patients, de **PEC validées dont la fin demandée est
  atteinte** et d' **attestations dont la fin est atteinte** dans l'horizon d'alerte (30 jours par défaut,
  paramétrable). **Point d'attention :** ces deux compteurs « à échéance » comptent aussi les documents **déjà expirés**
  de toute ancienneté (condition « date de fin ≤ horizon »), ce qui
  surestime le besoin de renouvellement ; à borner en bas par la date du jour.
- **Point d'attention (RG-PEC-009)** — `GET /api/v1/pec` (PEC du centre) et `GET /api/v1/pec/patient/{id}` renvoient des
  listes non
  paginées (volume borné par patient pour la seconde ; non borné pour la première) — écart à RG-TRV-020 à résorber au
  profit de la
  recherche paginée `POST /api/v1/pec/pec-center/search`.
