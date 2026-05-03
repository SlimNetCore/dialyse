# ✅ RAPPORT PHASE 5 — Refactorisation Endpoints REST

**Date :** 2026-05-03  
**Statut :** ✅ Complétée (lot backend, compatible)  
**Objectif :** réduire les endpoints à trop de `@RequestParam` en introduisant des recherches `@RequestBody` validées.

---

## Checklist exécutée

- [x] Identifier les endpoints avec > 3 paramètres
- [x] Introduire des endpoints `POST .../search` avec DTOs de critères
- [x] Conserver les endpoints GET existants pour compatibilité
- [x] Centraliser la logique de recherche dans des méthodes privées
- [x] Compiler et tester le backend

---

## Endpoints refactorisés

### `PatientRestController`

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/PatientRestController.java`

- Ajout de l’endpoint :
    - `POST /api/v1/patients/search`
    - `@RequestBody @Valid PatientSearchRequest`
- Le GET historique `/api/v1/patients` est conservé, mais transforme ses query params en `PatientSearchRequest` puis
  délègue.
- La logique SQL/pagination est désormais déléguée à `PatientListQueryService`.

### `PecRestController`

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/PecRestController.java`

- Ajout de deux endpoints :
    - `POST /api/v1/pec/attestations-center/search`
        - `@RequestBody @Valid AttestationSearchRequest`
    - `POST /api/v1/pec/pec-center/search`
        - `@RequestBody @Valid PecSearchRequest`
- Les GET historiques `/attestations-center` et `/pec-center` restent disponibles et délèguent aux nouvelles méthodes de
  critères.

---

## Conformité visée

- ✅ Les recherches complexes ont maintenant des variantes `POST` avec body structuré.
- ✅ Validation activée sur les critères (`@Valid` + contraintes déjà présentes sur les records DTO).
- ✅ Pas de rupture frontend immédiate (compatibilité GET conservée).

---

## Validation technique

### Compilation

- Commande : `mvnw.cmd clean compile`
- Résultat : ✅ BUILD SUCCESS

### Tests backend

- Commande : `mvnw.cmd test`
- Résultat : ✅ 11 tests, 0 échec

---

## Décisions d’implémentation

- **Compatibilité d’abord** : garder les GET existants pour éviter la régression côté Angular.
- **Migration progressive** : les nouveaux endpoints `POST .../search` sont prêts pour migration frontend.
- **Lisibilité** : extraction de méthodes privées (`listByCriteria`, `list...Criteria`) pour éviter la duplication.

---

## Statut des phases

- Phase 0 : ✅
- Phase 1 : ✅
- Phase 2 : ✅
- Phase 3 : ✅
- Phase 4 : ✅
- Phase 5 : ✅

**Prêt pour Phase 6 (Audit Sécurité).**

