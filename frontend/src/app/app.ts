import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';

import { AppShellStore } from './core/state/app-shell.store';
import { BackendApiService, PecStatus } from './core/api/backend-api.service';
import { AuthApiService } from './core/api/auth-api.service';
import { AuthSessionService } from './core/auth/auth-session.service';
import { LangService } from './core/i18n/lang.service';
import { PatientFormComponent } from './features/patient/patient-form.component';
import { PatientCreateFormComponent, PatientFormData } from './features/patient/patient-create-form.component';
import { PatientListComponent, PatientRow } from './features/patient/patient-list.component';
import { PecWorkflowComponent } from './features/pec/pec-workflow.component';
import { LoginComponent, LoginFormPayload } from './features/auth/login.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    MatToolbarModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatDividerModule,
    MatMenuModule,
    MatTabsModule,
    TranslateModule,
    PatientFormComponent,
    PatientCreateFormComponent,
    PatientListComponent,
    PecWorkflowComponent,
    LoginComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly store = inject(AppShellStore);
  private readonly api = inject(BackendApiService);
  private readonly authApi = inject(AuthApiService);
  readonly authSession = inject(AuthSessionService);
  readonly lang = inject(LangService);

  readonly loginLoading = signal(false);
  readonly currentPatientId = signal<string | null>(null);
  readonly currentPecId = signal<string | null>(null);
  readonly currentPecStatus = signal<PecStatus | null>(null);
  readonly sessionAllowed = signal<boolean | null>(null);
  readonly feedback = signal<string>('Pret.');
  readonly patients = signal<PatientRow[]>([]);
  readonly showPatientForm = signal(false);

  switchCenter(centerId: string): void {
    this.store.switchCenter(centerId);
    this.feedback.set(`Centre actif change: ${centerId}`);
  }

  login(payload: LoginFormPayload): void {
    this.loginLoading.set(true);
    this.feedback.set('Authentification en cours...');

    this.authApi.login(payload).subscribe({
      next: (response) => {
        this.authSession.setSession(response);
        this.store.switchCenter(response.centerId);
        this.feedback.set(`Bienvenue ${response.username} - ${response.centerName}`);
        this.loginLoading.set(false);
        this.loadPatients();
      },
      error: (err) => {
        this.feedback.set(this.extractError(err));
        this.loginLoading.set(false);
      }
    });
  }

  logout(): void {
    this.authSession.clearSession();
    this.currentPatientId.set(null);
    this.currentPecId.set(null);
    this.currentPecStatus.set(null);
    this.sessionAllowed.set(null);
    this.feedback.set('Session terminee.');
  }

  loadPatients(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.api.listPatients(centerId, this.authSession.username() ?? 'demo').subscribe({
      next: (list) => {
        this.patients.set(
          list.map((p: any) => ({
            id: p.id,
            code: p.id?.substring(0, 8) ?? '—',
            nom: p.nom ?? '',
            prenom: p.prenom ?? '',
            sexe: p.sexe ?? '',
            dateAdmission: p.dateAdmission ?? '',
            numeroAssurance: p.numeroAssurance ?? '',
            etatPatient: p.etatPatient ?? 'PERMANENT'
          }))
        );
      },
      error: () => this.patients.set([])
    });
  }

  savePatient(data: PatientFormData): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;

    const toDateStr = (v: any): string | undefined => {
      if (!v) return undefined;
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v);
    };

    this.api.createPatient({
      nom: data.nom,
      prenom: data.prenom,
      sexe: data.sexe,
      dateAdmission: toDateStr(data.dateAdmission) || new Date().toISOString().slice(0, 10),
      dateNaissance: toDateStr(data.dateNaissance),
      numeroAssurance: data.numeroAssurance,
      typePatient: (data.typePatient as any) || 'NON_VACANCIER',
      attestationDebut: toDateStr(data.attestationDebut) || undefined,
      attestationFin: toDateStr(data.attestationFin) || undefined,
      centerId,
      userId: this.authSession.username() ?? 'front-demo',
      // Extended
      codePatient: data.codePatient,
      civilite: data.civilite,
      groupeSanguin: data.groupeSanguin,
      nombreEnfants: data.nombreEnfants,
      enSommeil: data.enSommeil,
      lieuNaissance: data.lieuNaissance,
      situationFamiliale: data.situationFamiliale,
      profession1: data.profession1,
      profession2: data.profession2,
      adresse: data.adresse,
      telPersonnel: data.telPersonnel,
      telMobile: data.telMobile,
      telBureau: data.telBureau,
      email: data.email,
      etatPatient: data.etatPatient,
      qualiteAssure: data.qualiteAssure,
      observation: data.observation,
      sousKt: data.sousKt,
      centrePayeurId: (data as any).centrePayeurId || undefined,
      medecinTraitantId: (data as any).medecinTraitantId || undefined,
      salleId: (data as any).salleId || undefined,
      positionId: (data as any).positionId || undefined,
      transporteurAllerId: (data as any).transporteurAllerId || undefined,
      transporteurRetourId: (data as any).transporteurRetourId || undefined,
      jourDimanche: data.jourDimanche,
      jourLundi: data.jourLundi,
      jourMardi: data.jourMardi,
      jourMercredi: data.jourMercredi,
      jourJeudi: data.jourJeudi,
      jourVendredi: data.jourVendredi,
      jourSamedi: data.jourSamedi,
      assureNom: data.assureNom,
      assurePrenom: data.assurePrenom,
      assureSexe: data.assureSexe,
      assureDateNaissance: toDateStr(data.assureDateNaissance),
      assureTelPersonnel: data.assureTelPersonnel,
      assureAdresse: data.assureAdresse,
      assureGroupeSanguin: data.assureGroupeSanguin
    }).subscribe({
      next: (response) => {
        this.currentPatientId.set(response.id);
        this.feedback.set('Patient enregistré avec succès');
        this.showPatientForm.set(false);
        this.loadPatients();
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  onSelectPatient(row: PatientRow): void {
    this.currentPatientId.set(row.id);
    this.feedback.set(`Patient sélectionné: ${row.nom} ${row.prenom}`);
  }

  createDemoPatient(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) {
      this.feedback.set('Aucun centre selectionne.');
      return;
    }

    const now = new Date().toISOString().slice(0, 10);
    const end = new Date(Date.now() + 1000 * 60 * 60 * 24 * 90).toISOString().slice(0, 10);

    this.api.createPatient({
      nom: 'Dialyse',
      prenom: 'Patient Demo',
      sexe: 'F',
      dateAdmission: now,
      dateNaissance: '1985-05-20',
      numeroAssurance: `ASSUR-${Date.now()}`,
      typePatient: 'NON_VACANCIER',
      attestationDebut: now,
      attestationFin: end,
      centerId,
      userId: this.authSession.username() ?? 'front-demo'
    }).subscribe({
      next: (response) => {
        this.currentPatientId.set(response.id);
        this.feedback.set(`Patient cree (${response.id})`);
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  createDemoPec(): void {
    const centerId = this.store.currentCenterId();
    const patientId = this.currentPatientId();
    if (!centerId || !patientId) {
      this.feedback.set('Creez un patient avant de creer une PEC.');
      return;
    }

    const now = new Date().toISOString().slice(0, 10);
    const end = new Date(Date.now() + 1000 * 60 * 60 * 24 * 30).toISOString().slice(0, 10);

    this.api.createPec({
      patientId,
      centerId,
      userId: this.authSession.username() ?? 'front-demo',
      dateDebutDemande: now,
      dateFinDemande: end
    }).subscribe({
      next: (response) => {
        this.currentPecId.set(response.id);
        this.currentPecStatus.set(response.status);
        this.sessionAllowed.set(false);
        this.feedback.set(`PEC creee (${response.status})`);
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  validatePec(): void {
    const centerId = this.store.currentCenterId();
    const pecId = this.currentPecId();
    if (!centerId || !pecId) {
      this.feedback.set('Aucune PEC a valider.');
      return;
    }

    this.api.validatePec(pecId, centerId, this.authSession.username() ?? 'front-demo').subscribe({
      next: (response) => {
        this.currentPecStatus.set(response.status);
        this.feedback.set(`PEC ${response.status}`);
        this.checkSessionAllowed();
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  closePec(): void {
    const centerId = this.store.currentCenterId();
    const pecId = this.currentPecId();
    if (!centerId || !pecId) {
      this.feedback.set('Aucune PEC a cloturer.');
      return;
    }

    this.api.closePec(pecId, centerId, this.authSession.username() ?? 'front-demo').subscribe({
      next: (response) => {
        this.currentPecStatus.set(response.status);
        this.feedback.set(`PEC ${response.status}`);
        this.checkSessionAllowed();
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  checkSessionAllowed(): void {
    const centerId = this.store.currentCenterId();
    const pecId = this.currentPecId();
    if (!centerId || !pecId) {
      this.sessionAllowed.set(null);
      return;
    }

    this.api.sessionAllowed(pecId, centerId, this.authSession.username() ?? 'front-demo').subscribe({
      next: (response) => {
        this.sessionAllowed.set(response.allowed);
      },
      error: (err) => this.feedback.set(this.extractError(err))
    });
  }

  private extractError(err: unknown): string {
    const detail = (err as { error?: { detail?: string } })?.error?.detail;
    if (detail) {
      return detail;
    }
    return 'Erreur technique. Verifiez que le backend est demarre.';
  }
}
