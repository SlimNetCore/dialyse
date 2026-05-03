# ✅ RAPPORT PHASE 7 — Nettoyage du Code Mort

**Date :** 2026-05-03  
**Statut :** ✅ Complétée (code mort supprimé, sans régression)  
**Objectif :** Identifier et supprimer les fichiers, classes et dossiers inutilisés dans le backend et le frontend.

---

## Méthodologie

1. **Analyse statique manuelle** : grep des noms de classes/composants dans tout le codebase
2. **Vérification des routes Angular** : `app.routes.ts`, `patient.routes.ts`, `admin.routes.ts`
3. **Vérification des imports** : grep sur chaque composant suspect dans les fichiers `.ts` et `.html`
4. **Vérification des appels Java** : grep des noms de classes dans tous les `.java`
5. **Validation finale** : compilation + tests backend, build production frontend

---

## Fichiers supprimés — Frontend Angular

| Fichier                                               | Raison                                                                          |
|-------------------------------------------------------|---------------------------------------------------------------------------------|
| `features/auth/login.component.ts`                    | Remplacé par `login-page.component.ts` (route pointe vers `LoginPageComponent`) |
| `features/patient/patient-form.component.ts`          | Non référencé dans aucune route, template ou import                             |
| `features/patient/patient-detail.component.ts`        | Non référencé dans `patient.routes.ts` ni dans aucun import                     |
| `features/patient/patient-create-form.component.ts`   | Non référencé dans aucune route ou import                                       |
| `features/patient/patient-create-form.component.html` | Associé au composant mort ci-dessus                                             |
| `features/patient/patient-create-form.component.scss` | Associé au composant mort ci-dessus                                             |
| `features/pec/pec-workflow.component.ts`              | Non référencé dans aucune route, template ou import                             |

### Dossiers vides supprimés — Frontend

| Dossier                   | Raison                                     |
|---------------------------|--------------------------------------------|
| `features/patient/state/` | Dossier vide (état NgRx jamais implémenté) |

---

## Fichiers supprimés — Backend Java

| Fichier                                                    | Raison                                                                                                      |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `application/system/GetSystemStatusUseCase.java`           | Jamais injecté ni appelé (`SystemRestController` implémente la logique inline)                              |
| `domain/patient/service/PatientCommandHydrator.java`       | Classe `final` package-private non utilisée (`PatientDomainService` implémente l'hydratation inline)        |
| `domain/patient/service/PatientAssureRelationService.java` | Classe `final` package-private non utilisée (`PatientDomainService` implémente `syncAssureRelation` inline) |

### Dossiers vides supprimés — Backend

| Dossier                         | Raison                                                                     |
|---------------------------------|----------------------------------------------------------------------------|
| `application/web/dto/`          | Dossier vide depuis Phase 1 (DTOs déplacés dans `infrastructure/web/dto/`) |
| `src/main/resources/static/`    | Dossier vide (frontend servi séparément)                                   |
| `src/main/resources/templates/` | Dossier vide (Thymeleaf non utilisé)                                       |

---

## Composants conservés (faux positifs écartés)

| Composant                | Raison de conservation                                      |
|--------------------------|-------------------------------------------------------------|
| `PatientQrCardComponent` | Importé et utilisé dans `patient-list.component.ts`         |
| `NotificationService`    | Injecté dans `PatientRestController` et `PecRestController` |

---

## Amélioration TypeScript

**Fichier :** `frontend/tsconfig.json`

Ajout de deux flags compilateur pour prévenir le code mort futur :

- `"noUnusedLocals": true` — erreur de compilation si une variable locale n'est pas utilisée
- `"noUnusedParameters": true` — erreur de compilation si un paramètre de fonction n'est pas utilisé

---

## Validation technique

### Backend

- Commande : `mvnw.cmd clean test`
- Résultat : ✅ **11 tests, 0 échec — BUILD SUCCESS**

### Frontend

- Commande : `ng build --configuration production`
- Résultat : ✅ **Build réussi** (warnings préexistants sur budget de taille uniquement, pas d'erreurs TypeScript)

---

## Bilan

| Catégorie                    | Supprimés |
|------------------------------|-----------|
| Composants Angular           | 5         |
| Fichiers HTML/SCSS orphelins | 2         |
| Classes Java                 | 3         |
| Dossiers vides               | 4         |
| **Total éléments supprimés** | **14**    |

---

## Statut des phases

- Phase 0 : ✅
- Phase 1 : ✅
- Phase 2 : ✅
- Phase 3 : ✅
- Phase 4 : ✅
- Phase 5 : ✅
- Phase 6 : ✅
- Phase 7 : ✅

**Prêt pour Phase 8.**

