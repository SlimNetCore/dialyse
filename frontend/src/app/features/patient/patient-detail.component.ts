import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AppShellStore } from '../../core/state/app-shell.store';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatDatepickerModule, MatNativeDateModule,
    MatSnackBarModule
  ],
  template: `
    <mat-card class="detail-card">
      <div class="header">
        <button mat-icon-button (click)="router.navigate(['/patients'])"><mat-icon>arrow_back</mat-icon></button>
        <h2>Consultation / Modification patient</h2>
        <span class="spacer"></span>
        <button mat-stroked-button color="primary" (click)="print('FICHE_PATIENT')"><mat-icon>print</mat-icon> Imprimer fiche</button>
        <button mat-stroked-button color="primary" (click)="print('PEC')"><mat-icon>print</mat-icon> Imprimer PEC</button>
        <button mat-stroked-button color="primary" (click)="print('ATTESTATION')"><mat-icon>print</mat-icon> Imprimer attestation</button>
      </div>

      @if (loading()) {
        <p>Chargement...</p>
      } @else {
        <form [formGroup]="form" class="grid">
          <mat-form-field appearance="outline"><mat-label>Nom *</mat-label><input matInput formControlName="nom" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Prénom *</mat-label><input matInput formControlName="prenom" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Sexe *</mat-label><mat-select formControlName="sexe"><mat-option value="M">Masculin</mat-option><mat-option value="F">Féminin</mat-option></mat-select></mat-form-field>

          <mat-form-field appearance="outline"><mat-label>Date admission *</mat-label><input matInput [matDatepicker]="dpAdm" formControlName="dateAdmission" /><mat-datepicker-toggle matSuffix [for]="dpAdm"/><mat-datepicker #dpAdm /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Date naissance *</mat-label><input matInput [matDatepicker]="dpN" formControlName="dateNaissance" /><mat-datepicker-toggle matSuffix [for]="dpN"/><mat-datepicker #dpN /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>N° assurance *</mat-label><input matInput formControlName="numeroAssurance" /></mat-form-field>

          <mat-form-field appearance="outline"><mat-label>État patient</mat-label><mat-select formControlName="etatPatient"><mat-option value="PERMANENT">Permanent</mat-option><mat-option value="OCCASIONNEL">Occasionnel</mat-option><mat-option value="TRANSFERE">Transféré</mat-option><mat-option value="DECEDE">Décédé</mat-option><mat-option value="GREFFE">Greffé</mat-option><mat-option value="GUERRI">Guéri</mat-option><mat-option value="VACANCIER_LOCAL">Vacancier local</mat-option><mat-option value="VACANCIER_ETRANGER">Vacancier étranger</mat-option></mat-select></mat-form-field>
          @if (showDateEvenement()) {
            <mat-form-field appearance="outline"><mat-label>Date événement état</mat-label><input matInput [matDatepicker]="dpEvt" formControlName="dateEvenementEtat" /><mat-datepicker-toggle matSuffix [for]="dpEvt"/><mat-datepicker #dpEvt /></mat-form-field>
          }
          <mat-form-field appearance="outline"><mat-label>Téléphone mobile</mat-label><input matInput formControlName="telMobile" /></mat-form-field>

          <mat-form-field appearance="outline" class="span-3"><mat-label>Adresse</mat-label><input matInput formControlName="adresse" /></mat-form-field>
          <mat-form-field appearance="outline" class="span-3"><mat-label>Observation</mat-label><textarea matInput rows="3" formControlName="observation"></textarea></mat-form-field>
        </form>

        <div class="actions">
          <button mat-stroked-button (click)="router.navigate(['/patients'])">Annuler</button>
          <button mat-flat-button color="primary" (click)="save()" [disabled]="form.invalid || saving()">
            <mat-icon>save</mat-icon> Enregistrer modifications
          </button>
        </div>
      }
    </mat-card>
  `,
  styles: [`
    .detail-card { padding: 18px; }
    .header { display:flex; align-items:center; gap:8px; margin-bottom:12px; }
    h2 { margin:0; color:#1b5e20; font-size:1.2rem; }
    .spacer { flex:1; }
    .grid { display:grid; grid-template-columns: repeat(3,1fr); gap:10px; }
    .span-3 { grid-column: 1 / -1; }
    .actions { display:flex; justify-content:flex-end; gap:8px; margin-top:12px; }
  `]
})
export class PatientDetailComponent implements OnInit {
  readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly store = inject(AppShellStore);
  private readonly snack = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly saving = signal(false);
  private patientId = '';

  form = this.fb.group({
    civilite: [''],
    nom: ['', Validators.required],
    prenom: ['', Validators.required],
    sexe: ['', Validators.required],
    dateAdmission: [null as any, Validators.required],
    dateNaissance: [null as any, Validators.required],
    numeroAssurance: ['', Validators.required],
    etatPatient: ['PERMANENT'],
    dateEvenementEtat: [null as any],
    telMobile: [''],
    adresse: [''],
    observation: ['']
  });

  showDateEvenement(): boolean {
    const e = this.form.get('etatPatient')?.value;
    return e === 'DECEDE' || e === 'GREFFE' || e === 'TRANSFERE';
  }

  ngOnInit(): void {
    this.patientId = this.route.snapshot.paramMap.get('id') ?? '';
    const centerId = this.store.currentCenterId();
    if (!this.patientId || !centerId) return;

    this.api.getPatient(this.patientId, centerId, this.auth.username() ?? 'demo').subscribe({
      next: (p: any) => {
        this.form.patchValue({
          civilite: p.civilite ?? '',
          nom: p.nom ?? '',
          prenom: p.prenom ?? '',
          sexe: p.sexe ?? '',
          dateAdmission: p.dateAdmission ?? null,
          dateNaissance: p.dateNaissance ?? null,
          numeroAssurance: p.numeroAssurance?.value ?? p.numeroAssurance ?? '',
          etatPatient: p.etatPatient ?? 'PERMANENT',
          dateEvenementEtat: p.dateEvenementEtat ?? null,
          telMobile: p.telMobile ?? '',
          adresse: p.adresse ?? '',
          observation: p.observation ?? ''
        });
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Impossible de charger le patient', 'OK', { duration: 4000 });
      }
    });
  }

  private toDate(v: any): string | undefined {
    if (!v) return undefined;
    if (v instanceof Date) return v.toISOString().slice(0, 10);
    return String(v);
  }

  save(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.saving.set(true);

    const v = this.form.value;
    this.api.updatePatient(this.patientId, {
      centerId,
      userId: this.auth.username() ?? 'demo',
      nom: v.nom!, prenom: v.prenom!, sexe: v.sexe!,
      dateAdmission: this.toDate(v.dateAdmission)!,
      dateNaissance: this.toDate(v.dateNaissance),
      numeroAssurance: v.numeroAssurance!,
      typePatient: (v.etatPatient === 'VACANCIER_LOCAL' || v.etatPatient === 'VACANCIER_ETRANGER') ? 'VACANCIER' : 'NON_VACANCIER',
      civilite: v.civilite ?? '',
      etatPatient: v.etatPatient ?? 'PERMANENT',
      dateEvenementEtat: this.toDate(v.dateEvenementEtat),
      telMobile: v.telMobile ?? '',
      adresse: v.adresse ?? '',
      observation: v.observation ?? ''
    } as any).subscribe({
      next: () => {
        this.saving.set(false);
        this.snack.open('Patient modifié', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open(err?.error?.detail || 'Erreur modification', 'OK', { duration: 5000 });
      }
    });
  }

  print(typeDocument: string): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.api.printDocument(centerId, typeDocument, { patientId: this.patientId }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => this.snack.open('Erreur impression: ' + (err?.error?.text || err.message), 'OK', { duration: 5000 })
    });
  }
}

