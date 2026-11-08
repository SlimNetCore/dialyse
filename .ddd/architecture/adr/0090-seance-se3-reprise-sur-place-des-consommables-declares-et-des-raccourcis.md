---
status: accepted
date: 2026-11-08
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance
related: ADR 0002, 0024 (refactoring sur place), 0045 (sortie pour séances), 0062, 0088 (SE1), 0089 (SE2, même démarche) ; `.ddd/safety-net/seance-se3-consommables.md` (net abdadcc5, 28 scénarios, 28 goldens), `.ddd/migration/slices/seance-se3-consommables.md`, KB-SEA-22 à 34
---

# 0090 - Séance, SE3 : reprise sur place des consommables déclarés et des raccourcis de consommables

## RISQUES À FAIRE CONFIRMER AVANT LA MISE EN PRODUCTION (décisions REPRODUITES, conditions de mise en production)
Toutes les décisions de cet ADR sont prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO** (données de santé, traçabilité des consommations, stock).
1. **KB-SEA-33 : l'auteur de la consommation n'est enregistré nulle part (RISQUE MÉDICO-LÉGAL).** L'adapter du Stock (`ConsommationsDeSeanceDuStock`, lignes 35 à 52) ignore le paramètre `auteur` : l'ajout transmet le compte de la session, la modification le `userId` du CORPS (absent ou usurpable), le retrait le paramètre `userId` (`system` par défaut), et AUCUNE de ces valeurs n'est lue en base ; seule la diffusion temps réel porte l'auteur de la session. REPRODUIT : le module transmet les mêmes valeurs, et ne corrige rien en silence. **Condition de mise en production** : décision écrite du juridique et du DPO parmi (a) colonne d'auteur côté Stock (slice Stock) avec auteur = compte de la session sur les trois routes, (b) acceptation écrite de l'absence de trace, (c) rattachement à un journal d'audit dédié. Tant que (a) n'est pas faite, personne ne peut dire QUI a déclaré, modifié ou retiré un consommable d'une séance.
2. **KB-SEA-22 : aucune sortie de stock à la déclaration**, malgré la Javadoc du legacy (« seule cette ligne sort du stock (FEFO) », « restoring stock » au retrait). L'ajout, la modification et le retrait ne créent aucun mouvement et ne changent ni `articles.stock_quantity` ni les lots : le Stock IMPUTE la consommation sur la sortie pour séances du jour (alertes `AUCUNE_PIECE`, `DEPASSEMENT_DE_LA_SORTIE`, `ARTICLE_HORS_PIECE`, ADR 0045). REPRODUIT (aucun changement de contrat) ; les Javadoc sont corrigées dans le module. **Condition de mise en production** : les métiers confirment que le stock physique est décrémenté par la sortie pour séances (ADR 0045) et non par la déclaration ; un centre qui n'a pas activé la sortie pour séances n'a AUCUNE décrémentation (alerte `AUCUNE_PIECE`).
3. **KB-SEA-24 : gardes de statut incohérentes entre les trois routes.** L'ajout exige `VALIDEE` ou `SIGNEE` (`CREE` et `ABSENT` : 422 `BUSINESS_RULE_VIOLATION`), la modification et le retrait acceptent TOUS les statuts sauf `FACTUREE` (422 `SEANCE_FACTUREE_NON_MODIFIABLE`) : on peut donc modifier ou retirer des consommables d'une séance « créée » ou « absente ». REPRODUIT. **Condition** : les métiers confirment la matrice (ou demandent de l'aligner sur `VALIDEE`/`SIGNEE`).
4. **KB-SEA-31 : `PUT` d'un corps `{}` ou d'une liste nulle EFFACE tous les raccourcis du centre** (200, traité comme liste vide). REPRODUIT (administrateur seul). **Condition** : confirmer que l'effacement par un corps vide est voulu, ou exiger la liste (400 si absente) ; le front envoie toujours la liste.

## Reproduits sans alerte
KB-SEA-23 (ajout CUMULATIF 2 + 3 = 5, sans clé d'idempotence : un double clic compte deux fois ; modification ABSOLUE qui crée la ligne d'un article jamais déclaré ; retrait d'un article non déclaré répond 200 `removed:true`), KB-SEA-28 (diffusions rediffusées à chaque appel même sans changement : `SEANCE_CONSOMMABLE_CHANGED` puis `SAISIE_INFIRMIER`), KB-SEA-29 (audit par chemin inchangé : ajout `SEANCES_CREATION`, modification `SEANCES_MODIFICATION`, retrait `SEANCES_SUPPRESSION` avec `entity_id` = article ; raccourcis `SEANCES_MODIFICATION` sans entité ; l'étiquette `SEANCES_SUPPRESSION` du retrait est celle de la suppression d'une séance : à confirmer avec le DPO, voir KB-SEA-08 et 17), KB-SEA-30 (quantité sans borne ni précision : 1e12 accepté, arrondi à deux décimales par la colonne du Stock), KB-SEA-32 (cache des raccourcis PAR centre et PAR instance, purgé au remplacement du centre, visible en retard sur une écriture SQL directe ; à vérifier si plusieurs instances partagent la base), KB-SEA-34 (droits : consommables `ADMIN` et `INFIRMIER` ; raccourcis lecture `ADMIN`, `INFIRMIER`, `SECRETAIRE`, écriture `ADMIN` ; `SUPERADMIN` 403 `ROLE_SCOPE` ; séance d'un autre centre 400 « Seance introuvable » ; le constat de rapprochement ne fige pas les consommations, seule sa validation les refuse, 422 `PIECE_DEJA_RAPPROCHEE`).

## Contexte
SE3 est caractérisée (net abdadcc5 : 28 scénarios, 28 goldens) : `POST /api/v1/seances/{id}/consommables`, `PUT` et `DELETE …/consommables/{articleId}` (`SeanceRestController`, `SeanceDomainService`), `GET` et `PUT /api/v1/seances/raccourcis-consommables` (`SeanceStationRestController`, `SeanceRaccourciDomainService`, `SeanceRaccourciJdbcAdapter`, cache `seance.raccourcis`). Le Stock possède déjà la consommation (`ConsommationsDeSeance`, ADR 0045) et le catalogue (`CatalogueDArticles`) : la Séance ne fait que déclarer et ne touche ni lot ni mouvement.

## Décisions
1. **Dans `backend/contexts/seance`**, mêmes tables et URL, aucune migration ni colonne (ADR 0024). Les consommables restent dans les tables du Stock (`consommations_de_seance`…), les raccourcis dans `seance_raccourcis_articles` (trois colonnes, clé primaire centre + article, constaté sur PostgreSQL 16).
2. **Domaine** : `Seance.exigerLAjoutDeConsommables` (facturée, puis validée ou signée) et `exigerLaModificationDesConsommables` (facturée seule) ; `QuantiteConsommee` (strictement positive, valeur conservée telle que saisie, deux messages du legacy) ; `ArticleId` ; agrégat `RaccourcisDeConsommables` (au plus 12, sans doublon, articles connus et actifs, contrôles dans l'ordre du legacy : taille, puis article par article) ; ports `RaccourcisDesCentres` (dépôt) et `ArticlesDuCentre` (catalogue). Aucun agrégat « consommable » dans la Séance : la consommation appartient au Stock (Customer-Supplier), ne pas la dupliquer.
3. **Application** : `AjouterUnConsommable`, `ModifierLaQuantiteDUnConsommable`, `RetirerUnConsommable`, `ConsulterLesRaccourcis`, `RemplacerLesRaccourcis` ; une unité de travail chacune. Ports : `ConsommablesDeLaSeance` (ACL du Stock : `ajouter`, `remplacer`, `retirer`), `DiffusionsDeSeance.consommablesModifies` (nouvelle). Le jour du centre n'intervient pas (aucune de ces routes ne lit la date) : `HorlogeDuCentre` n'est pas injectée, aucun `LocalDate.now`.
4. **Adapters** : ACL du Stock (`ConsommablesDeLaSeanceDuStock`, `ArticlesDuCentreDuStock`), dépôt JDBC `RaccourcisDesCentresJdbc` (mêmes tables), cache `RaccourcisDesCentresEnCache` (cache `seance.raccourcis`, purge APRÈS la validation de la transaction), diffusion `consommablesModifies` ajoutée au langage publié `DiffusionsFournies` et à son fournisseur legacy.
5. **Décisions par bug connu** :

| KB | Décision | Raison |
|---|---|---|
| KB-SEA-22 | REPRODUIRE (risque en tête) ; Javadoc corrigées | le Stock impute, ADR 0045 |
| KB-SEA-23 | REPRODUIRE | règle de saisie, golden stable ; idempotence à décider |
| KB-SEA-24 | REPRODUIRE (risque en tête) | statuts des métiers |
| KB-SEA-25 | **CORRIGER** : un seul statut par cause sur les trois routes : article inconnu, d'un autre centre ou inactif et quantité invalide : **400 `BAD_REQUEST`** (comme la validation de séance SE2 et les raccourcis, qui répondent déjà 400) ; refus de règle du Stock (facturée, pièce rapprochée) : 422 avec le code du Stock. Le Stock reste seul juge de l'article (un article devenu inactif ne peut qu'être diminué ou maintenu) ; le contrôle redondant du catalogue à l'ajout disparaît | cohérence du module, un seul juge |
| KB-SEA-26 | **CORRIGER** : corps, paramètre ou identifiant illisible, absent, quantité textuelle, `centerId` absent ou illisible, `centerId` absent du corps : 400 `BAD_REQUEST` (plus de 500, plus de message technique `CenterId cannot be null`), **403 avant 400**. `@Valid`/`@Positive` abandonnés au profit du domaine : plus aucun `VALIDATION_ERROR` sur ces routes | 400 contre 500, comme SE1/SE2 (KB-SEA-16) |
| KB-SEA-27 | **CORRIGER** : séance, garde de statut et PATIENT lus avant d'écrire, dans la même unité de travail que l'écriture : patient disparu = 404 `PATIENT_INTROUVABLE`, rien d'écrit, aucune diffusion (comme SE1 et SE2) | intégrité |
| KB-SEA-28 | REPRODUIRE | sans risque d'intégrité |
| KB-SEA-29 | REPRODUIRE | audit par chemin, DPO |
| KB-SEA-30 | REPRODUIRE | bornes à décider |
| KB-SEA-31 | REPRODUIRE (risque en tête) | décision métier |
| KB-SEA-32 | REPRODUIRE | cache par instance |
| KB-SEA-33 | REPRODUIRE (RISQUE MÉDICO-LÉGAL en tête) | décision juridique et DPO, slice Stock |
| KB-SEA-34 | REPRODUIRE | droits |
| Écritures concurrentes (limite du net) | **CORRIGER** : ligne de la séance verrouillée (`FOR UPDATE`) avant lecture et écriture ; remplacement des raccourcis sous verrou consultatif du centre ; verrou non obtenu dans les 5 s : 409 `ECRITURE_CONCURRENTE` | intégrité |
| Séance, patient, centre inconnus | séance inconnue ou d'un autre centre : 400 « Seance introuvable » ; patient disparu : 404 | décision de SE1 |

## Lot A (livré 2026-11-08) et suite
LOT A : ADR, domaine, application, ports publiés et ACL, dépôt JDBC des raccourcis et cache, contrats (mémoire, H2, PostgreSQL 16 par `seance-se3-pg16-rejeu.sh`), câblage des cinq cas d'usage ; AUCUNE route changée (le legacy sert toujours les six routes). LOT B : contrôleur, advice, droits, routage par retrait des handlers legacy, parité 28/28, verrou ArchUnit. LOT C : documentation, `built`.
