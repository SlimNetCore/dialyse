---
status: proposed
date: 2026-11-12
sources: pl5-filet.md, doc1-realisation.md, pl4-realisation.md, ADR 0140/0142/0144, known-bugs.md (KB-PL5-1..6)
---

# PL5 - realisation

Impression du cahier de dialyse (`GET /api/v1/cahier-dialyse/{patientId}/imprimer`) deplacee de `backend/platform` vers `backend/contexts/seance`, rendue par Documents (`RenduDeDocument`, documents.v1). Refactoring sur place (ADR 0024) : memes URL, roles, en-tetes, codes. Decisions : ADR 0144. Aucun flag, rien commite.

## Cree (contexts/seance)
- domain : `CahierRefuse`, `PeriodeDuCahier`
- application : `ImprimerLeCahierDeDialyse`, `LigneDIdentiteDuCahier`, ports `SeancesDuCahier` et `RenduDuCahier`, `DemandeDeCahier`, `CahierImprime` ; `SessionCourante.peutLireLeVoletMedical()`
- infrastructure : `acl/documents/CahierRenduParDocuments` ; `SessionLegacy` (portee du volet medical)
- interfaces/http : `CahierDeDialyseControleur` (erreurs via `ErreursDeSeanceAdvice`) ; configuration : 3 beans ; pom : dependance `contexts-documents`
- tests : `ImprimerLeCahierDeDialyseTest` (7), `CahierRenduParDocumentsTest` (5), `CahierDeDialyseControleurTest` (5), ArchUnit hexagonal (10)

## Plateforme
- Supprimes (git rm) : `CahierDialyseRestController`, `CahierDialyseReportService` + 2 tests ; retires de `covered.json`.
- Modifies (couverts) : `AuditActionResolver` (`CAHIER_DIALYSE_IMPRESSION`), `DatesIllisiblesDeLaDirectionAdvice` (405 sur le prefixe cahier), `reports/cahier_dialyse.jrxml`.
- jrxml : parametre `VOLET_MEDICAL_VISIBLE` (defaut `true`) ; jointures medicales neutralisees et chaque element medical masque par `printWhenExpression` (le validateur JRXML n'est PAS elargi) ; consommables nets des ENTREE/AJUSTEMENT ; virgule decimale ; identite sur 2 lignes (en-tete 109) ; Prix/DA conditionnels.
- Tests : `FrontieresDesContextesTest` (regle interdisant le retour des 2 classes + regle d'usage de `RenduDeDocument`), `TableDesSeancesTest` (5 fichiers), `CahierDialyseReportIntegrationTest` (virgule, test du volet medical masque).

## Parite et goldens
8 scenarios PL5 + 3 SE7e rejoues contre le nouveau code. Ecarts approuves un a un (relus en diff `.received`), uniquement les ecarts decides : 400/405, 404 (message « Patient introuvable » conserve), virgule decimale, identite sur 2 lignes, « Prix/DA » conditionnel, net des retours, action d'audit, volet medical masque pour la secretaire (cas 02 du golden `complet` : `true` -> `false`).

| Golden | Decision |
|---|---|
| PL5 `complet`, `volets_et_dossier`, `consommables_prescription_forfait_absent`, `garde_de_centre`, `parametres`, `audit` | `*_nouveau_code.approved.json` ; anciens archives `pl5/cahier/_ecarts-legacy/*_avant_pl5.legacy.json` |
| SE7e `statuts_bornes_et_egalite`, `refus`, `surcharge_de_forfait` | idem, archives `se7e/_ecarts-legacy/cahier/*_avant_pl5.legacy.json` |
| PL5 `droits_par_profil`, `etancheite_des_centres` | inchanges |

## Ecarts reproduits
KB-PL5-6 (a) valeur de volet faite de tabulation/saut de ligne imprimee vide, (d) prescription a dose sans article/frequence. Pas d'ecart d'affichage non decide.

## Observations
- Lignes de consommables a quantite nette negative (retour superieur aux sorties au meme prix) : imprimees telles quelles (fixture PL5 : gants -7, zebre -8). A confirmer.
- L'audit journalise aussi la reponse 400 d'une entree illisible.

## Questions ouvertes (reprises dans l'ADR 0144)
- Modele personnalise par un centre : pas de masque du volet medical, la secretaire y lit encore ce volet.
- AJUSTEMENT (signe) et quantite nette nulle/negative : regle a confirmer avec le Stock/les metiers.
- Journalisation des refus 422/404 non verifiee (200 et 400 observes).
- Matrice role x volet medical (KB-PL5-4) : metiers/DPO avant le 2026-12-15.
- Le jrxml et le moteur Jasper restent en plateforme jusqu'a DOC2.
- Doc fonctionnelle et maquette non regenerees.

## Bascule
Aucun flag (ADR 0024). Retour arriere = revert du commit.
