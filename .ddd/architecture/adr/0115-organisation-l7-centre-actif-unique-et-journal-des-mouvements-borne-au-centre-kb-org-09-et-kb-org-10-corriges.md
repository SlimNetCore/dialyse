---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande de corriger les anomalies reproduites pendant la migration, 2026-11-09), architecte]
context-scope: organisation (publishedlanguage), stock, patient-couverture, plateforme (entité des centres, migration V56)
related: ADR 0108 (amendé : variante stricte retirée), 0109 (amendé : lecture bornée), 0107, 0111, 0024 et 0075 (refactoring sur place) ; `.ddd/migration/corrections-kb-2026-11-09.md` §6, `.ddd/safety-net/known-bugs.md` (KB-ORG-09, KB-ORG-10), `.ddd/migration/verifications-production.md` (§72, §73)
---

# 0115 - Organisation L7 : un seul « centre actif » (KB-ORG-09) et journal des mouvements borné au centre (KB-ORG-10)

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement (demande de l'utilisateur du 2026-11-09). Refactoring sur place : mêmes
tables, mêmes URL, mêmes rôles ; ni façade, ni flag, ni projection. À confirmer par les métiers / DPO au plus tard le **2026-12-15**.

## KB-ORG-09 : décision et règles
Cause supprimée dans la donnée, pas dans le code.
1. **Migration `V56__centers_actif_not_null.sql`** (dernière version réelle V55) : `UPDATE centers SET actif = TRUE WHERE actif IS NULL` PUIS
   `ALTER COLUMN actif SET DEFAULT TRUE` et `SET NOT NULL`. L'UPDATE précède la contrainte : aucune ligne ne peut faire échouer le démarrage.
   `TRUE` parce que tous les consommateurs sauf le Stock lisaient NULL comme actif.
2. **`AnnuaireDesCentres.actifsAuSensStrict()` retiré** du langage publié `organisation.v1` et de l'implémentation JDBC ; le port fourni au Stock
   `CentresFournis.centresActifsAuSensStrict()` devient `centresActifs()`, traduit par `CentresDuStockParOrganisation` vers `actifs()`.
   Un seul consommateur de la variante stricte (le Stock) : suppression dans le même lot.
3. **Effet comportemental unique** : un centre sans état ENTRE dans le contrôle de nuit des péremptions du Stock (notifications nouvelles
   possibles). Nul si §72.1 rend `nuls = 0` ; le SELECT de contrôle est à exécuter AVANT le déploiement.
4. **`CentresJdbc`** (Organisation, `getBoolean` : NULL = faux, O4-R4) : rendu équivalent par la contrainte NOT NULL ; documenté, code inchangé.
   `actifs()` garde `COALESCE(actif, TRUE)` (défense en profondeur sans coût).
5. **H2** : la base de test de la plateforme est bâtie par Hibernate (pas de `db/schema.sql` pour `centers`, pas de Flyway) : `CenterJpaEntity.actif`
   porte `nullable = false`, `columnDefinition = "boolean default true"` et la valeur initiale `TRUE` (un centre créé sans état est actif).
   Les tables de test minimales des contrats de modules gardent leur colonne nullable à dessein (elles décrivent les lignes anciennes).
6. Tests : `CentreSansEtatPrendLeDefautIntegrationTest` (défaut et NOT NULL sous H2), `AnnuairesContrat.il_n_existe_qu_une_definition_…`,
   `CentresActifsFournisTest`, `CentresDuStockParOrganisationTest` (rouges avant, verts après).

## KB-ORG-10 : décision et preuves du transfert de patient
**Préalable lu avant de borner.** Le journal `mouvement_patient` n'est écrit que par deux chemins, tous deux dans le centre du patient :
- `Patient.tracer` (`patient-couverture/.../domain/Patient.java:169-173`) : `new MouvementDuPatient(…, id, centre, type, …, a.salleId(), a.creneauId(),
  a.generateurId(), …)` avec le `centre` du patient et la place de SA fiche (`Affectation`, contrôlée et écrite par la Planification) ; persisté par
  `MouvementsJdbc.ajouter` (`…/MouvementsJdbc.java:20-25`, `center_id = m.centre()`).
- `PLACE_LIBEREE` (`planification/.../acl/legacypatient/MouvementsDesPatientsJdbc.java:53-58`) : `center_id = l.centre()`, place quittée du même centre.
- Le type `TRANSFERT` (`TypeDeMouvement.java:8`, `EtatDuPatient.java:17`) est la SORTIE d'un patient (état `TRANSFERE`, RG-PAT-032) tracée dans le
  centre de départ avec la place qu'il quitte : même centre, aucune écriture dans le centre d'arrivée.
- Le transfert entre centres de la mémoire de projet (copie clinique vers la destination, archive en lecture seule, `PatientDataCopier`,
  `/archives/transfers`) n'existe PAS dans ce dépôt : aucune classe, aucun SQL, absent de tout l'historique git (`git log --all -S PatientDataCopier` : vide).
  Les seuls « transferts » du code d'Organisation (`SocietesControleur`, `TransfertCentreRequete`) déplacent un CENTRE entre sociétés, pas un patient.
Conclusion : aucun chemin du code ne pose légitimement un mouvement dans le centre B avec la salle ou le créneau de A. Borner n'efface donc pas
d'information historique légitime produite par le code actuel. **Décision : borner** (`nomsDesSalles(centre, salles)`, `libellesDesCreneaux(centre,
creneaux)`, `AND center_id = :centre`), les anciennes signatures supprimées dans le même lot (un seul consommateur, `LectureDesMouvementsJdbc`, vérifié par
`Grep`). Une salle ou un créneau d'un autre centre rend un libellé vide, comme un libellé disparu ; l'identifiant reste dans la ligne.

**Résiduel nominatif** : les mouvements déjà en base issus d'un éventuel transfert fait hors de ce code (autre version, import) perdraient leur libellé :
c'est exactement ce que compte le SELECT §73.1, à exécuter AVANT le déploiement et à joindre à la décision métiers/DPO ; si des lignes légitimes existent,
la reprise correcte est de remapper l'identifiant (ou de figer le libellé dans le mouvement), pas de rouvrir la lecture non bornée. Le futur module de
transfert devra écrire dans le centre d'arrivée des identifiants du centre d'arrivée.

## Conséquences
- Langage publié `organisation.v1` : une méthode retirée (`actifsAuSensStrict`), deux signatures remplacées. Pas de contrainte inter-tables (elle exigerait un trigger).
- Goldens : AUCUN ne change (Stock, PC1 et Organisation rejoués : aucune ligne de golden ne portait un centre sans état ni une salle étrangère).
- Frontend : colonne salle ou créneau vide pour les lignes concernées (aucune connue). Documentation fonctionnelle HTML : à relire si une ligne concernée est constatée.
- Retrait de l'ancien comportement : ADR 0108 (variante stricte) et 0109 (lecture non bornée) portent une note de renvoi vers ce document.
