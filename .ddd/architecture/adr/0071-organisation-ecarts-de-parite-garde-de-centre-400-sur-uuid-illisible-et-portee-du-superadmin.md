---
status: proposed
date: 2026-11-02
decision-makers: [utilisateur (décisions du 2026-11-02, à accepter formellement), architecte]
context-scope: organisation
related: ADR 0024, 0063, 0070 ; `.ddd/safety-net/known-bugs.md` (KB-ORG-02, 03, 05) ; `.ddd/safety-net/organisation-retroactif.md` ; `.ddd/verify/verify-organisation-2.md`
---

# 0071 - Organisation : écarts de parité décidés (garde de centre, 400 sur UUID illisible, tri, suppression logique) et portée du SUPERADMIN

## Contexte

Le legacy d'Organisation a été supprimé avant sa caractérisation ; le filet a été reconstitué sur `b365a63a^` (script reproductible
`backend/scripts/organisation-rejeu-legacy.sh`, exécuté le 2026-11-02 : 22 scénarios verts contre le legacy réel). La parité est
atteinte sauf les écarts ci-dessous, tous décidés par l'utilisateur le 2026-11-02, relus golden par golden (`_nouveau_code`, ancien
comportement archivé dans `_ecarts-legacy`).

## Décisions

1. **Garde de centre sur `GET /api/v1/referentials/*` (KB-ORG-05)** : le centre est celui du jeton ; un autre centre, ou une session sans
   centre, répond 403. Le legacy croyait le `centerId` du client : tout utilisateur authentifié lisait les référentiels de n'importe quel
   centre. Conséquence de production : les comptes de centre sans centre rattaché perdent l'accès (200 devient 403) : contrôle ORG-12 de
   `verifications-production.md` § 36.
2. **400 pour un identifiant ou un paramètre UUID illisible (KB-ORG-02)** au lieu de 500 (3 goldens). Le message est encore celui de
   l'exception (« Invalid UUID string ») : un message métier fixe reste possible. Le 500 sur un corps absent ou illisible de
   `POST /societes` (KB-ORG-01) n'est PAS décidé.
3. **Tri** : salles et positions par code, médecins par nom (le legacy ne triait pas ; KB-ORG-03).
4. **Suppression logique des médecins** (V52, `medecin.actif`), dans l'API d'Organisation et dans l'administration générique (édition
   minimale de `ReferentialAdminJdbcAdapter`, seam fermé) ; le contrôle d'usage `REFERENTIAL_IN_USE` de l'admin est conservé.
5. **Portée du SUPERADMIN** : ouverte le 2026-11-02 à tout `/api/v1/organisation`, puis LIMITÉE au legacy après la revue n° 2 : il garde
   `/organisation/societes` et `/organisation/centres` (création, modification, transfert, lecture) ; salles, créneaux, médecins, machines,
   protocoles et référentiels lui répondent 403 `ROLE_SCOPE`, comme `/api/v1/referentials` et `/api/v1/admin/referentials`. Raison :
   l'écriture transverse sur les référentiels de n'importe quel centre n'avait aucun consommateur (`OrganisationApiService` n'est importé par
   aucun composant) et élargissait les droits du legacy. Conséquence : deux NOUVELLES URLs publiques (`/organisation/societes`,
   `/organisation/centres`), écart avec « mêmes URLs » de l'ADR 0024 ; les URLs historiques `/api/v1/societes/**` coexistent et
   renvoient le même contenu.

## Conséquences

- Les goldens `_nouveau_code` sont les seuls écarts de parité ; toute autre différence échoue le filet.
- Le test `AutorisationsDeLOrganisationIntegrationTest` fige la matrice SUPERADMIN.
- À valider par l'utilisateur ; non appliqué au-delà de l'existant.
