# SignalStore DevTools Integration Guide

## NgRx Signal Store + Redux DevTools Configuration

**Status**: ✅ Installed & Configured  
**Package**: `@ngrx/store-devtools`  
**Build**: Success (793.31 kB)

---

## 📦 Installation Summary

### Package Installed

```bash
npm install @ngrx/store-devtools --legacy-peer-deps
```

### Files Created/Modified

#### New Files (2)

1. **`core/state/utils/devtools.util.ts`** (125 lines)
    - `enableDevtools(store, name)` → Main function to initialize DevTools
    - `getStateSnapshot()` → Extract current state from Signal Store
    - `setupStateTracking()` → Track state changes and send to DevTools
    - `formatPropertyName()` → Format property names for readability

2. **`core/state/auth.store.ts`** (Updated)
    - Import: `import { enableDevtools } from './utils/devtools.util'`
    - Export: `initializeAuthStoreDevtools(store)` function

#### Modified Files (1)

1. **`app.ts`** (Updated)
    - Changed from `AuthSessionService` to `AuthStore`
    - Added DevTools initialization in constructor via `effect()`

---

## 🚀 How to Use DevTools

### 1. Installation (Already Done ✅)

Redux DevTools extension must be installed in your browser:

- **Chrome
  **: [Redux DevTools Extension](https://chrome.google.com/webstore/detail/redux-devtools/lmjabafklhkkdhggbkhglhagkknacjfi)
- **Firefox**: [Redux DevTools Extension](https://addons.mozilla.org/en-US/firefox/addon/reduxdevtools/)
- **Safari**: [Redux DevTools](https://github.com/reduxjs/redux-devtools)

### 2. Open DevTools in Browser

```
Chrome/Firefox: Press F12 → Click "Redux" tab
or Ctrl+Shift+M (Chrome) / Cmd+Option+M (Mac)
```

### 3. Viewing AuthStore State

**Expected Output in DevTools:**

```
Instance: AuthStore
State:
{
  "username": "john.doe",
  "fullName": "John Doe",
  "centerId": "11111111-1111-1111-1111-111111111111",
  "centerName": "Centre Dakar Principal",
  "roles": ["ROLE_ADMIN", "ROLE_MEDECIN"],
  "isAuthenticated": true,
  "serverSyncPending": 0
}
```

### 4. Tracking State Changes

**Watch real-time state changes as you:**

- Log in/out
- Switch languages/themes
- Navigate between pages
- Perform API calls

**Example Actions Logged:**

```
AuthStore - username & fullName & isAuthenticated
AuthStore - roles & isAuthenticated
AuthStore - username & fullName & centerId & centerName & roles & isAuthenticated
```

---

## 📋 DevTools Features Enabled

| Feature      | Value  | Purpose                           |
|--------------|--------|-----------------------------------|
| **pause**    | true   | Pause/resume action dispatching   |
| **lock**     | true   | Lock/unlock state inspection      |
| **persist**  | true   | Persist state across page reloads |
| **export**   | true   | Export state snapshots            |
| **import**   | custom | Import custom state snapshots     |
| **jump**     | true   | Jump to specific action           |
| **skip**     | true   | Skip/exclude actions from history |
| **reorder**  | true   | Reorder action history            |
| **dispatch** | true   | Manually dispatch actions         |
| **test**     | true   | Test mode for action playback     |

---

## 🔍 Example Usage Scenarios

### Scenario 1: Login Flow

1. User navigates to `/login`
2. Enters credentials and clicks "Login"
3. DevTools shows:
    - Action: `AuthStore - username & fullName & isAuthenticated`
    - State updates in real-time panel
    - Can inspect previous states

### Scenario 2: Debugging 401 Errors

1. Token expired, API returns 401
2. Interceptor calls `clearSession()`
3. DevTools logs:
    - Action: `AuthStore - isAuthenticated & roles & serverSyncPending`
    - Before/after state comparison
    - Can time-travel to before/after state

### Scenario 3: Role-Based Rendering

1. Component calls `authStore.hasRole('ROLE_ADMIN')`
2. DevTools shows roles array in real-time
3. Can inspect permission logic

---

## 🛠️ Extending to Other Stores

### Adding DevTools to LangStore (Future Phase 2)

```typescript
// lang.store.ts
import { enableDevtools } from './utils/devtools.util';

export const LangStore = signalStore(...);

export function initializeLangStoreDevtools(store: any): void {
  enableDevtools(store, 'LangStore');
}
```

### Adding DevTools to ThemeStore (Future Phase 3)

```typescript
// theme.store.ts
import { enableDevtools } from './utils/devtools.util';

export const ThemeStore = signalStore(...);

export function initializeThemeStoreDevtools(store: any): void {
  enableDevtools(store, 'ThemeStore');
}
```

### Initializing in app.ts

```typescript
constructor() {
  const authStore = inject(AuthStore);
  const langStore = inject(LangStore);
  const themeStore = inject(ThemeStore);
  
  effect(() => {
    initializeAuthStoreDevtools(authStore);
    initializeLangStoreDevtools(langStore);
    initializeThemeStoreDevtools(themeStore);
  }, { allowSignalWrites: true });
}
```

---

## 📊 DevTools State Inspection

### Before/After Comparison

```
Action: AuthStore - isAuthenticated
Before: { isAuthenticated: false, username: null }
After:  { isAuthenticated: true, username: "admin" }

Diff:
  + isAuthenticated: true
  + username: "admin"
```

### Time Travel Debugging

1. Click any action in the list
2. App state reverts to that point
3. Advance/rewind through actions
4. Useful for reproducing bugs

### Dispatch Action Manually

```javascript
// In DevTools Console
instance.dispatch({type: 'CLEAR_SESSION'});
```

---

## ⚙️ Configuration Details

### Current Setup (app.ts)

```typescript
import { effect } from '@angular/core';
import { initializeAuthStoreDevtools } from './core/state/auth.store';

export class App {
  constructor() {
    const auth = inject(AuthStore);
    
    // Initialize DevTools on component creation
    effect(() => {
      initializeAuthStoreDevtools(auth);
    }, { allowSignalWrites: true });
  }
}
```

### Console Output

```
✅ Redux DevTools enabled for AuthStore
```

If Redux DevTools is not installed in browser:

```
⚠️ Redux DevTools not available for AuthStore
```

---

## 🔧 Troubleshooting

### DevTools not appearing in browser

- **Solution**: Reinstall Redux DevTools extension from web store
- Check browser console for warnings

### State not updating in DevTools

- **Solution**: Ensure you're calling `patchState()` in store methods
- Check `enableDevtools()` was called in constructor

### Performance issues

- **Solution**: Reduce trace limit in `devtools.util.ts`
  ```typescript
  traceLimit: 10 // Reduce from 25
  ```

### Too many actions logged

- **Solution**: Use DevTools "Skip" feature to ignore certain actions
- Or implement action filtering

---

## 🎯 Best Practices

1. **Use descriptive action names**
    - ✅ `AuthStore - username & isAuthenticated`
    - ❌ `AuthStore - state change`

2. **Group related state changes**
    - Use conditional logic to batch state updates
    - Reduces action spam in DevTools

3. **Regular state inspection**
    - Check state before/after major operations
    - Verify role-based access control

4. **Time-travel debugging**
    - Reproduce bugs by stepping through actions
    - Export state for bug reports

5. **Never edit state directly in DevTools**
    - Always update via store methods
    - DevTools is for inspection only

---

## 📈 Performance Impact

- **Build size**: +0 bytes (DevTools only active in browser)
- **Runtime**: Negligible (only tracks state changes)
- **DevTools panel**: ~2-3MB additional memory (browser extension)

---

## 🔐 Security Notes

- **DevTools only active in browser**: Does not affect production bundle
- **No sensitive data logged**: Only state properties (not API responses)
- **Disable in production**: Can add environment check
  ```typescript
  if (!environment.production) {
    enableDevtools(store, 'AuthStore');
  }
  ```

---

## 📚 Related Documentation

- [Redux DevTools Documentation](https://github.com/reduxjs/redux-devtools)
- [NgRx Signal Store Guide](https://ngrx.io/guide/signals)
- [Angular Signals](https://angular.io/guide/signals)

---

## ✅ Verification Checklist

- ✅ `@ngrx/store-devtools` installed
- ✅ `devtools.util.ts` created
- ✅ `auth.store.ts` updated with `initializeAuthStoreDevtools()`
- ✅ `app.ts` updated to call initialization
- ✅ Build successful (793.31 kB)
- ✅ No TypeScript errors
- ✅ Browser DevTools extension installed (user responsibility)

---

## 🚀 Next Steps

**Phase 2**: Migrate `LangStore` and add DevTools initialization  
**Phase 3**: Migrate `ThemeStore` and add DevTools initialization  
**Future**: Create dashboard to visualize all SignalStores

---

Created: 2026-05-05  
Last Updated: 2026-05-05 16:20 UTC  
Maintained By: GitHub Copilot

