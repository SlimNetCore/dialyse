import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

type LoginPageState = {
  selectedCenter: string;
  username: string;
  password: string;
  loading: boolean;
  error: string;
};

const initialState: LoginPageState = {
  selectedCenter: '',
  username: '',
  password: '',
  loading: false,
  error: ''
};

export const LoginPageStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('LoginPageStore'),
  withMethods((store) => ({
    setSelectedCenter(selectedCenter: string): void {
      patchState(store, {selectedCenter});
    },

    setUsername(username: string): void {
      patchState(store, {username});
    },

    setPassword(password: string): void {
      patchState(store, {password});
    },

    setLoading(loading: boolean): void {
      patchState(store, {loading});
    },

    setError(error: string): void {
      patchState(store, {error});
    }
  }))
);
