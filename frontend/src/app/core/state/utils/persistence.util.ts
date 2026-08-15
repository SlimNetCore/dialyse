/**
 * Centralized persistence utility for NgRx SignalStore
 * Handles localStorage serialization/deserialization for all stores
 */

export class PersistenceUtil {
  /**
   * Retrieve value from localStorage
   * @param key Storage key
   * @param defaultValue Default value if key not found or parse fails
   * @returns Parsed value or default value
   */
  static getFromStorage<T>(key: string, defaultValue: T): T {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return defaultValue;
      return JSON.parse(raw) as T;
    } catch {
      return defaultValue;
    }
  }

  /**
   * Retrieve string value from localStorage
   * @param key Storage key
   * @param defaultValue Default value if key not found
   * @returns String value or default
   */
  static getStringFromStorage(key: string, defaultValue: string = ''): string {
    try {
      return localStorage.getItem(key) ?? defaultValue;
    } catch {
      return defaultValue;
    }
  }

  /**
   * Save value to localStorage
   * @param key Storage key
   * @param value Value to persist
   */
  static saveToStorage<T>(key: string, value: T): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      console.error(`Failed to persist ${key} to localStorage:`, error);
    }
  }

  /**
   * Save string value to localStorage
   * @param key Storage key
   * @param value String value to persist
   */
  static saveStringToStorage(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      console.error(`Failed to persist ${key} to localStorage:`, error);
    }
  }

  /**
   * Remove value from localStorage
   * @param key Storage key
   */
  static removeFromStorage(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error(`Failed to remove ${key} from localStorage:`, error);
    }
  }

  /**
   * Clear all values from localStorage
   */
  static clearStorage(): void {
    try {
      localStorage.clear();
    } catch (error) {
      console.error('Failed to clear localStorage:', error);
    }
  }
}
