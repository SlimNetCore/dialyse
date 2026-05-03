# ✅ RAPPORT PHASE 6 — Audit Sécurité

**Date :** 2026-05-03  
**Statut :** ✅ Complétée (backend durci, sans régression)  
**Objectif :** renforcer auth cookie-based, headers de sécurité, validation d’entrée et gestion d’erreurs.

---

## Checklist exécutée

- [x] Vérifier la stratégie token (cookies HttpOnly, pas de token en JS)
- [x] Restreindre le path du refresh token
- [x] Ajouter les headers de sécurité HTTP majeurs
- [x] Réduire la permissivité CORS headers
- [x] Ajouter un handler d’erreur générique non verbeux
- [x] Renforcer `@Valid` sur request bodies principaux
- [x] Compiler et exécuter les tests backend

---

## Changements appliqués

### 1) Auth cookies (durcissement)

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/AuthRestController.java`

- Séparation explicite des paths cookie :
    - access token : `app.auth.cookie.path` (défaut `/`)
    - refresh token : `app.auth.cookie.refresh-path` (défaut `/api/v1/auth/refresh`)
- `login`, `refresh`, `logout` utilisent désormais des cookies construits avec path adapté.
- Nettoyage logout cohérent sur les deux paths.

**Config :** `backend/src/main/resources/application.yml`

- Ajout : `app.auth.cookie.refresh-path: /api/v1/auth/refresh`

### 2) Headers de sécurité HTTP

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/security/SecurityConfig.java`

- Ajout d’une CSP :
    - `default-src 'self'`
    - `script-src 'self'`
    - `style-src 'self' 'unsafe-inline'`
    - `img-src 'self' data: https:`
    - `connect-src 'self' http://localhost:4200`
    - `frame-ancestors 'none'`
    - `form-action 'self'`
- Ajout :
    - `X-Frame-Options: DENY`
    - `X-Content-Type-Options: nosniff`
    - `Referrer-Policy: strict-origin-when-cross-origin`

### 3) CORS plus strict

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/security/SecurityConfig.java`

- Remplacement de `allowedHeaders = ["*"]` par une liste explicite :
    - `Content-Type`, `Accept`, `X-Requested-With`, `X-Skip-Auth-Refresh`, `X-XSRF-TOKEN`

### 4) Validation d’entrée

**Fichiers :**

- `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/PatientRestController.java`
- `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/PecRestController.java`

- Ajout de `@Valid` sur les `@RequestBody` principaux (`create`, `update`, `validate`, etc.).

### 5) Fuites d’erreurs

**Fichier :** `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/ApiExceptionHandler.java`

- Ajout d’un `@ExceptionHandler(Exception.class)` global :
    - log serveur complet
    - réponse client générique `INTERNAL_ERROR`
    - pas d’exposition de stacktrace/détails techniques

---

## Vérification frontend (stockage token)

- Recherche effectuée : pas de stockage token dans `localStorage/sessionStorage` ✅
- Les `localStorage.setItem(...)` trouvés concernent uniquement thème/langue.
- Appels auth Angular utilisent `withCredentials: true` ✅

Fichiers vérifiés :

- `frontend/src/app/core/api/auth-api.service.ts`
- `frontend/src/app/core/api/auth.interceptor.ts`

---

## Validation technique

### Compilation

- Commande : `mvnw.cmd clean compile`
- Résultat : ✅ BUILD SUCCESS

### Tests backend

- Commande : `mvnw.cmd test`
- Résultat : ✅ 11 tests, 0 échec

---

## Décisions de sécurité

- **CSRF** : conservé désactivé côté Spring Security (cohérent avec stratégie cookie `SameSite=Strict` déjà en place +
  architecture actuelle).
- **Refresh token path** : restreint au strict nécessaire (`/api/v1/auth/refresh`).
- **Durcissement progressif** : sécurité renforcée sans casser les contrats API existants.

---

## Statut des phases

- Phase 0 : ✅
- Phase 1 : ✅
- Phase 2 : ✅
- Phase 3 : ✅
- Phase 4 : ✅
- Phase 5 : ✅
- Phase 6 : ✅

**Prêt pour Phase 7 (nettoyage du code mort).**

