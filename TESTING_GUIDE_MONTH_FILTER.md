# Guide de Test - Filtre Mois du Dashboard

## 🧪 Instructions de Test en Local

### Prérequis

- Backend compilé : `mvnw clean compile` ✅
- Frontend compilé : `npm run build` ✅
- H2 database en mémoire prête

### Étape 1 : Démarrer l'Application

#### Terminal 1 - Backend (Port 8090)

```bash
cd backend
./mvnw spring-boot:run
# Attendre le message : "Started ... in X seconds"
```

#### Terminal 2 - Frontend (Port 4200)

```bash
cd frontend
npm start
# Attendre le message : "Application bundle generation complete"
```

### Étape 2 : Se Connecter

1. Naviguer vers : `http://localhost:4200`
2. Identifiants de test :
    - Username: `admin@hemodialyse.fr`
    - Password: `admin123`
3. Cliquer sur "Connexion"

### Étape 3 : Accéder au Dashboard

1. Une fois connecté, naviguer vers le menu principal
2. Cliquer sur "Tableau de Bord" ou "Dashboard"
3. L'URL doit être : `http://localhost:4200/dashboard`

### Étape 4 : Tester le Filtre Mois

#### A) Affichage Initial (Sans Filtre)

```
Observé :
✓ Champ "Échéance (jours)" = 30
✓ Champ "Filtrer par mois" = vide
✓ 6 cartes statistiques affichées :
  - Patients (nombre total)
  - PEC Créées
  - PEC Validées
  - PEC Expirant
  - Attestations (total)
  - Attestations Expirant
```

#### B) Tester le Filtre Mois

```
Action : Cliquer sur le champ "Filtrer par mois"
Résultat : Un calendrier picker s'ouvre
          Sélectionner : Juni 2024 (ou le mois actuel)

Observé :
✓ Statistiques se mettent à jour
✓ Nombres peuvent diminuer (filtrées par mois)
✓ Le champ affiche la date sélectionnée (format YYYY-MM)
```

#### C) Combiner les Deux Filtres

```
Action : Modifier "Échéance" à 60 jours
         Garder le mois sélectionné

Observé :
✓ Les deux filtres s'appliquent ensemble
✓ Valeurs mises à jour
```

#### D) Réinitialiser le Filtre Mois

```
Action : Effacer le champ mois (clic sur le champ + supprimer)
         Ou sélectionner une date puis effacer

Observé :
✓ Statistiques reviennent aux 6 cartes complètes
✓ Nombres augmentent (tous les temps)
✓ Le champ devient vide
```

### Étape 5 : Tester Différents Mois

```
Test 1 : Juin 2024
Résultat : Voir les PEC/attestations créées en juin

Test 2 : Janvier 2024
Résultat : Voir les PEC/attestations créées en janvier (peut être 0)

Test 3 : Le mois actuel (juillet 2026)
Résultat : Voir les données du mois courant
```

### Étape 6 : Tester le Responsive Design

#### Sur Desktop (1920x1080)

```
Layout : Deux champs sur une ligne
✓ "Échéance (jours)" à gauche
✓ "Filtrer par mois" à droite
✓ Gap de 12px entre les champs
```

#### Sur Tablet (768px)

```
Layout : Deux champs toujours côte à côte
✓ Les champs restent visibles
✓ Les cartes passa en 2 colonnes
```

#### Sur Mobile (360px)

```
Layout : Champs empilés verticalement
✓ "Échéance (jours)" prend 100% de largeur
✓ "Filtrer par mois" en dessous, aussi 100%
✓ Les cartes passent à 1 colonne
```

### Étape 7 : Tester les Traductions

#### Français

```
Libellés attendus :
✓ "Tableau de bord du centre"
✓ "Échéance (jours)"
✓ "Filtrer par mois"
✓ "Patients"
✓ "PEC Créées" / "PEC Validées" / "PEC Expirant..."
✓ "Attestations"
```

#### Anglais (si disponible)

```
Label switch vers "en" dans les paramètres
✓ "Center Dashboard"
✓ "Expiration (days)"
✓ "Filter by month"
```

---

## 🔍 Vérifications Techniques

### Vérifier les Appels API

#### Ouvrir les Developer Tools (F12)

```
1. Onglet "Network"
2. Sélectionner le mois
3. Observer l'appel GET à : /api/v1/dashboard/stats
4. Params doivent inclure :
   - centerId=<uuid>
   - expirationDays=30
   - month=2024-06 (exemple)
```

#### Vérifier la Réponse JSON

```json
{
  "pecCree": 5,
  "pecValidee": 10,
  "pecExpiring": 2,
  "attestationTotal": 8,
  "attestationExpiring": 1,
  "patientCount": 25,
  "expirationDays": 30,
  "month": "2024-06"
}
```

### Vérifier le Store State (DevTools)

```
1. Installer Redux DevTools Extension
2. Ouvrir Redux DevTools
3. Naviguer vers "Dashboard Store"
4. Sélectionner un mois
5. Observer : selectedMonth = "2024-06"
6. Sélectionner un autre mois
7. Observer : changement du state et rechargement des stats
```

---

## ❌ Résolution de Problèmes

### Symptôme : Le champ mois n'apparaît pas

```
Cause possible : Frontend non compilé
Solution :
  cd frontend
  npm run build
  npm start
```

### Symptôme : Les statistiques ne changent pas en sélectionnant un mois

```
Cause possible : Backend ne retourne pas les bonnes données
Solution :
  1. Vérifier les appels API (Network tab)
  2. Vérif la réponse contient le "month" correct
  3. Redémarrer le backend : mvnw spring-boot:run
```

### Symptôme : Erreur 400 ou 404 sur l'API

```
Cause possible : Format du mois invalide
Solution :
  - Le format doit être YYYY-MM (ex: 2024-06)
  - Utiliser le HTML5 month picker (auto-format)
  - Ne pas saisir manuellement
```

### Symptôme : Translations manquantes

```
Cause possible : Fichiers i18n non mis à jour
Solution :
  1. Vérifier les fichiers en frontend/public/i18n/*.json
  2. Vérifier que DASHBOARD.FILTER_MONTH existe
  3. Recharger la page (Ctrl+F5)
```

---

## 📊 Données de Test Suggests

Pour mieux tester, insérer des données de test :

```sql
-- Démo data (mois actuel) - à exécuter via H2 console
INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) 
VALUES (random_uuid(), '<center-id>', 'CREE', '2026-07-05', '2026-08-05');

INSERT INTO attestation_droit (id, center_id, created_at, date_fin) 
VALUES (random_uuid(), '<center-id>', '2026-07-05', '2027-07-05');

-- Pour mois précédent (juin 2026)
INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) 
VALUES (random_uuid(), '<center-id>', 'VALIDEE', '2026-06-15', '2026-07-15');
```

H2 Console : `http://localhost:8090/h2-console`

---

## 🧬 Tests Automatisés

### Exécuter les Tests Backend

```bash
cd backend
./mvnw test -Dtest=DashboardRestControllerMonthFilterTest -v
```

### Exécuter les Tests Frontend (Unit)

```bash
cd frontend
npm test -- dashboard.store.spec.ts
npm test -- center-dashboard.component.spec.ts
```

### Exécuter les Tests E2E

```bash
cd frontend
npm run e2e -- dashboard-month-filter.spec.ts
```

---

## ✅ Checklist de Validation

**Avant le Go-Live :**

- [ ] Backend compile sans erreurs
- [ ] Frontend compile sans erreurs
- [ ] Accès au dashboard fonctionnel
- [ ] Filtre mois apparaît dans l'UI
- [ ] Sélection d'un mois filtre les stats
- [ ] Les deux filtres (jours + mois) fonctionnent ensemble
- [ ] Effacer le mois réinitialise les stats
- [ ] Responsive design OK (desktop, tablet, mobile)
- [ ] Traductions affichées correctement
- [ ] API appels correctement paramétrés (Network tab)
- [ ] Tests unitaires passent
- [ ] Tests E2E passent

**Après déploiement :**

- [ ] Vérifier sur serveur de production
- [ ] Tester multi-centre (différents centres)
- [ ] Tester permissions utilisateurs
- [ ] Tester avec données réelles
- [ ] Vérifier performances (plusieurs mois, gros volumes)

---

## 📞 Support

Si vous rencontrez des problèmes :

1. Vérifier les logs du backend :
   ```bash
   tail -f backend-log-live.txt
   ```

2. Vérifier la console du navigateur (F12)

3. Vérifier les requêtes réseau (F12 → Network)

4. Consulter la documentation technique : `DASHBOARD_MONTH_FILTER_IMPLEMENTATION.md`

---

**Version** : 1.0
**Date** : 5 juillet 2026
**Status** : Prêt pour test ✅

