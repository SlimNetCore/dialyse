# Corrections - Fiche Patient Vide et Bouton Modifier Grisé

## Problèmes Identifiés et Corrections

### Problème 1: L'onglet généralités s'affiche vide

**Cause Root:**

- Quand on consulte un patient existant (mode édition/consultation), la fonction `patchStepsFromWizardData()` n'appelait
  que `patchActiveStep()`, qui ne patchait que l'étape active.
- Si l'étape 0 (généralités) n'était pas l'étape active au moment du chargement initial, elle n'était jamais patchée
  avec les données.
- De plus, si l'étape n'était pas active, le composant `step-generalites` n'était pas rendu du tout à cause de la
  condition `shouldRenderStep()`, donc `patchData()` ne pouvait pas être appelée.

**Corrections Apportées:**

1. **Logique de patchage unifiée** (ligne 789-813):
    - Maintenant, TOUTES les étapes sont patchées en mode édition, pas seulement l'étape active
    - Cela garantit que les données sont disponibles pour tous les onglets, même s'ils ne sont pas actuellement affichés

2. **Contrôle dynamique du rendu** (ligne 452-453, 588-596):
    - Ajout d'une variable signal `loadingPatientData` pour signaler quando les étapes doivent être rendues
    - Pendant le chargement des données patient, TOUTES les étapes sont rendues (même si elles ne sont pas visibles)
    - Une fois le chargement terminé, le rendu revient à la normale (seulement active + adjacentes)

3. **Flux amélioré** (ligne 994-1001):
    - `loadingPatientData` est défini à `true` au début du chargement
    - Il est réinitialisé à `false` une fois que les données sont chargées et patchées (ligne 563-565)

**Code Modifié:**

- `patient-wizard.component.ts` (lignes 452, 562-565, 588-596, 789-813, 994-1001)

### Problème 2: Le bouton "Modifier" grisé

**Analyse:**
Le bouton "Modifier" (qui passe en mode édition) se trouve à la ligne 68-71 et n'a pas d'attribut `disabled`. Il ne
devrait donc pas être grisé techniquement.

**Hypothèses:**

1. Le utilisateur confond peut-être avec un autre bouton (ex: "Enregistrer")
2. Le bouton "Enregistrer" (ligne 130-135) est volontairement grisé (non-visible) en mode consultation car
   `consultationMode()` est `true` et `canSave()` retourne `false`
3. Il y a peut-être un problème CSS qui le rend grisé

**Solution recommandée:**
Une fois que les données patient s'affichent correctement (après la correction du problème 1), vérifier si le bouton "
Modifier" est visible et fonctionnel. Si c'est un autre bouton qui pose problème, il faudra l'identifier précisément.

## Test

Un fichier de test (`patient-wizard.component.spec.ts`) a été créé pour vérifier:

1. Que toutes les étapes sont rendues pendant le chargement
2. Que `patchData()` est appelée avec les bonnes données
3. Que le rendu revient à la normale après le chargement

## Débogage

Des logs ont été ajoutés à `patchStepsFromWizardData()` pour faciliter le débogage:

- Affiche un avertissement si `wizardData` est vide
- Affiche le nombre de clés et les champs critiques dans `wizardData`
- Affiche les 10 premières clés pour vérifier le format des données

Vous verrez ces logs dans la console du navigateur (F12 > Console) lors du chargement d'un patient.

## Vérification

Pour vérifier que les corrections fonctionnent:

1. Ouvrez la console du navigateur (F12)
2. Allez dans l'onglet "Patients"
3. Cliquez sur "Voir" pour un patient existant
4. Vérifiez que:
    - Les données patient s'affichent dans l'onglet "Généralités"
    - Les logs apparaissent dans la console débogage
    - Le bouton "Modifier" est visible et fonctionnel

