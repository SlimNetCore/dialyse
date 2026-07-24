import {ChangeDetectionStrategy, Component, inject, Input,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {TranslateModule, TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'app-cahier-step-paramedical',
  standalone: true,
  imports: [CommonModule, MatCardModule, TranslateModule],
  templateUrl: './cahier-step-paramedical.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-step-paramedical.component.css',
})
export class CahierStepParamedicalComponent {
  @Input() patientId!: string;
  private readonly translate = inject(TranslateService);
  @Input() seanceDate: string | null = null;
  @Input() seanceStatus: string | null = null;
  @Input() paramedicalData: Record<string, unknown> | null = null;

  readonly summaryFields = [
    {key: 'poidsAvantKg', label: 'CAHIER.PARAMEDICAL_POIDS_AVANT'},
    {key: 'poidsApresKg', label: 'CAHIER.PARAMEDICAL_POIDS_APRES'},
    {key: 'taAvant', label: 'CAHIER.PARAMEDICAL_TA_AVANT'},
    {key: 'taApres', label: 'CAHIER.PARAMEDICAL_TA_APRES'},
    {key: 'dureeMinutes', label: 'CAHIER.PARAMEDICAL_DUREE'},
    {key: 'debitSangMlMin', label: 'CAHIER.PARAMEDICAL_DEBIT_SANG'},
    {key: 'ultrafiltrationMl', label: 'CAHIER.PARAMEDICAL_UF'},
    {key: 'typeDialysat', label: 'CAHIER.PARAMEDICAL_DIALYSAT'},
    {key: 'anticoagulant', label: 'CAHIER.PARAMEDICAL_ANTICOAGULANT'},
    {key: 'incidents', label: 'CAHIER.PARAMEDICAL_INCIDENTS'},
  ] as const;

  formatValue(value: unknown): string {
    if (value === null || value === undefined) return '-';
    if (typeof value === 'boolean') return value ? this.translate.instant('COMMON.YES') : this.translate.instant('COMMON.NO');
    if (typeof value === 'number') return String(value);
    const text = String(value).trim();
    return text || '-';
  }

}

