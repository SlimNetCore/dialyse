---
status: accepted (livrée le 2026-11-14 : deux lecteurs GMAO et KB-FAC-49)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: gmao (gmao.v1 : DescriptionsDesEquipements), patient-couverture (consommateur), facturation (consommateur de patientcouverture.v1)
related: ADR 0024, 0176, 0177, 0178, 0179, 0180 ; `.ddd/migration/reviews/lec-pat-gmao-realisation.md` ; `inventaire-platform-restant-v2.md` lignes 14 et 15
---

# 0181 - LEC-PAT-GMAO : Patient et couverture lit les équipements par gmao.v1 ; Facturation lit la fiche par patientcouverture.v1

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables. Un seul changement de comportement décidé (Décision 1).

## Décisions de l'utilisateur (2026-11-14)
1. **`LectureDesMouvementsJdbc`** : le code du générateur d'un mouvement vient de la lecture publiée gmao.v1, **BORNÉE AU CENTRE** du mouvement. CHANGEMENT DE COMPORTEMENT : un équipement d'un autre centre n'apparaît plus (le code est vide, comme un équipement inconnu). Avant : la jointure `LEFT JOIN gmao_equipements g ON g.id = m.generateur_id` n'avait aucune borne. Un équipement supprimé reste décrit (comme avant).
2. **`GenerateursDuCentreJdbc`** : le port dédié gmao.v1 décrit AUSSI les équipements supprimés (parité exacte avec la fiche actuelle), lecture par lot.
3. **`SituationsDesFacturesJdbc` (KB-FAC-49)** : tri départagé par l'identifiant de la facture comme dernier critère, puis lecture de `patients` isolée derrière `LotsDePatients.aFacturerDuCentre` (bornée au centre), appliquée en mémoire. À confirmer métiers/DPO <= 2026-12-15 pour le départage.

## Réalisation
- **gmao.v1** `DescriptionsDesEquipements.duCentre(centre, ids)` -> `Map<id, DescriptionDeLEquipement(id, code, fabricant, statut)>` ; tout type d'équipement, supprimés COMPRIS, borné au centre, lot vide sans accès base. Adapter `DescriptionsDesEquipementsJpa` (requête `descriptionsDuCentre` de `EquipementSpringData`), bean `gmaoDescriptionsDesEquipements`. `FichesDesGenerateurs` (LEC-PLN-GMAO, supprimés exclus) n'est pas modifié : deux ports, deux sémantiques.
- **Patient et couverture** : `GenerateursDuCentreJdbc` (acl/legacygmao) SUPPRIMÉ, remplacé par l'ACL `acl/gmao/GenerateursDuCentreParLaGmao` ; `LectureDesMouvementsJdbc` n'a plus de jointure sur les équipements (lit `m.generateur_id`, décrit le lot de la page par le port). Dépendance Maven explicite `contexts-gmao` (publishedlanguage seul, ArchUnit vert).
- **Facturation** : `SituationsDesFacturesJdbc` lit `factures` x `facture_reglements` (GROUP BY réduit aux colonnes de la facture), tri `date_facturation DESC, numero_facture DESC, id`, puis applique la fiche (numéro d'assurance, nom, prénom, centre payeur courant) par lots de 500 patients. COALESCE reproduits en mémoire : fiche, sinon instantané de la facture, sinon `-` ; centre payeur = instantané d'abord, sinon celui de la fiche.
- Gardes : `TableDesEquipementsDeLaGmaoTest` (2 entrées supprimées, liste blanche VIDE, test verrou) ; `TableDuPatientCouvertureTest` (entrée JOINTURE supprimée, plus que PLACE et CYCLE de la Planification, test verrou). Aucun report. `seamAllowed` = [].

## Constat : le départage de KB-FAC-49 n'a aucun effet observable
La table `factures` porte `UNIQUE (center_id, numero_facture)` (`schema.sql:1281`, V1) et la lecture est toujours bornée à un centre : deux factures de même date ET de même numéro ne peuvent pas coexister dans un résultat. Le tri d'avant était donc déjà total ; l'identifiant est un départage défensif. Aucune comparaison d'égalité n'est constructible (la base refuse la seconde facture) : le test `la_base_refuse_deux_factures_de_meme_numero…` verrouille ce fait au lieu d'un golden d'égalité. L'écart redouté (changement d'ordre du plan d'exécution) n'existe pas. Aucun écart de montant.

## Filet et parité
Ajoutés AVANT le changement, approuvés un par un depuis `.received`, sur le code d'avant : `pc1/mouvements/le_generateur_d_un_mouvement_est_dec/generateur_d_un_autre_centre` (autre centre, supprimé, inconnu, nul), `pc1/consultation/la_fiche_d_un_patient_dont_le_genera/fiche_generateur_d_un_autre_centre` et `fiche_generateur_inconnu` (la fiche est déjà bornée : parité), `facturation/reglements/recherche/patient_d_un_autre_centre_ou_inconnu` et `..._compact`.
Seul écart : `generateur_d_un_autre_centre` (« G-AUTRE-CENTRE » devient `null`), golden renommé `generateur_d_un_autre_centre_nouveau_code.approved.json`, ancien état archivé `_ecarts-legacy/mouvements/le_generateur_d_un_mouvement_est_dec/generateur_d_un_autre_centre_avant_lec_pat_gmao.legacy.json`, KB-PAT-13 (journal des mouvements) annoté « CORRIGÉ (2026-11-14) ». Tous les autres goldens (mouvements x8, fiche x générateur supprimé et hors service, règlements, tableau de bord et export, situation d'une facture) inchangés byte-à-byte.
Contrats : gmao `PilotageContrat` +1 cas sur le double, JPA/H2 et PostgreSQL 16 (IT ignoré sans URL jetable) ; Patient et couverture : `EquipementsEnMemoire` (double borné), `GenerateursDuCentreParLaGmaoTest` (3), `PatientsJdbcContrat` +1 ; Facturation `ReglementsJdbcContrat` +1 (H2, PG16).

## Reproduits ou limites, à confirmer métiers/DPO <= 2026-12-15
Le départage par identifiant (Décision 3). Coût : la fiche est lue par lots de 500 identifiants distincts de patients par appel de la liste (avant : une jointure) ; sans effet sur le résultat.
