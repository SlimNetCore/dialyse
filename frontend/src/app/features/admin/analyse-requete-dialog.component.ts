import {DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {PerformanceBaseStore} from './state/performance-base.store';

export interface AnalyseRequeteDialogData {
  /** Identifiant PostgreSQL de la requête mesurée : seul élément transmis au serveur. */
  id: string;
  requete: string;
}

/**
 * Plan d'exécution d'une requête mesurée, établi sans l'exécuter : quelles tables sont lues en entier, et si un index
 * serait utile. Les suggestions sont des pistes à valider (plan générique : valeurs de paramètres inconnues).
 */
@Component({
  selector: 'app-analyse-requete-dialog',
  standalone: true,
  imports: [DecimalPipe, TranslateModule, MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatTooltipModule],
  templateUrl: './analyse-requete-dialog.component.html',
  styleUrl: './analyse-requete-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyseRequeteDialogComponent implements OnInit, OnDestroy {
  protected readonly data = inject<AnalyseRequeteDialogData>(MAT_DIALOG_DATA);
  protected readonly store = inject(PerformanceBaseStore);
  /** Index suggéré dont l'ordre SQL vient d'être copié (retour visuel de quelques secondes). */
  protected readonly copie = signal<string | null>(null);

  ngOnInit(): void {
    void this.store.analyser(this.data.id);
  }

  ngOnDestroy(): void {
    this.store.fermerAnalyse();
  }

  protected async copier(sql: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(sql);
      this.copie.set(sql);
      setTimeout(() => this.copie.set(null), 2000);
    } catch {
      this.copie.set(null);
    }
  }
}
