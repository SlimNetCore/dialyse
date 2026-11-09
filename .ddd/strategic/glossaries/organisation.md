---
status: validated
date: 2026-10-09
context: Organisation (supporting) — inclut le shared kernel minimal (ADR 0010)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §1]
---
# Glossaire — Organisation

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Société | Entité juridique regroupant des centres | `Societe` / `SocieteId` (kernel) | `societes` | — | — | proposed |
| Centre | Établissement de dialyse, unité d'isolement des données | `Centre` / `CentreId` (kernel) | `CenterId`, `center`, `centers` | Center | isolement par centre (RG-TRV-002) | proposed |
| Transfert de centre | Rattachement d'un centre à une autre société | `Centre.transfererVers()` / `CentreTransfere` | `SocieteDomainService` | — | — | proposed |
| Salle | Local de dialyse d'un centre (dont salles d'isolement) | `Salle` | `SalleRef` | — | — | proposed |
| Créneau | Plage horaire de dialyse d'un centre | `Creneau` | `CreneauRef` | — | — | proposed |
| Mois | Période mensuelle de référence des clôtures | `Mois` (kernel) | — | — | ADR 0009/0010 | proposed |
| Centre actif | Centre non désactivé ; un centre dont l'état n'a jamais été renseigné est actif | `AnnuaireDesCentres.actifs()` | `centers.actif`, `COALESCE(actif, TRUE)` | centre « en service », centre « ouvert » | ADR 0107, 0115 (une seule définition, Stock compris ; `centers.actif` NOT NULL DEFAULT TRUE depuis V56) | proposed |
| ~~Centre actif au sens strict~~ (RETIRÉ le 2026-11-09) | Ancienne variante réservée au Stock (un état non renseigné en était exclu) ; supprimée, le Stock lit « Centre actif » | ~~`actifsAuSensStrict()`~~ | — | — | ADR 0108 (amendé), 0115, KB-ORG-09 corrigé | proposed |
| Verrou du centre | Verrou de la ligne d'un centre, pris dans la transaction de l'appelant, qui sérialise les opérations « centre entier » d'un autre contexte (clôture de période de stock) | `VerrouDuCentre` | `SELECT … FOR UPDATE` sur `centers` | — | ADR 0108 | proposed |
| Fuseau horaire du centre | Fuseau (identifiant IANA) dans lequel se juge « aujourd'hui » pour le centre ; vide si inconnu ou illisible, chaque consommateur applique alors son défaut | `AnnuaireDesCentres.fuseauHoraireDe()` | `centers.fuseau_horaire` | timezone, fuseau du site | ADR 0107 | proposed |
| Annuaire des centres | Langage publié de lecture des centres (actifs, tous, fuseau) | `AnnuaireDesCentres` | lecteurs SQL `CentresActifs*`, `HorlogeDesCentres*`, `JourDuCentre*` | répertoire, registre des centres | organisation.v1, lot A de O4 | proposed |
| Annuaire des sociétés | Langage publié de lecture des sociétés actives, de la société d'un centre, d'une société avec ses centres | `AnnuaireDesSocietes` | `SocietesEtCentresJdbc`, `SocieteDUnCentreJdbc` | répertoire des sociétés | organisation.v1, lot A de O4 | proposed |
| Libellés de l'Organisation | Langage publié de lecture, par lot d'identifiants, du code et du nom des salles, du code et du libellé des créneaux, du libellé des médecins (nom puis prénom), et de leur recherche par terme. Un identifiant inconnu, d'un autre centre ou sans libellé n'a pas d'entrée | `LibellesDeLOrganisation` | lecteurs SQL de `LecturesPublieesDesSeancesJdbc`, `LectureDesMouvementsJdbc`, `LectureDesPatientsJdbc` | libellés de référentiel | organisation.v1, lot C de O4, ADR 0109 | proposed |
| Libellé du médecin | « Nom Prénom » sans blanc de bord (le prénom est facultatif) ; un médecin désactivé (V52) garde son libellé | `LibellesDeLOrganisation.nomsDesMedecins()` | `medecin.nom`, `medecin.prenom` | — | ADR 0109 | proposed |
| Libellé borné au centre | Nom d'une salle ou libellé d'un créneau lu DANS le centre du mouvement ; une salle ou un créneau d'un autre centre rend un libellé vide, comme un libellé disparu (corrigé le 2026-11-09 ; à confirmer métiers/DPO au plus tard le 2026-12-15) | `nomsDesSalles(centre, salles)`, `libellesDesCreneaux(centre, creneaux)` | `LEFT JOIN salle`, `LEFT JOIN position_creneau` sans borne | — | KB-ORG-10 corrigé, ADR 0115, `verifications-production.md` §73 | proposed |
| Salle d'isolement | Salle réservée aux patients à risque infectieux ; propriété de la salle, écrite par Organisation seule. Définir les salles d'isolement d'un centre retire toutes les autres du centre, ignore une salle inconnue ou d'un autre centre, et suit la transaction de l'appelant | `DefinirLesSallesDIsolement.definir()`, `SalleDuCentre.estSalleDIsolement` | `salle.isolement` (`'OUI'`/`'NON'`) | `UPDATE salle` par la Planification | ADR 0053, 0110 | proposed |
| Identité imprimée | Ce que les documents impriment d'un centre et de sa société : nom, adresse (adresse, ville, wilaya), téléphone, email, site web ; pour la société aussi NIF, NIS, RC, pied de page et logo. Textes rognés, jamais nuls (absent = vide), logo absent si vide ; une société désactivée s'imprime comme une autre ; la mise en forme est celle de l'imprimeur | `IdentiteImprimee` (`duCentre`, `deLaSociete`) | lecteur SQL de `DocumentIdentityProvider` | — | ADR 0111 | proposed |
| Salles et créneaux du centre | Salles (fermées comprises) et créneaux (séries) d'un centre, triés par code, lus bornés au centre ; une salle ou un créneau d'un autre centre n'existe pas pour l'appelant | `SallesEtCreneauxDuCentre` | `salle`, `position_creneau` | — | ADR 0110 | proposed |
| Réserver le centre | Verrou d'écriture sur la ligne du centre, qui n'empêche pas les insertions des lignes rattachées au centre (distinct de *verrouiller le centre*, verrou fort) ; sérialise les écritures du référentiel des infirmiers | `VerrouDuCentre.reserverLeCentre()` | `UPDATE centers SET id = id` | — | ADR 0056, 0110 | proposed |

Homonymes : *Centre* (ici) ≠ *Centre payeur* (Facturation).
