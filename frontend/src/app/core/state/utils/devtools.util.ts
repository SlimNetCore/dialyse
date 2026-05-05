/**
 * DevTools integration utility for NgRx Signal Stores
 * Enables Redux DevTools support for Signal Store state tracking
 */

import {effect, isSignal} from '@angular/core';

/**
 * Enable Redux DevTools for Signal Store
 * Call this function in your store initialization to enable DevTools tracking
 *
 * Example:
 * export const AuthStore = signalStore(...);
 * enableDevtools(AuthStore, 'AuthStore');
 */
export function enableDevtools<T extends Record<string, any>>(
  store: T,
  storeName: string
): void {
  // Check if Redux DevTools is available
  const hasReduxDevTools = typeof window !== 'undefined' && (window as any).__REDUX_DEVTOOLS_EXTENSION__;

  if (!hasReduxDevTools) {
    console.warn(`Redux DevTools not available for ${storeName}`);
    return;
  }

  try {
    // Initialize DevTools connection
    const devTools = (window as any).__REDUX_DEVTOOLS_EXTENSION__.connect({
      name: storeName,
      trace: true,
      traceLimit: 25,
      features: {
        pause: true,
        lock: true,
        persist: true,
        export: true,
        import: 'custom',
        jump: true,
        skip: true,
        reorder: true,
        dispatch: true,
        test: true
      }
    });

    // Send initial state
    const initialState = getStateSnapshot(store);
    devTools.init(initialState);

    // Track state changes
    setupStateTracking(store, storeName, devTools);

    // Store DevTools instance for potential later use
    (store as any).__devtools = devTools;

    console.log(`✅ Redux DevTools enabled for ${storeName}`);
  } catch (error) {
    console.error(`Failed to enable DevTools for ${storeName}:`, error);
  }
}

/**
 * Get current state snapshot from Signal Store
 */
function getStateSnapshot(store: any): Record<string, any> {
  const snapshot: Record<string, any> = {};

  // Iterate through all properties
  for (const key in store) {
    if (typeof store[key] === 'function') continue; // Skip methods
    if (key.startsWith('_')) continue; // Skip private properties

    try {
      const value = store[key];

      // Check if it's a signal (callable function that returns a value)
      if (isSignal(value)) {
        snapshot[key] = value();
      }
    } catch (e) {
      // Skip properties that can't be accessed
    }
  }

  return snapshot;
}

/**
 * Setup automatic state change tracking
 */
function setupStateTracking(
  store: any,
  storeName: string,
  devTools: any
): void {
  const previousValues: Record<string, any> = {};
  let isInitial = true;

  // Create effect to track changes
  effect(() => {
    const currentState = getStateSnapshot(store);

    // Detect which properties changed
    const changedKeys: string[] = [];
    for (const key of Object.keys(currentState)) {
      if (!Object.prototype.hasOwnProperty.call(previousValues, key) ||
        previousValues[key] !== currentState[key]) {
        changedKeys.push(key);
      }
    }

    // Update previous values
    for (const key of Object.keys(currentState)) {
      previousValues[key] = currentState[key];
    }

    // Skip initial state (already sent)
    if (isInitial) {
      isInitial = false;
      return;
    }

    // Send action to DevTools if state changed
    if (changedKeys.length > 0) {
      const actionName = `${storeName} - ${changedKeys.map(k => formatPropertyName(k)).join(' & ')}`;
      devTools.send(
        {
          type: actionName,
          timestamp: new Date().toISOString(),
          changedProperties: changedKeys
        },
        currentState
      );
    }
  });
}

/**
 * Format property key for readability in DevTools (camelCase → Camel Case)
 */
function formatPropertyName(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, str => str.toUpperCase())
    .trim();
}

export default enableDevtools;


