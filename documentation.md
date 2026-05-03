# Documentation Authentification et Securisation (Front + Back)

Ce document explique la methode d'authentification actuellement implemente dans l'application, avec un scenario detaille
de bout en bout.

Objectif: qu'un lecteur puisse comprendre le mecanisme et le reproduire dans une autre application (Angular + Spring
Boot).

---

## 1) Vue d'ensemble de l'architecture

### Stack

- **Frontend**: Angular (HTTP interceptor, session en memoire via signals).
- **Backend**: Spring Boot + Spring Security (stateless) + JWT.
- **Stockage refresh tokens**: table SQL `auth_refresh_token`.

### Principe cle

- L'access token JWT est stocke dans un **cookie HttpOnly** (`HEMO_AUTH`), pas dans `localStorage`.
- Le refresh token opaque est stocke dans un **cookie HttpOnly** distinct (`HEMO_REFRESH`).
- Le frontend envoie les cookies automatiquement via `withCredentials: true`.
- Le backend est **stateless** (`SessionCreationPolicy.STATELESS`) et valide le JWT a chaque requete.

---

## 2) Fichiers et composants importants

### Backend

- `backend/src/main/java/com/hemodialyse/backend/infrastructure/security/SecurityConfig.java`
- `backend/src/main/java/com/hemodialyse/backend/infrastructure/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/hemodialyse/backend/infrastructure/security/JwtTokenProvider.java`
- `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/AuthRestController.java`
- `backend/src/main/java/com/hemodialyse/backend/application/auth/AuthService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/schema.sql` (table `auth_refresh_token`)

### Frontend

- `frontend/src/app/features/auth/login-page.component.ts`
- `frontend/src/app/core/api/auth-api.service.ts`
- `frontend/src/app/core/api/auth.interceptor.ts`
- `frontend/src/app/core/auth/auth-session.service.ts`
- `frontend/src/app/app.config.ts` (APP_INITIALIZER + interceptor)
- `frontend/src/app/core/layout/shell.component.ts` (logout)

---

## 3) Configuration securite appliquee

## 3.1 Cote backend (Spring Security)

Dans `SecurityConfig`:

- CSRF desactive (`csrf().disable()`) car API stateless + cookies JWT/refresh geres explicitement.
- Session HTTP desactivee (`STATELESS`).
- Endpoints publics:
    - `POST /api/v1/auth/login`
    - `POST /api/v1/auth/logout`
    - `POST /api/v1/auth/refresh`
    - docs Swagger, health, websocket, OPTIONS.
- Endpoints proteges:
    - `GET /api/v1/auth/me`
    - `/api/v1/patients/**`, `/api/v1/pec/**`, `/api/v1/referentials/**`, etc.
- Filtre JWT: `JwtAuthenticationFilter` avant `UsernamePasswordAuthenticationFilter`.

### Headers/CORS

- Origines autorisees: `app.cors.allowed-origins` (dans `application.yml`).
- `allowCredentials = true` (obligatoire pour envoyer/recevoir les cookies cross-origin).
- SameSite/Secure/Path/Domain des cookies configures via:
    - `app.auth.cookie.*`

## 3.2 Cote frontend (Angular)

- `authInterceptor` force `withCredentials: true` pour toutes les requetes `/api/v1/`.
- En cas de `401`, l'interceptor tente un refresh automatique (`/auth/refresh`) puis rejoue la requete.
- Le service `AuthSessionService` maintient l'etat de session en memoire (username, roles, centre, etc.).
- Au bootstrap, `APP_INITIALIZER` appelle `auth.initFromServer({ force: true })` (hors route `/login`) pour restaurer la
  session depuis le serveur.

---

## 4) Modele des tokens

## 4.1 Access token (JWT)

Genere par `JwtTokenProvider.generateToken(...)` avec claims:

- `sub` = username
- `userId`
- `roles`
- `center_id`
- `exp` selon `app.jwt.expiration`

Usage:

- lu depuis cookie `HEMO_AUTH` par `JwtAuthenticationFilter`
- valide a chaque requete
- converti en `Authentication` Spring Security

## 4.2 Refresh token (opaque + rotation)

- Cree par `AuthService.issueRefreshToken(...)`
- Valeur brute renvoyee **uniquement** au cookie client
- En base, seul un **hash SHA-256** est stocke (`token_hash`) dans `auth_refresh_token`
- Expiration: `app.jwt.refresh-expiration`
- Rotation: a chaque refresh, ancien token revoke + nouveau token cree

Schema SQL (resume):

- table `auth_refresh_token`:
    - `id`, `token_hash` (unique), `user_id`, `center_id`, `expires_at`, `revoked`, `created_at`, `revoked_at`

---

## 5) Scenario detaille d'authentification (Front -> Back -> Front)

## 5.1 Login initial

1. L'utilisateur soumet le formulaire dans `login-page.component.ts` via `doLogin()`.
2. `AuthApiService.login()` envoie `POST /api/v1/auth/login` avec:
    - `centerId`, `username`, `password`
    - `withCredentials: true`
3. `AuthRestController.login()` appelle `AuthService.login(...)`.
4. `AuthService.login(...)`:
    - charge l'utilisateur (`app_user`)
    - verifie `active = true`
    - verifie le mot de passe BCrypt
    - verifie l'acces au centre (`app_user_center`)
    - charge roles (`app_user_role` + `app_role`)
    - genere le JWT (access token)
5. `AuthRestController.login()`:
    - genere aussi un refresh token (`issueRefreshToken`)
    - renvoie **2 Set-Cookie**:
        - cookie access (`HEMO_AUTH`, HttpOnly)
        - cookie refresh (`HEMO_REFRESH`, HttpOnly)
    - renvoie le body `LoginResponse` (username, fullName, centerId, roles...)
6. Frontend:
    - `AuthSessionService.setSession(res)` met la session en memoire
    - `store.switchCenter(res.centerId)`
    - navigation vers `/dashboard`

## 5.2 Appel API protege apres login

1. Un composant appelle un service API frontend.
2. `authInterceptor` ajoute `withCredentials: true`.
3. Le navigateur envoie automatiquement le cookie `HEMO_AUTH`.
4. `JwtAuthenticationFilter` lit le cookie, valide le JWT, construit un `UserPrincipal`.
5. Spring Security autorise/refuse selon:
    - regles `SecurityConfig`
    - eventuel `@PreAuthorize` (ex: admin uniquement)
6. Le backend repond, le frontend affiche les donnees.

---

## 6) Scenario refresh token (expiration access token)

Quand l'access token expire:

1. Une requete protegee retourne `401`.
2. `authInterceptor` intercepte l'erreur:
    - ignore login/logout/refresh eux-memes
    - lance une seule requete refresh partagee (`refreshInFlight$` + `shareReplay(1)`) pour eviter N refresh paralleles
3. `AuthApiService.refresh()` envoie `POST /api/v1/auth/refresh` avec `withCredentials: true`.
4. `AuthRestController.refresh()` lit le cookie `HEMO_REFRESH`.
5. `AuthService.rotateFromRefreshToken(...)`:
    - verifie token non expire et non revoke (`FOR UPDATE`)
    - revoque l'ancien token
    - regenere un nouvel access token
    - cree un nouveau refresh token
    - si reutilisation suspecte: revoke tous les tokens utilisateur
6. Le backend renvoie les nouveaux cookies access+refresh.
7. L'interceptor rejoue la requete initiale.
8. Si refresh echoue:
    - `AuthSessionService.clearSession()`
    - redirection `/login`

---

## 7) Scenario logout

1. L'utilisateur clique logout (dans `shell.component.ts`, `onLogout()`).
2. Frontend appelle `POST /api/v1/auth/logout`.
3. Backend:
    - revoque le refresh token courant
    - renvoie cookies access/refresh expires (max-age=0)
4. Frontend:
    - vide session locale (`clearSession`)
    - deconnecte websocket
    - redirige vers `/login`

---

## 8) Comment les cookies fonctionnent ici

## 8.1 Attributs utilises

Dans `AuthRestController.buildCookie(...)`:

- `HttpOnly = true` (inaccessible depuis JS, limite XSS)
- `Secure` configurable (`app.auth.cookie.secure`)
- `SameSite` configurable (`Strict` par defaut)
- `Path`:
    - access token: `/`
    - refresh token: `/api/v1/auth/refresh`
- `Domain` optionnel
- `Max-Age` base sur expiration access/refresh

## 8.2 Impact pratique

- Le frontend ne lit jamais directement les tokens.
- Les cookies sont envoyes seulement si:
    - `withCredentials: true` cote client
    - `Access-Control-Allow-Credentials: true` cote serveur
    - origine autorisee explicitement en CORS

---

## 9) Gestion des erreurs et codes HTTP

- **401 Unauthorized**
    - JWT absent/expire/invalide
    - refresh invalide/expire
    - le frontend tente refresh auto (sauf endpoints exclus)
- **403 Forbidden**
    - utilisateur authentifie mais role insuffisant (`@PreAuthorize`, ex admin)
- **400 / 422**
    - erreurs metier/validation (via `ApiExceptionHandler`)

---

## 10) Chaine complete des appels (reference rapide)

## 10.1 Login (front -> back)

- Front:
    - `LoginPageComponent.doLogin()`
    - `AuthApiService.login()`
- Back:
    - `AuthRestController.login()`
    - `AuthService.login()`
    - `JwtTokenProvider.generateToken()`
    - `AuthService.issueRefreshToken()`
- Retour front:
    - cookies set
    - `AuthSessionService.setSession()`

## 10.2 Requete metier protegee

- Front:
    - composant metier -> service API
    - `authInterceptor`
- Back:
    - `JwtAuthenticationFilter`
    - `SecurityConfig` / `@PreAuthorize`

## 10.3 Refresh

- Front:
    - `authInterceptor` (sur 401) -> `AuthApiService.refresh()`
- Back:
    - `AuthRestController.refresh()`
    - `AuthService.rotateFromRefreshToken()`
- Front:
    - replay requete initiale

## 10.4 Logout

- Front: `ShellComponent.onLogout()`
- Back: `AuthRestController.logout()` + revocation
- Front: `clearSession()` + redirection

---

## 11) Reproduire ce mecanisme dans une autre application

Checklist implementation:

1. **Backend Security**
    - Configurer Spring Security en stateless
    - Ajouter un filtre JWT en amont
    - Definir endpoints publics (`/auth/login`, `/auth/refresh`, `/auth/logout`) et routes protegees

2. **JWT + Refresh Rotation**
    - JWT court (access token)
    - refresh token opaque long
    - stocker uniquement hash du refresh token en DB
    - rotation stricte a chaque refresh
    - detection de reuse + invalidation globale utilisateur

3. **Cookies securises**
    - `HttpOnly`, `Secure`, `SameSite`, `Path` ajustes
    - `withCredentials` cote front
    - CORS precis (origines exactes, credentials=true)

4. **Frontend Interceptor**
    - ajouter `withCredentials` automatiquement
    - sur `401`, lancer refresh unique mutualise
    - rejouer la requete originale apres refresh
    - fallback vers logout local + redirect login

5. **Session Front locale**
    - stocker profil en memoire (pas de token en JS)
    - endpoint `/auth/me` pour restaurer la session au demarrage

6. **Protection roles**
    - backend: `@PreAuthorize` + roles dans JWT
    - frontend: afficher/masquer UI selon roles (controle UX, pas securite)

7. **Observabilite / audit minimum**
    - journaliser echec login, echec refresh, reuse detecte
    - monitorer taux de 401/403

---

## 12) Notes de mise en production

- En production, mettre `Secure=true` et HTTPS obligatoire.
- Valider `SameSite` selon architecture (SPA meme domaine, sous-domaines, SSO, etc.).
- Faire rotation periodique de `app.jwt.secret` (avec strategie de migration si necessaire).
- Limiter la duree de vie access token (court) et refresh token (raisonnable).
- Ajouter rate limiting sur `/auth/login` et `/auth/refresh`.
- Eventuellement ajouter CSRF token si politique cookies cross-site plus ouverte.

---

## 13) Exemple sequence simplifiee

```text
[LoginPageComponent] --POST /auth/login--> [AuthRestController]
[AuthRestController] --AuthService.login--> [DB app_user/app_user_role/app_user_center]
[AuthRestController] --Set-Cookie HEMO_AUTH + HEMO_REFRESH--> [Browser]
[Browser] --cookies automatiques--> [API /patients/...]
[JwtAuthenticationFilter] --valide JWT--> [SecurityContext]

Si 401:
[auth.interceptor] --POST /auth/refresh--> [AuthRestController.refresh]
[AuthService] --rotate refresh--> [DB auth_refresh_token]
[AuthRestController] --Set-Cookie nouveaux tokens--> [Browser]
[auth.interceptor] --replay requete initiale--> [API]
```

---

## 14) Credentials de seed (dev)

Les utilisateurs seed sont prepares dans `seed.sql` et les mots de passe sont forces au demarrage par
`SeedPasswordInitializer`:

- `admin` / `admin123`
- `medecin` / `medecin123`

(Usage dev uniquement)

