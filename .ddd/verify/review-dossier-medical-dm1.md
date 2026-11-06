---
status: proposed
date: 2026-11-06
sources: [git 542c7571..24b1021c, ADR 0080, seams.md, known-bugs.md KB-MED-01..14]
---
# Revue indépendante DM1 (constantes) — verdict : pass-with-actions

check-arch : 0 violation. Aucun build ni test lancé (lecture seule) : la verdeur des suites n'est pas vérifiée par cette revue.

| Sév. | Domaine | Constat | Preuve | Correction |
|---|---|---|---|---|
| major | legacy/sécurité | Routes de statistiques et d'export médicaux SANS garde de centre : `stats/medical` (GET + POST search), `stats/export` (GET + POST search, ADMIN, CSV/PDF contenant poids avant/après, poids sec, Hb). Le détail DM8 est documenté, mais la fuite inter-centres de données de santé reste ouverte. | `PatientStatsRestController.java:50-70` (medical), `:72-110` (export) ; `PatientStatsQueryService.java:96,120,294-304` | Ajouter `requireCenter` (même seam) avant DM8, ou assumer par écrit le risque dans l'ADR 0080. |
| major | legacy/sécurité | Autres routes qui renvoient des données de séance sans garde : `GET /seances` (liste), `GET /seances/journal`, `GET /seances/calendar`, `PUT /seances/{id}` (ADMIN, `request.centerId()`), `POST /seances` (centerId du corps). `SeanceStationRestController` est gardé (`:55`). Cahier, FHIR, KDIGO gardés. | `SeanceRestController.java:115,171,465,565,673` | Inventorier dans KB-MED-02 (périmètre Séance, SE*) ; ne pas laisser croire que le périmètre « constantes » est clos. |
| minor | legacy | `PatientStatsRestController` absent de `covered.json` (avant et après), alors que seams.md dit « reste couvert ». Le hook bloquera la prochaine édition (DM8) sans `seamAllowed`. | `.ddd/safety-net/seams.md` section DM1 ; grep `covered.json` : 0 occurrence | Corriger la phrase de seams.md ou inscrire le fichier avec son filet. |
| minor | legacy | Entrées mortes dans `covered.json` : 3 fichiers supprimés. | `.ddd/safety-net/covered.json:106-108` | Les retirer. |
| minor | stratégique/doc | Le tri `id DESC` est un ajout (départage), présenté « reproduit » dans l'ADR 0080 (§5, ligne 28). Ce n'est pas un écart de contenu mais ce n'est pas du legacy ; pas listé comme écart décidé. | `HistoriqueDesConstantesJdbc.java:ORDER BY ... s.id DESC` ; ADR 0080:28 | Le déclarer comme écart bénin (déterminisme) dans l'ADR et la fiche. |
| minor | tactique | Lecture pure sans agrégat : acceptable (VO `Constantes`, `Pagination`, `PatientId`, `SeanceId` validés ; port `HistoriqueDesConstantes` nommé par le métier, adapter JDBC, ACL `SessionLegacy` par réflexion sur `getCenterId`). La réflexion est fragile (renommage du principal = 403 généralisé). | `SessionLegacy.java:44-60` | Test d'intégration qui échoue si le principal change (présent via goldens) ; retrait daté avec Identité. |
| minor | legacy | POST `stats/paramedical/search` : `requireCenter` résout le centre mais le `centerId` du corps (peut être null) est passé tel quel ; aucune fuite (null = rien), mais le centre résolu est ignoré. | `PatientStatsRestController.java:49-50` | Utiliser la valeur retournée. |
| minor | archi | Count + page sans transaction commune (non atomique) ; comme le legacy Spring Data, lecture seule, sans incidence. | `HistoriqueDesConstantesJdbc.dePatient` | Aucune. |
| nit | doc | `target/` du module contient du bytecode périmé (non versionné). | `backend/contexts/dossier-medical/target` | `mvn clean` avant build. |

## Vérifications positives (preuve)
- Parité : les 8 archives `_ecarts-legacy/**/*.legacy.json` sont des renommages R100 (octet pour octet) des anciens approved (`git diff --name-status 542c7571 HEAD`). Seuls 8 `*_nouveau_code.approved.json` ajoutés ; AUCUN autre golden modifié (Séance, Stock, Facturation, Planification, Absences, PC inchangés ; les 4 goldens historiques `dossiermedical/constantes` aussi). Les `_nouveau_code` ne contiennent que les écarts décidés (400 bornes/illisibles, 403 inter-centres détail/stats/écriture, ADMIN sans centre 403).
- JSON de la lecture : champs, noms et enveloppe `items/total/page/size` identiques (`PageDeConstantesReponse.java`, `ConstanteSeanceResponse` supprimé), JOIN `seance_id` ET `center_id` = `findBySeanceIdAndCenterId`. Tri legacy `dateSeance DESC, createdAt DESC` conservé.
- `@PreAuthorize` identiques : `hasAnyRole('MEDECIN','ADMIN')` (legacy `ConstantesRestController` supprimé, ancienne route reprise sur la même URL et le même nom de variable `patientId`, utile à l'audit).
- `PUT volet-paramedical` : `requireCenter(request.centerId())` suffit car le use case scope la séance au centre du corps (golden : séance B + corps A = 400 « introuvable », corps B = 403). Non testé ici : SUPERADMIN bloqué en amont par le rôle (`@PreAuthorize`).
- Suppression : seuls 3 fichiers legacy supprimés hors du module ; `bc-medical` et les 14 entités médicales non touchés ; 132+ fichiers supprimés limités à `backend/contexts/dossier-medical`. `seamAllowed` vide. Réacteur, `platform/pom.xml`, Dockerfile cohérents.
- Pas de `*.received.json`, `.bak`, `.orig` versionnés ; aucune horloge dans les tests DM1 ; arbre de travail propre.
- Docs : ADR 0080 `accepted`, fiches DM2-DM8 annotées « Découpage réel » (planned, sans filet), KB-MED statuts tracés, RG-MED-100/103 et annexe 16 à jour, §45 verifications-production en SELECT seul, glossaire « Constantes » `proposed`, GEL.md « levée partielle ».

## Audit / confidentialité
Lecture des constantes non auditée (KB-MED-04, question DPO ouverte) ; seule la lecture `dossier-medical` l'est. Diffusion nom/prénom patient (KB-MED-05) reproduite. Les réponses 403 n'exposent pas de donnée de santé.

## État proposé : DM1 = `built` confirmé, avec réserve
`built` pour la lecture des constantes et les 3 gardes minimales ; NE PAS passer `verified/done` tant que : (a) suites non rejouées par cette revue (à lancer par l'utilisateur), (b) contrôles de production §45 non exécutés, (c) décisions DPO/métiers non prises.

## Top 3 actions
1. Décider du sort des gardes manquantes (`stats/medical`, `stats/export`, `GET /seances`, `journal`, `calendar`, `PUT /seances/{id}`) : les poser maintenant (seam) ou les inscrire avec échéance DM8/SE*.
2. Rejouer les suites (caractérisation DM1 + Séance/Stock/Facturation, contrat PG16 `dossier-medical-dm1-pg16-rejeu.sh`, ArchUnit) puis nettoyer `covered.json` (106-108).
3. Faire confirmer par DPO/métiers : audit des lectures (KB-MED-04), droits asymétriques MEDECIN/INFIRMIER (KB-MED-08), plafond size 100, 403 inter-centres.

## Ordre des contrôles de production
1. §45 req. 2 et 3 (volets inter-centres/orphelins) AVANT déploiement ; 2. req. 1 et 5 ; 3. déploiement ; 4. surveiller 403 (`PUT volet`, `GET /seances/{id}`, `stats/paramedical`) et 400 (`size>100`) pendant la période d'observation ; 5. contrôle front : tous les appels envoient le `centerId` de la session.
