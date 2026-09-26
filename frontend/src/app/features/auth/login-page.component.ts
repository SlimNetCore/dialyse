import {TitleCasePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {AuthApiService, DirectoryItem} from '../../core/api/auth-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {LangStore} from '../../core/state/lang.store';
import {ThemeStore} from '../../core/state/theme.store';
import {MatMenuModule} from '@angular/material/menu';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {homeRouteFor} from '../../core/auth/role-scope.guard';
import {LoginPageStore} from './state/login-page.store';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    TranslateModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    FormRoot,
    FormField,
    MatMenuModule,
    MatButtonToggleModule,
    TitleCasePipe,
  ],
  templateUrl: './login-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login-page.component.css',
})
export class LoginPageComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly loginStore = inject(LoginPageStore);
  readonly lang = inject(LangStore);
  readonly store = inject(AppShellStore);
  readonly theme = inject(ThemeStore);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  /** Affiché après la création du compte propriétaire par l'installation initiale. */
  readonly setupDone = inject(ActivatedRoute).snapshot.queryParamMap.get('setup') === 'done';
  readonly loading = this.loginStore.loading;
  readonly error = this.loginStore.error;

  /** Étape 1 : sociétés actives (annuaire public). Étape 2 : centres de la société choisie. */
  readonly societes = signal<DirectoryItem[]>([]);
  readonly centres = signal<DirectoryItem[]>([]);
  readonly loadingCentres = signal(false);

  /**
   * « centre » : société puis centre (personnel). « direction » : société facultative, sans centre — le
   * propriétaire de l'application (aucune société) et la direction d'une société se connectent ici.
   */
  readonly mode = signal<'centre' | 'direction'>('centre');

  readonly loginModel = signal({
    selectedSociete: '',
    selectedCenter: '',
    username: '',
    password: '',
  });

  readonly loginForm = compatForm(this.loginModel, (form) => {
    required(form.selectedSociete);
    required(form.selectedCenter);
    required(form.username);
    required(form.password);
  });
  readonly canSubmit = computed(() => {
    const form = this.loginModel();
    const identified = !!form.username && !!form.password && !this.loading();
    return this.mode() === 'direction' ? identified : identified && !!form.selectedSociete && !!form.selectedCenter;
  });
  private readonly selectedSocieteId = computed(() => this.loginModel().selectedSociete);

  constructor() {
    // Première ouverture : aucun propriétaire n'existe encore, l'installation doit d'abord le créer.
    this.authApi.getSetupStatus().subscribe({
      next: (status) => {
        if (status.required) void this.router.navigate(['/setup']);
      },
      error: () => undefined,
    });

    this.authApi.getLoginSocietes().subscribe({
      next: (list) => {
        this.societes.set(list);
        if (list.length === 1) {
          this.loginModel.update((m) => ({...m, selectedSociete: list[0].id}));
        }
      },
      error: () => this.loginStore.setError('Impossible de charger les sociétés'),
    });

    // Le choix d'une société détermine la liste des centres proposés. L'effet ne dépend que de l'identifiant
    // de société (computed) : lire tout le modèle relancerait le chargement à chaque frappe ou sélection.
    effect(() => {
      const societeId = this.selectedSocieteId();
      untracked(() => this.loadCentres(societeId));
    });
  }

  doLogin(): void {
    if (!this.canSubmit()) return;
    const {selectedSociete, selectedCenter, username, password} = this.loginModel();
    const direction = this.mode() === 'direction';
    this.loginStore.setLoading(true);
    this.loginStore.setError('');
    this.authApi
      .login({
        societeId: selectedSociete || undefined,
        centerId: direction ? undefined : selectedCenter,
        username,
        password,
      })
      .subscribe({
        next: (res) => {
          this.authStore.setSession(res);
          if (res.centerId) {
            this.store.switchCenter(res.centerId);
            this.authApi.getAccessibleCenters().subscribe({
              next: (centers) => this.store.setAvailableCenters(centers),
              error: () => undefined,
            });
          }
          this.loginStore.setLoading(false);
          this.router.navigate([homeRouteFor(res.roles ?? [])]);
        },
        error: (err) => {
          this.loginStore.setError(
            err?.error?.detail || err?.error?.message || 'Erreur de connexion',
          );
          this.loginStore.setLoading(false);
        },
      });
  }

  setMode(mode: 'centre' | 'direction'): void {
    this.mode.set(mode);
    this.loginStore.setError('');
  }

  private loadCentres(societeId: string): void {
    this.centres.set([]);
    this.loginModel.update((m) => (m.selectedCenter ? {...m, selectedCenter: ''} : m));
    if (!societeId) return;
    this.loadingCentres.set(true);
    this.authApi.getLoginCentres(societeId).subscribe({
      next: (list) => {
        this.centres.set(list);
        this.loadingCentres.set(false);
        if (list.length === 1) {
          this.loginModel.update((m) => ({...m, selectedCenter: list[0].id}));
        }
      },
      error: () => this.loadingCentres.set(false),
    });
  }
}
