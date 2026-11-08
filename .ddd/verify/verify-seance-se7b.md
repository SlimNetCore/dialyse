---
status: proposed
date: 2026-11-08
sources: ADR 0095, commits 973b6a8a / e366602d / 3de40eea / 9eea6f83, backend/contexts/seance, SeanceRestController (3de40eea^), FinOccupation (bc-patient)
---

# Revue indépendante SE7b : tableau de bord mensuel des séances

Contrôles automatiques : `check-arch` 0 violation (0 baseline, 0 nouvelle). Aucune exécution de test Maven (lecture seule) : les verdicts "vert" ne sont pas revérifiés ici.

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy / confidentialité | Le contrôle de production de KB-SEA-66 est vide. La requête 2 de §60 compte les lectures de `/dashboard/details` dans `audit_log`, et la recette (d) affirme que ces lectures y figurent. Le filtre d'audit ne trace que les écritures et le GET `/api/v1/patients/{patientId}/dossier-medical`. Aucune lecture du détail nominatif n'est journalisée : la requête renvoie toujours 0 ligne, ce qui donne une fausse assurance au DPO. | `backend/platform/.../application/audit/AuditActionResolver.java:28-30,78-82`; `.ddd/migration/verifications-production.md:2243-2244,2252`; ADR 0095 section A, ligne KB-SEA-66 | Corriger la requête 2 et la recette (d) en "aucune trace aujourd'hui". Ajouter dans l'ADR que la décision (c), journaliser les lectures, suppose d'ajouter la route à `SENSITIVE_READS`. Ne pas présenter l'audit comme un contrôle existant. |
| minor | legacy / doc | RG-SEA-059 dit que le détail sert "nom, prénom et date de naissance". Le code, l'ADR et known-bugs servent nom, prénom, identifiant patient et présence ; la date de naissance n'est pas exposée. La doc surestime l'exposition et diverge de l'ADR. | `docs/reference/regles-de-gestion/07-seances.md` (RG-SEA-059, "KB-SEA-66"); `LigneDuDetail.java:12`; `TableauDeBordDesSeancesControleur.java:84-92` | Remplacer par "nom, prénom, identifiant du patient et présence". |
| minor | legacy / traçabilité | Numérotation KB croisée. known-bugs : KB-SEA-64 = compteurs ≠ détail + contamination de l'effectif, KB-SEA-65 = présent ET absent. ADR 0095, Javadoc `EffectifDuMois`, §60 : KB-SEA-64 = compteurs + présent/absent, KB-SEA-65 = contamination. Les conditions de production citent des numéros que le journal des bugs entend autrement : une décision pourrait viser le mauvais constat. | `.ddd/safety-net/known-bugs.md:1909-1910`; ADR 0095 lignes 21-22; `EffectifDuMois.java:14-17` | Aligner ADR, Javadoc, §60 et RG sur known-bugs (ou l'inverse), une seule numérotation. |
| minor | legacy | Recette (a) et ADR section B : "un centre étranger reçoit toujours 403". Faux si un paramètre n'est pas lisible : la liaison échoue avant `session.centre`, et l'advice ne teste que le rôle, pas le centre. `year=abc` avec un centre étranger donne 400, jamais 403. Pas de fuite de donnée ; seule la promesse est trop large. | `ErreursDeSeanceAdvice.java` (`illisible`, `parametreAbsent` : `droitsSuffisants` ne contrôle que `@PreAuthorize`); ADR 0095 ligne 28; `verifications-production.md:2250` | Reformuler : "profil sans droit : 403 avant 400 ; centre étranger : 403 si les paramètres sont lisibles". |
| minor | legacy (écart de jour) | Question 5 : écart réel. L'ancien `AbsencesDuPilotage.aujourdhui()` utilise le fuseau par défaut (`app.absences.fuseau-par-defaut`, repli Planification, Africa/Algiers). Le nouveau code utilise `HorlogeDuCentre`, branchée sur `JourDuCentreFourni` / `JourDuCentre` (fuseau de chaque centre). Les deux divergent pour un centre à fuseau propre, autour de minuit (jour échu et dernier jour compté). Documenté comme R3 (butoir 2027-01-31), mais ADR section C le classe "reproduit sans alerte" et KB-SEA-63 est donné comme seul écart. | `AbsencesDuPilotageJdbc.java:43-45`; `AbsencesPatientsContexteConfiguration.java:324-325`; `SeanceConfiguration.java:150-153`; `JourDuCentreFourniLegacy.java:20-22`; ADR 0095 lignes 36 et 56 | Ajouter la mention "écart de fuseau possible" en section A ou B de l'ADR. Pas de correctif de code requis tant que tous les centres partagent le fuseau par défaut. |
| minor | tests / doc | §60 requête 4 (`upper(trim(sexe)) NOT IN (...)`) ignore les sexes NULL, alors que le code les range dans AUTRE. Requête 6 compte des lignes de séance, pas des patients distincts, mais son alias dit "patients". | `verifications-production.md:2246,2248`; `Sexe.java:13-14` | `OR p.sexe IS NULL` ; `count(DISTINCT s.patient_id, s.date_seance)` n'existe pas en PostgreSQL, faire un sous-select distinct. |
| minor | archi / doc | Résidus mal numérotés. La Javadoc de l'ACL et la liste blanche `TableDuPatientCouvertureTest` nomment l'ACL des patients "R2". L'ADR 0095 l'appelle R1 (R2 = règle de libération). | `PatientsDuTableauDeBordJdbc.java:25-26`; `TableDuPatientCouvertureTest.java` (entrée ajoutée, "résidu R2"); ADR 0095 lignes 54-55 | Remplacer par "R1". |
| minor | docs | `docs/client/documentation-fonctionnelle.html` cite encore `SeanceRestController` (lignes 1895, 1908, 1990, 4662). Les fiches `.md` du référentiel et l'annexe 16 sont propres. | `docs/client/documentation-fonctionnelle.html:1895,1908,1990,4662` | Régénérer la doc fonctionnelle (consigne du projet), ce que le lot C n'a pas fait. |
| nit | tactique | `FinDeLaPriseEnCharge` copie `FinOccupation` du Patient à l'identique (vérifié : mêmes ensembles, même `minusDays(1)`, même `LocalDate.MIN`). Aucun test de parité entre les deux : une évolution de la règle Patient divergerait en silence jusqu'à R2. États en `String` (primitive obsession, hérité du legacy). | `FinDeLaPriseEnCharge.java:15-29` contre `bc-patient/.../FinOccupation.java:33-62`; `FormulesDuTableauDeBordTest.java:91-107` | Test de parité temporaire ou commentaire de rappel sur `FinOccupation`. |
| nit | tactique | Nom du fichier d'export (`String.format("%02d")`) construit dans le cas d'usage, et `weekday` / `scheduled: true` dans le contrôleur. Pas de règle métier, mais de la présentation hors adapter. | `ExporterLeTableauDeBord.java:33`; `TableauDeBordDesSeancesControleur.java:88-90` | Facultatif. |
| nit | archi | Le module `seance` dépend du jar `contexts-absences-patients` entier (transitivement planification, patient-couverture, facturation, comptabilité). Pas de cycle (aucun de ces modules ne dépend de `contexts-seance`) ; ArchUnit n'autorise que `absencespatients.publishedlanguage`. | `contexts/seance/pom.xml:95-100`; `ArchitectureHexagonaleTest.java` (+5 interdictions) | Acceptable ; noter dans l'ADR si un module `published-language` est prévu. |

## Réponses aux points demandés

1. **Hexagonal / langage / pom** : conforme. Domaine pur (java.time seulement, sans horloge), ports dans `application`, câblage dans `SeanceConfiguration`, ACL nommées, aucune lecture de table Absences. POI 5.2.5 et OpenHTMLtoPDF 1.0.10 identiques à `platform/pom.xml` (R6 daté).
2. **Formules** : fidélité vérifiée ligne à ligne contre `SeanceRestController` (3de40eea^).
   - Tranches d'âge : `ChronoUnit.YEARS` à la fin du mois, bornes 18/40/60, naissance nulle = INCONNU, naissance future = 0-17. Identique.
   - Sexe : trim, majuscules, M/H = M, F = F, sinon AUTRE. Identique.
   - Effectif : même ordre (programmés puis présents en `putIfAbsent`), même filtre sommeil / admission / `derniereDateAttendue(to)`. Identique.
   - Séances attendues : 7 clés françaises à 0 puis clés anglaises (14 clés), jours bloqués sautés, plafond au dernier jour échu. Identique.
   - Jours bloqués : union dédoublonnée des deux natures. Identique ; la tolérance aux erreurs est conservée, avec un WARN.
   - `FinDeLaPriseEnCharge` ne diffère pas du Patient (nit ci-dessus).
   - Tri : ordre naturel des chaînes, nom/prénom nuls ramenés à "". Identique.
   - Exports : CSV, XLSX et PDF identiques textuellement (feuilles, libellés, `%%` du CSS). Le test compare les fichiers décodés ; les octets PDF/XLSX ne sont pas comparables (métadonnées).
   - Février et années 0, 1, 9999 : `YearMonth` partout, bornes acceptées. Tests présents.
3. **Écarts** : les 5 écarts sont tous du type 500 vers 400 (KB-SEA-63). Les 4 goldens A4 sont archivés en R100 (`git diff --name-status`, 0 ligne modifiée). Leur contenu, retiré des CRLF, est identique octet pour octet à `git show 973b6a8a:<chemin>` (MD5 identiques sur les 4). Les 4 `*_nouveau_code.approved.json` portent le 400. Aucun autre golden A4 ou PC1 n'a changé. Ordre des contrôles respecté : centre, puis `kind` ou format, puis mois.
4. **Confidentialité** : KB-SEA-66 est en tête de l'ADR (section A, condition de production, décision métier / DPO). Droits non modifiés. Aucun montant dans les contrôleurs, exports ni ports. Le seul log ajouté (`JoursBloquesDuCalendrier`) ne porte ni donnée de santé ni message d'exception. Réserve : le contrôle d'audit est fictif (major ci-dessus), et le détail reste sans pagination.
5. **Jour du centre** : écart réel mais limité (minor ci-dessus).
6. **Tests / horloge** : aucun appel à une horloge système dans les tests SE7b (les mois sont fixes : 2025-12, 2026-01, 2026-02, 2024-02, 2099). PC1 `le_tableau_de_bord_de_la_seance_compte_l_effectif...` lit le mois courant : la valeur de `patientsConsidered` (4) ne change pas dans un mois grâce aux décalages ±100 jours. Risque faible, limité aux bascules de mois autour de minuit (fuseau du test et du pilotage) ; résidu R7 correctement daté.
7. **§60** : les 6 requêtes sont textuellement identiques à celles du script `seance-se7b-pg16-rejeu.sh` et à la numérotation de §60 ; SELECT seulement ; colonnes `audit_log.route_template/status_code` existent (V1 ligne 315-316). Défauts de sémantique : voir major et minor ci-dessus.
8. **Résidus R1 à R7** : revue 2026-12-15 et butoirs propres présents et cohérents (2027-01-31, 2027-03-31, 2027-06-30). Un ajout utile : le fuseau (R3) devrait mentionner l'écart décrit ici.
9. **Annexe 16 / fiches** : cohérentes (`TableauDeBordDesSeancesControleur`, RG-SEA-059). Plus aucune mention de `SeanceRestController` dans les `.md` du référentiel, hors Javadoc historiques et ADR antérieurs. Reste la doc HTML client.
10. **Liste blanche `TableDuPatientCouvertureTest`** : l'entrée `SeanceRestController` est retirée, une seule entrée ajoutée (l'ACL, datée, revue 2026-12-15, butoir 2027-03-31). Aucune entrée historique repoussée.

## Verdict : pass-with-actions

Aucun blocker. Le code est fidèle au legacy sur les formules et les écarts sont bornés à KB-SEA-63.

Top 3 actions
1. Corriger le contrôle d'audit fictif de KB-SEA-66 (requête 2, recette (d), ADR) et dire qu'aucune lecture du détail nominatif n'est tracée aujourd'hui.
2. Aligner la numérotation KB-SEA-64 / 65 (ADR, Javadoc, §60, RG) sur known-bugs, et corriger RG-SEA-059 ("date de naissance" faux).
3. Déclarer l'écart de fuseau (R3) dans l'ADR comme écart réel, reformuler la promesse "centre étranger = 403 toujours", et régénérer la doc fonctionnelle HTML.
