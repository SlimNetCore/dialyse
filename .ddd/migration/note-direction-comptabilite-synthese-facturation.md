---
status: draft
date: 2026-10-21
destinataires: direction, comptabilité
sources: [.ddd/migration/slices/facturation-f5-consultation-pilotage.md, ADR 0034, .ddd/migration/verifications-production.md (point 21)]
---
# Note à la direction et à la comptabilité — changement de certains chiffres de la synthèse mensuelle de facturation

*(À relire puis à transmettre par vous : ce document n'a été envoyé à personne.)*

## Ce qui change

La **synthèse mensuelle de facturation** (document imprimé, en PDF ou en Excel) reprend désormais, pour les montants,
les **totaux enregistrés sur chaque facture** (HT, TVA, TTC). Avant, elle recalculait le TTC ligne par ligne et
arrondissait chaque ligne, ce qui pouvait donner un total légèrement différent de la somme des factures.

Deux impressions existent (celle de l'écran de facturation et celle du module « Documents ») ; **toutes les deux**
suivent maintenant les totaux enregistrés. Leur présentation reste différente, comme avant.

## Ce que vous verrez

- Un **petit écart sur les totaux**, qui peut aller de quelques centimes à quelques dizaines de dinars selon le mois.
  Exemple sur un jeu de **test** (février 2026, ce n'est pas une donnée réelle) :

  | Montant | Avant | Maintenant |
  |---|---|---|
  | Montant HT | 1 760,35 | 1 837,00 |
  | Montant TTC | 1 995,54 | 2 072,13 |

  Le nouveau chiffre est celui qui correspond à la somme des factures émises.
- Une **colonne « Montant TVA »** en plus dans la sortie Excel.
- Les factures sans ligne détaillée sont maintenant comptées dans les totaux.
- Le **tableau de bord de facturation** et les chiffres de la **Direction** ne changent pas : ils additionnaient déjà les
  totaux enregistrés.

## Ce qui n'est pas modifié (à trancher avec vous)

Trois différences de définition existent toujours entre écrans. Nous les avons laissées telles quelles ; elles
expliquent que deux chiffres du même mois peuvent légitimement différer :

1. le **chiffre d'affaires par caisse** utilise l'agence figée sur la facture, alors que les **séances par caisse**
   utilisent le payeur actuel du patient ;
2. l'**encaissé** de la Direction additionne les règlements de toutes dates ;
3. le nombre de **séances facturées** peut ne pas être égal à la somme des répartitions.

Merci de nous dire si l'une de ces définitions doit être alignée.

## Ce que nous vous demandons

1. **Valider** les nouveaux montants de la synthèse avant la mise en production. Une requête de contrôle est prévue
   (point 21.2 de `.ddd/migration/verifications-production.md`) : elle mesure, par centre et par mois, l'écart
   réel entre l'ancien et le nouveau calcul sur vos données ; à lancer par l'équipe technique.
2. Nous confirmer que la synthèse reste réservée aux profils **ADMIN et SECRETAIRE** (le médecin seul n'y a plus accès).
3. Prévenir les personnes qui comparent la synthèse à d'autres états : les mois **déjà clôturés** peuvent apparaître
   avec un total différent de celui qui avait été imprimé.

## Autres changements visibles de la Facturation (rappel)

- Une facture ne peut plus être émise si une séance n'a pas de forfait (message explicite).
- La TVA appliquée est celle en vigueur à la **fin de la période facturée**.
- Un **remboursement** (règlement négatif) est comptabilisé en sens inverse ; un remboursement supérieur au total déjà
  réglé est refusé.
- Les règlements peuvent porter un **mode** (banque, chèque, caisse, espèces) et une **référence** ; un envoi répété
  d'un même règlement n'en crée plus deux s'il porte la même clé.
