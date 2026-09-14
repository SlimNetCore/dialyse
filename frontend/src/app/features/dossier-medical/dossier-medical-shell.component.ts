import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTabsModule} from '@angular/material/tabs';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {DossierMedicalAccessService} from './dossier-medical-access.service';
import {resolvePatientIdFromRoute} from './dossier-medical-route.util';

type PatientIdentity = {
  nom: string;
  prenom: string;
  code: string;
};

/**
 * Coquille du dossier médical : identité patient, mode lecture seule global, et navigation
 * par onglets adressables (URL partageable, chargement paresseux par onglet).
 */
@Component({
  selector: 'app-dossier-medical-shell',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './dossier-medical-shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dossier-medical-shell.component.css',
})
export class DossierMedicalShellComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly patientIdentity = signal<PatientIdentity | null>(null);
  protected readonly loadingIdentity = signal(true);
  protected readonly tabs = [
    {path: 'synthese', label: 'DOSSIER_MEDICAL.TAB_SYNTHESE', icon: 'summarize'},
    {path: 'antecedents', label: 'DOSSIER_MEDICAL.TAB_ANTECEDENTS', icon: 'history_edu'},
    {path: 'serologies', label: 'DOSSIER_MEDICAL.TAB_SEROLOGIES', icon: 'coronavirus'},
    {path: 'examens', label: 'DOSSIER_MEDICAL.TAB_EXAMENS', icon: 'biotech'},
    {path: 'abords', label: 'DOSSIER_MEDICAL.TAB_ABORDS', icon: 'vaccines'},
    {path: 'prescriptions', label: 'DOSSIER_MEDICAL.TAB_PRESCRIPTIONS', icon: 'medication'},
    {path: 'biologie', label: 'DOSSIER_MEDICAL.TAB_BIOLOGIE', icon: 'biotech'},
    {path: 'anemie', label: 'DOSSIER_MEDICAL.TAB_ANEMIE', icon: 'bloodtype'},
    {path: 'constantes', label: 'DOSSIER_MEDICAL.TAB_CONSTANTES', icon: 'monitor_heart'},
    {path: 'ordonnances', label: 'DOSSIER_MEDICAL.TAB_ORDONNANCES', icon: 'medication'},
    {path: 'greffe', label: 'DOSSIER_MEDICAL.TAB_GREFFE', icon: 'volunteer_activism'},
  ];
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly router = inject(Router);
  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!this.patientId || !centerId) {
      this.loadingIdentity.set(false);
      return;
    }
    this.api.getPatient(this.patientId, centerId, this.auth.username() ?? '').subscribe({
      next: (data) => {
        const d = data as Record<string, unknown>;
        this.patientIdentity.set({
          nom: (d['nom'] as string) ?? '',
          prenom: (d['prenom'] as string) ?? '',
          code: (d['codePatient'] as string) ?? (d['code'] as string) ?? '',
        });
        this.loadingIdentity.set(false);
      },
      error: () => this.loadingIdentity.set(false),
    });
  }

  goBackToPatient(): void {
    this.router.navigate(['/patients', this.patientId]);
  }
}
