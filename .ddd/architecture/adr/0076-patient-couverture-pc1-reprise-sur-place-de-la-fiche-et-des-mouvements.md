---
status: proposed
date: 2026-11-03
decision-makers: [utilisateur (décisions du 2026-11-02, déléguées à l'assistant), architecte]
context-scope: patient-couverture
related: ADR 0024 (refactoring sur place), 0052 (la place est écrite par Planification), 0061 (ordre global des verrous), 0064, 0073 (gel du module, levé pour PC1) ; `.ddd/migration/slices/patient-couverture-pc1-identite-mouvements.md` ; `.ddd/safety-net/patient-couverture-pc1-identite-mouvements.md` ; `.ddd/safety-net/known-bugs.md` KB-PAT-01 à 19
---

# 0076 - Patient et couverture, PC1 : reprise sur place de la fiche et des mouvements

## Contexte
Le gel du module parallèle (ADR 0073) prévoyait la reprise sur place : mêmes tables, mêmes URL, vocabulaire du legacy. Le filet de caractérisation PC1 (56 scénarios, 128 goldens, commité vert sur le legacy en 321275cb) a verrouillé le comportement de `PatientRestController`, `PatientApplicationService`, `MouvementPatientRestController` et de leurs lecteurs. Les décisions du 2026-11-02 (déléguées) fixent les écarts de comportement acceptés.

## Décision
1. **Le module `contexts/patient-couverture` est repris** (réécrit, rien de repris tel quel du module gelé hors le squelette) et remis au réacteur, au `Dockerfile` et à `platform/pom.xml`. Mêmes tables (`patients`, `mouvement_patient`), mêmes URL (`/api/v1/patients/**`, `/api/v1/mouvements-patients`), mêmes noms de variables de chemin (le journal d'audit en dépend). La place reste écrite par la Planification (ADR 0052) via ports publiés (`PlacementDesPatients`, `LiberationDesPlaces`), jamais par la fiche.
2. **Vocabulaire du legacy conservé** dans l'API et les données (`TRANSFERE`, `GUERRI`, types `TRANSFERT`, `GUERISON`), mappé vers les termes du glossaire dans le domaine et la documentation. États en **liste fermée** (`BIDON` refusé 422 `PATIENT_ETAT_INCONNU`) ; transitions permissives reproduites, y compris depuis `DECEDE` (**à décider**).
3. **Date d'évènement obligatoire** pour un état de sortie ET pour un séjour limité (422) ; naissance future ou postérieure à l'admission refusée (422).
4. **KB-PAT-05** : corriger la seule date d'un état de sortie déjà enregistré ne crée pas un second mouvement ; le changement d'état, ou de la date de fin d'un séjour limité, en crée un.
5. **Numéro d'assurance** normalisé (retrait des espaces de bord, majuscules), unique par centre à la création et à la modification, contrôlé sous le verrou du centre (ordre global centre, fiches, app_user ; `lock_timeout` posé à l'entrée de l'unité de travail ; refus 409). Pas de contrainte SQL : les doublons existants se relèvent par la requête de `verifications-production.md` §41 ; un index unique n'est proposé qu'après nettoyage.
6. **Garde de centre** sur toutes les lectures et les écritures d'assuré (centre du jeton, 403 sinon) ; rôles inconnus ou absents refusés 403 ; droits des rôles connus inchangés (lecture `ADMIN`/`SECRETAIRE`/`MEDECIN`/`INFIRMIER`, écriture sans `MEDECIN` seul). 403 avant lecture du corps.
7. **Entrées illisibles ou manquantes : 400 `BAD_REQUEST`** (au lieu de 500), patient inconnu 404, taille de page de 1 à 100.
8. **« Aujourd'hui » = le jour du centre** partout (`affecterAssure` compris), clé `app.planification.fuseau-par-defaut`.
9. **DTO de réponse** pour la fiche, JSON identique champ par champ (identifiants et numéro d'assurance en `{"value": …}`). `piecesJointesJson` reste nul à la lecture, comme aujourd'hui (KB-PAT-19, reproduit, à décider).
10. **Ligne héritée aux colonnes nulles** : l'état nul vaut `PERMANENT`, les indicateurs nuls valent faux ; la lecture reste possible et le `PUT` ne plante plus.
11. **Couverture, assuré, attestation, PEC** : routes et règles inchangées, portées par des ACL JDBC temporaires vers les tables actuelles (`infrastructure/acl/legacycouverture`, `persistence/AssuresJdbc`) jusqu'à PC2, PC3 et PC4.
12. **Retrait du legacy** : contrôleurs, services, adapters, ports et DTO de la fiche supprimés (liste dans `seams.md`) ; modèles et entités historiques conservés (lus par d'autres contextes).

## Alternatives écartées
- Garder le module gelé tel quel : URL, tables et états inventés, aucune parité (ADR 0073).
- Façade et bascule progressive par drapeau : écartée pour ce refactoring sur place (mêmes tables, mêmes URL) ; le filet de caractérisation est la garantie de parité.

## Conséquences
- 24 scénarios PC1, 1 PC2 et 1 P5 changent d'issue, tous par décision ; l'ancien comportement est archivé en `_ecarts-legacy/**/*.legacy.json`, les nouveaux goldens portent le suffixe `_nouveau_code`.
- Dette consignée : sources gelées Assure/Pec/Attestation toujours exclues de la compilation du module (retrait à PC2 à PC4), ACL de couverture temporaires, lecteurs SQL hors module (liste blanche datée du filet PC1) à remplacer par le langage publié à PC5, `TableDuPatientCouvertureTest` toujours désactivé.
- Points à valider par les métiers : transitions d'état depuis `DECEDE`, `GREFFE`, `GUERRI` ; `piecesJointesJson` jamais relu.
