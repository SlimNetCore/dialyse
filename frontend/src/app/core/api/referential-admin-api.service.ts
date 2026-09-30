import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './admin-api.service';

export type ReferentialFieldType = 'TEXT' | 'DECIMAL' | 'ENUM' | 'PHONE' | 'REFERENCE';

/** Champ d'un référentiel (décrit par le backend : formulaire, colonnes, aide à l'import). */
export interface ReferentialFieldDef {
  key: string;
  label: string;
  type: ReferentialFieldType;
  required: boolean;
  /** La colonne doit figurer dans un fichier d'import (obligatoire et sans valeur par défaut). */
  requiredColumn: boolean;
  maxLength: number;
  allowedValues: string[];
  defaultValue: string | null;
  /** Slug du référentiel ciblé (champ REFERENCE). */
  reference: string | null;
  example: string | null;
}

export interface ReferentialKindDef {
  slug: string;
  label: string;
  /** 1 = sans prérequis ; 2/3 = dépend d'un référentiel à importer avant. */
  importOrder: number;
  naturalKey: string[];
  fields: ReferentialFieldDef[];
}

export interface ReferentialEntry {
  id: string;
  values: Record<string, string | null>;
  /** Libellé affichable des champs REFERENCE (ex. « S1 · Salle 1 »). */
  references: Record<string, string>;
}

export interface ValidationIssue {
  /** Ligne du fichier (0 = anomalie globale / saisie de formulaire). */
  row: number;
  field: string | null;
  code: string;
  message: string;
  params: Record<string, string>;
}

export interface ImportReport {
  kind: string;
  totalRows: number;
  created: number;
  updated: number;
  missingColumns: string[];
  ignoredColumns: string[];
  errors: ValidationIssue[];
  valid: boolean;
  applied: boolean;
}

export interface ReferentialListQuery {
  centerId: string;
  search?: string;
  page: number;
  size: number;
}

/**
 * Administration des référentiels du centre (ADMIN / SUPERADMIN) : saisie, import CSV/Excel vérifié et
 * modèles d'import. Le centre est toujours transmis ; le backend refuse tout centre autre que celui de la session.
 */
@Injectable({providedIn: 'root'})
export class ReferentialAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/referentials`;

  kinds(): Observable<ReferentialKindDef[]> {
    return this.http.get<ReferentialKindDef[]>(this.base, {withCredentials: true});
  }

  list(slug: string, query: ReferentialListQuery): Observable<PagedResponse<ReferentialEntry>> {
    let params = new HttpParams().set('centerId', query.centerId).set('page', query.page).set('size', query.size);
    if (query.search?.trim()) params = params.set('search', query.search.trim());
    return this.http.get<PagedResponse<ReferentialEntry>>(`${this.base}/${slug}`, {params, withCredentials: true});
  }

  create(slug: string, centerId: string, values: Record<string, string | null>): Observable<ReferentialEntry> {
    return this.http.post<ReferentialEntry>(`${this.base}/${slug}`, {centerId, values}, {withCredentials: true});
  }

  update(slug: string, id: string, centerId: string, values: Record<string, string | null>): Observable<ReferentialEntry> {
    return this.http.put<ReferentialEntry>(`${this.base}/${slug}/${id}`, {centerId, values}, {withCredentials: true});
  }

  delete(slug: string, id: string, centerId: string): Observable<void> {
    const params = new HttpParams().set('centerId', centerId);
    return this.http.delete<void>(`${this.base}/${slug}/${id}`, {params, withCredentials: true});
  }

  /** `dryRun` = vérification seule (rien n'est écrit) ; sinon import tout-ou-rien si le fichier est valide. */
  importFile(slug: string, centerId: string, file: File, dryRun: boolean): Observable<ImportReport> {
    const body = new FormData();
    body.append('file', file, file.name);
    const params = new HttpParams().set('centerId', centerId).set('dryRun', dryRun);
    return this.http.post<ImportReport>(`${this.base}/${slug}/import`, body, {params, withCredentials: true});
  }

  template(slug: string, format: 'csv' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams().set('format', format);
    return this.http.get(`${this.base}/${slug}/template`, {params, responseType: 'blob', withCredentials: true});
  }
}

