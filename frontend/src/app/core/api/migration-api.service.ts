import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './admin-api.service';
import {ValidationIssue} from './referential-admin-api.service';

export type MigrationColumnType =
  'TEXT'
  | 'DATE'
  | 'INTEGER'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'ENUM'
  | 'PHONE'
  | 'EMAIL'
  | 'DAYS'
  | 'REFERENCE';

export interface MigrationColumn {
  key: string;
  label: string;
  type: MigrationColumnType;
  requiredColumn: boolean;
  allowedValues: string[];
  defaultValue: string | null;
  example: string | null;
}

export interface MigrationEntityDef {
  slug: string;
  label: string;
  /** Ordre de chargement (1 = en premier). */
  order: number;
  columns: MigrationColumn[];
}

export type MigrationBatchStatus = 'EN_COURS' | 'TERMINE' | 'ANNULE';

export interface MigrationBatch {
  id: string;
  libelle: string;
  sourceSystem: string | null;
  dateDebutReprise: string | null;
  status: MigrationBatchStatus;
  createdBy: string | null;
  createdAt: string;
  closedAt: string | null;
}

export interface MigrationRun {
  entity: string;
  fileName: string | null;
  totalRows: number;
  created: number;
  updated: number;
  missingColumns: string[];
  ignoredColumns: string[];
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  valid: boolean;
  dryRun: boolean;
  applied: boolean;
  executedBy: string | null;
  executedAt: string;
}

export interface MigrationBatchDetail {
  batch: MigrationBatch;
  runs: MigrationRun[];
}

export interface ValueMapping {
  column: string;
  source: string;
  target: string;
}

export interface OpenBatchPayload {
  libelle: string;
  sourceSystem: string | null;
  dateDebutReprise: string | null;
}

/**
 * Reprise des données d'un système existant (ADMIN / SUPERADMIN, centre de la session uniquement) :
 * lot par centre, vérification et import de fichiers CSV / Excel, correspondances de valeurs, annulation.
 */
@Injectable({providedIn: 'root'})
export class MigrationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/migration`;

  entities(): Observable<MigrationEntityDef[]> {
    return this.http.get<MigrationEntityDef[]>(`${this.base}/entities`, {withCredentials: true});
  }

  template(slug: string, format: 'csv' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams().set('format', format);
    return this.http.get(`${this.base}/entities/${slug}/template`, {
      params,
      responseType: 'blob',
      withCredentials: true
    });
  }

  batches(centerId: string, page: number, size: number): Observable<PagedResponse<MigrationBatch>> {
    const params = new HttpParams().set('centerId', centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<MigrationBatch>>(`${this.base}/batches`, {params, withCredentials: true});
  }

  open(centerId: string, payload: OpenBatchPayload): Observable<MigrationBatch> {
    return this.http.post<MigrationBatch>(`${this.base}/batches`, {centerId, ...payload}, {withCredentials: true});
  }

  detail(centerId: string, batchId: string): Observable<MigrationBatchDetail> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<MigrationBatchDetail>(`${this.base}/batches/${batchId}`, {params, withCredentials: true});
  }

  importFile(centerId: string, batchId: string, entity: string, file: File, dryRun: boolean): Observable<MigrationRun> {
    const body = new FormData();
    body.append('file', file, file.name);
    const params = new HttpParams().set('centerId', centerId).set('dryRun', dryRun);
    return this.http.post<MigrationRun>(`${this.base}/batches/${batchId}/import/${entity}`, body, {
      params,
      withCredentials: true
    });
  }

  close(centerId: string, batchId: string): Observable<MigrationBatch> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.post<MigrationBatch>(`${this.base}/batches/${batchId}/close`, null, {
      params,
      withCredentials: true
    });
  }

  cancel(centerId: string, batchId: string): Observable<MigrationBatch> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.post<MigrationBatch>(`${this.base}/batches/${batchId}/cancel`, null, {
      params,
      withCredentials: true
    });
  }

  valueMappings(centerId: string): Observable<ValueMapping[]> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.get<ValueMapping[]>(`${this.base}/value-mappings`, {params, withCredentials: true});
  }

  saveValueMapping(centerId: string, mapping: ValueMapping): Observable<void> {
    return this.http.put<void>(`${this.base}/value-mappings`, {centerId, ...mapping}, {withCredentials: true});
  }

  deleteValueMapping(centerId: string, column: string, source: string): Observable<void> {
    const params = new HttpParams().set('centerId', centerId).set('column', column).set('source', source);
    return this.http.delete<void>(`${this.base}/value-mappings`, {params, withCredentials: true});
  }
}

