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
    {key: 'poidsSecCibleKg', label: 'CAHIER.PARAMEDICAL_POIDS_SEC_CIBLE'},
    {key: 'poidsAvantKg', label: 'CAHIER.PARAMEDICAL_POIDS_AVANT'},
    {key: 'surchargeHydriqueKg', label: 'CAHIER.PARAMEDICAL_SURCHARGE'},
    {key: 'poidsApresKg', label: 'CAHIER.PARAMEDICAL_POIDS_APRES'},
    {key: 'taAvant', label: 'CAHIER.PARAMEDICAL_TA_AVANT'},
    {key: 'taApres', label: 'CAHIER.PARAMEDICAL_TA_APRES'},
    {key: 'qbMlMin', label: 'CAHIER.PARAMEDICAL_QB'},
    {key: 'qdMlMin', label: 'CAHIER.PARAMEDICAL_QD'},
    {key: 'ufCibleMl', label: 'CAHIER.PARAMEDICAL_UF_CIBLE'},
    {key: 'dureeMinutes', label: 'CAHIER.PARAMEDICAL_DUREE'},
    {key: 'debitSangMlMin', label: 'CAHIER.PARAMEDICAL_DEBIT_SANG'},
    {key: 'ultrafiltrationMl', label: 'CAHIER.PARAMEDICAL_UF'},
    {key: 'typeDialyseur', label: 'CAHIER.PARAMEDICAL_DIALYSEUR'},
    {key: 'typeBain', label: 'CAHIER.PARAMEDICAL_TYPE_BAIN'},
    {key: 'typeDialysat', label: 'CAHIER.PARAMEDICAL_DIALYSAT'},
    {key: 'conductivite', label: 'CAHIER.PARAMEDICAL_CONDUCTIVITE'},
    {key: 'temperatureBain', label: 'CAHIER.PARAMEDICAL_TEMPERATURE_BAIN'},
    {key: 'anticoagulant', label: 'CAHIER.PARAMEDICAL_ANTICOAGULANT'},
    {key: 'anticoagType', label: 'CAHIER.PARAMEDICAL_ANTICOAG_TYPE'},
    {key: 'anticoagDoseInitiale', label: 'CAHIER.PARAMEDICAL_ANTICOAG_DOSE_INIT'},
    {key: 'anticoagDoseHoraire', label: 'CAHIER.PARAMEDICAL_ANTICOAG_DOSE_HORAIRE'},
    {key: 'nbRincages', label: 'CAHIER.PARAMEDICAL_NB_RINCAGES'},
    {key: 'volumeRincageMl', label: 'CAHIER.PARAMEDICAL_VOLUME_RINCAGE'},
    {key: 'heureArretHeparine', label: 'CAHIER.PARAMEDICAL_ARRET_HEPARINE'},
    {key: 'abordType', label: 'CAHIER.PARAMEDICAL_ABORD_TYPE'},
    {key: 'abordCote', label: 'CAHIER.PARAMEDICAL_ABORD_COTE'},
    {key: 'aiguilleCalibr', label: 'CAHIER.PARAMEDICAL_AIGUILLE'},
    {key: 'ordrePonction', label: 'CAHIER.PARAMEDICAL_ORDRE_PONCTION'},
    {key: 'aspectSite', label: 'CAHIER.PARAMEDICAL_ASPECT_SITE'},
    {key: 'incidentPonction', label: 'CAHIER.PARAMEDICAL_INCIDENT_PONCTION'},
    {key: 'incidentPonctionDetail', label: 'CAHIER.PARAMEDICAL_INCIDENT_PONCTION_DETAIL'},
    {key: 'taSystoliqueApres', label: 'CAHIER.PARAMEDICAL_TA_SYS_APRES'},
    {key: 'taDiastoliqueApres', label: 'CAHIER.PARAMEDICAL_TA_DIA_APRES'},
    {key: 'fcApres', label: 'CAHIER.PARAMEDICAL_FC_APRES'},
    {key: 'ktVRealise', label: 'CAHIER.PARAMEDICAL_KTV'},
    {key: 'ufReelleMl', label: 'CAHIER.PARAMEDICAL_UF_REELLE'},
    {key: 'incidents', label: 'CAHIER.PARAMEDICAL_INCIDENTS'},
    {key: 'incidentHypotension', label: 'CAHIER.PARAMEDICAL_INCIDENT_HYPOTENSION'},
    {key: 'incidentCrampes', label: 'CAHIER.PARAMEDICAL_INCIDENT_CRAMPES'},
    {key: 'incidentCephalees', label: 'CAHIER.PARAMEDICAL_INCIDENT_CEPHALEES'},
    {key: 'incidentFrissons', label: 'CAHIER.PARAMEDICAL_INCIDENT_FRISSONS'},
    {key: 'incidentNausees', label: 'CAHIER.PARAMEDICAL_INCIDENT_NAUSEES'},
    {key: 'incidentThrombose', label: 'CAHIER.PARAMEDICAL_INCIDENT_THROMBOSE'},
    {key: 'incidentAutre', label: 'CAHIER.PARAMEDICAL_INCIDENT_AUTRE'},
    {key: 'signatureInfirmierId', label: 'CAHIER.PARAMEDICAL_SIGNATURE_INFI'},
    {key: 'signatureAt', label: 'CAHIER.PARAMEDICAL_SIGNATURE_DATE'},
    {key: 'createdAt', label: 'CAHIER.PARAMEDICAL_CREATED_AT'},
    {key: 'updatedAt', label: 'CAHIER.PARAMEDICAL_UPDATED_AT'},
  ] as const;

  formatValue(value: unknown): string {
    if (value === null || value === undefined) return '-';
    if (typeof value === 'boolean') return value ? this.translate.instant('COMMON.YES') : this.translate.instant('COMMON.NO');
    if (typeof value === 'number') return String(value);
    const text = String(value).trim();
    return text || '-';
  }

  isArrayValue(value: unknown): boolean {
    return Array.isArray(value);
  }

  entries(value: unknown): Array<{ key: string; value: unknown }> {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
    return Object.entries(value as Record<string, unknown>).map(([key, val]) => ({key, value: val}));
  }
}

