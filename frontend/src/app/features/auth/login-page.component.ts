import {TitleCasePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {Router} from '@angular/router';
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
  readonly loading = this.loginStore.loading;
  readonly error = this.loginStore.error;

  /** Étape 1 : sociétés actives (annuaire public). Étape 2 : centres de la société choisie. */
  readonly societes = signal<DirectoryItem[]>([]);
  readonly centres = signal<DirectoryItem[]>([]);
  readonly loadingCentres = signal(false);

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
    return !!form.selectedSociete && !!form.selectedCenter && !!form.username && !!form.password && !this.loading();
  });
  private readonly selectedSocieteId = computed(() => this.loginModel().selectedSociete);

  constructor() {
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
    const {selectedSociete, selectedCenter, username, password} = this.loginModel();
    if (!selectedSociete || !selectedCenter || !username || !password || this.loading()) return;
    this.loginStore.setLoading(true);
    this.loginStore.setError('');
    this.authApi
      .login({societeId: selectedSociete, centerId: selectedCenter, username, password})
      .subscribe({
        next: (res) => {
          this.authStore.setSession(res);
          this.store.switchCenter(res.centerId);
          this.authApi.getAccessibleCenters().subscribe({
            next: (centers) => this.store.setAvailableCenters(centers),
            error: () => undefined,
          });
          this.loginStore.setLoading(false);
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loginStore.setError(
            err?.error?.detail || err?.error?.message || 'Erreur de connexion',
          );
          this.loginStore.setLoading(false);
        },
      });
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
