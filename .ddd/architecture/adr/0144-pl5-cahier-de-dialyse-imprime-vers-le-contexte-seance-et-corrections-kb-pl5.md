---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: seance, documents, platform
related: ADR 0024, 0099, 0140, 0142 ; `.ddd/migration/reviews/pl5-filet.md` ; `.ddd/safety-net/known-bugs.md` KB-PL5-1 à 6 ; `.ddd/migration/reviews/pl5-realisation.md`
---

# 0144 - PL5 : l'impression du cahier de dialyse passe au contexte Séance, rendue par Documents ; corrections KB-PL5-1, 3, 4, 5, 6

Refactoring sur place (ADR 0024) : mêmes tables, même URL `GET /api/v1/cahier-dialyse/{patientId}/imprimer`, mêmes rôles (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER), mêmes en-têtes de réponse. Pas de façade ni de flag ; retour arrière = revert du commit.

## Décision de placement
| Élément | Où |
|---|---|
| `CahierDialyseRestController`, `CahierDialyseReportService` (plateforme) | supprimés ; retour interdit par ArchUnit (`FrontieresDesContextesTest`) |
| Domaine Séance | `PeriodeDuCahier`, `CahierRefuse` (`CAHIER_VIDE`, `CAHIER_PERIODE_INVALIDE`) |
| Application Séance | `ImprimerLeCahierDeDialyse` ; ports `SeancesDuCahier`, `RenduDuCahier` ; `SessionCourante.peutLireLeVoletMedical()` |
| Adapters | `CahierRenduParDocuments` (ACL vers `RenduDeDocument`, documents.v1, type `CAHIER_DIALYSE`), `CahierDeDialyseControleur`, erreurs par `ErreursDeSeanceAdvice` |
| Gabarit `reports/cahier_dialyse.jrxml` et moteur Jasper | RESTENT en plateforme (comme les autres modèles et leurs tests) ; migrent avec le moteur en DOC2 |

## Décisions de conception
- D1. Le modèle de document est PERSONNALISABLE par centre : sa requête embarquée lit séances, volets, consommables, forfait et prescription (ADR 0099 décision 3). La Séance fournit le compte (refus `CAHIER_VIDE`), l'identité du patient (fiche publiée) et les paramètres ; elle ne duplique pas la requête en ports Java.
- D2. Ordre des gardes : centre de la session (403), période (422), patient du centre (404, message historique « Patient introuvable » conservé), cahier vide (422).
- D3. Volet médical : paramètre `VOLET_MEDICAL_VISIBLE` posé par la Séance (vrai pour ADMIN, MEDECIN, INFIRMIER ; faux pour une secrétaire seule). Le gabarit neutralise les jointures médicales et masque chaque élément médical par `printWhenExpression` (le validateur de JRXML, liste blanche, n'est pas élargi).
- D4. Journalisation : `GET .../imprimer` est une lecture sensible tracée par la plateforme sous l'action `CAHIER_DIALYSE_IMPRESSION` (`AuditActionResolver`), comme `STATISTIQUES_PATIENT_CONSULTATION`. Constaté : la réponse 400 d'une entrée illisible est aussi journalisée.

## Écarts décidés par l'utilisateur (2026-11-12)
| KB | Décision |
|---|---|
| KB-PL5-1 | Corrigé : entrées illisibles (`patientId`, `centerId`, `from`, `to`) 400 `BAD_REQUEST` ; mauvais verbe 405 `METHOD_NOT_ALLOWED` (préfixe ajouté à l'advice de la plateforme) ; 403 avant 400 |
| KB-PL5-2 | Corrigé : la ligne d'identité s'imprime sur deux lignes (zone 539 x 26, en-tête 109) |
| KB-PL5-3 | Corrigé : impression journalisée (action de lecture dédiée) |
| KB-PL5-4 | Corrigé : la secrétaire seule ne voit plus le volet médical (hépatites, conclusions, prescription, volet de séance) ; DIRECTION et SUPERADMIN restent 403 `ROLE_SCOPE`. Question ouverte : à confirmer métiers/DPO avant le 2026-12-15 |
| KB-PL5-5 | Corrigé : ENTREE et AJUSTEMENT rattachés à la séance réduisent quantité et valorisation (net des retours, regroupement article + prix inchangé). À confirmer métiers |
| KB-PL5-6 | (b) et (c) corrigés : « Prix : x DA » imprimé seulement si le prix existe ; point décimal remplacé par la virgule (poids, ultrafiltration, poids sec). (e) corrigé : `PATIENT_INTROUVABLE` 404. (a) et (d) reproduits (dette d'affichage) |

## Conséquences
- Goldens modifiés par décision, relus un à un depuis `.received` : PL5 `complet`, `volets_et_dossier`, `consommables_prescription_forfait_absent`, `garde_de_centre`, `parametres`, `audit` ; SE7e `statuts_bornes_et_egalite`, `refus`, `surcharge_de_forfait`. Nouveaux : `*_nouveau_code.approved.json` ; anciens archivés en `_ecarts-legacy/**/*_avant_pl5.legacy.json`.
- Les lignes de consommables peuvent avoir une quantité nette négative (retour supérieur aux sorties de l'article au même prix) : imprimées telles quelles.

## Questions ouvertes
- Modèle personnalisé par un centre (version active en base) : sa requête n'a pas le masque `VOLET_MEDICAL_VISIBLE`, la secrétaire y lit encore le volet médical. À traiter (imposer le modèle d'origine à la secrétaire, ou re-valider les versions).
- AJUSTEMENT (signe réel) et quantité nette nulle ou négative : règle à confirmer avec le Stock et les métiers.
- Journalisation des refus 422 et 404 : seuls les statuts effectivement observés par le filtre d'audit sont garantis (200, 400).
- Matrice rôle x volet médical (KB-PL5-4), avant le 2026-12-15.
