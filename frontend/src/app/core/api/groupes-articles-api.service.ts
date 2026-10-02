import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {PagedResponse} from './gmao-api.service';

/** Groupe nommé d'articles d'un centre (ex. « KIT CNAS »), dont la valorisation est suivie par la direction. */
export interface GroupeArticle {
  id: string;
  nom: string;
  description: string | null;
  nbArticles: number;
  articleIds: string[];
  updatedAt: string;
}

export interface GroupeArticlePayload {
  nom: string;
  description: string | null;
  articleIds: string[];
}

/**
 * Administration des groupes d'articles du centre actif. Le centre est toujours transmis (isolation
 * multi-centre) ; la liste est paginée.
 */
@Injectable({providedIn: 'root'})
export class GroupesArticlesApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/stock/groupes-articles`;

  list(centerId: string, page: number, size: number): Observable<PagedResponse<GroupeArticle>> {
    const params = new HttpParams().set('centerId', centerId).set('page', page).set('size', size);
    return this.http.get<PagedResponse<GroupeArticle>>(this.base, {params});
  }

  create(centerId: string, payload: GroupeArticlePayload): Observable<GroupeArticle> {
    return this.http.post<GroupeArticle>(this.base, payload, {params: new HttpParams().set('centerId', centerId)});
  }

  update(centerId: string, id: string, payload: GroupeArticlePayload): Observable<GroupeArticle> {
    return this.http.put<GroupeArticle>(`${this.base}/${id}`, payload, {params: new HttpParams().set('centerId', centerId)});
  }

  delete(centerId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, {params: new HttpParams().set('centerId', centerId)});
  }
}
