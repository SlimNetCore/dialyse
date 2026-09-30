import {inject, Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {
  ReferentialFieldDef,
  ReferentialKindDef,
  ValidationIssue
} from '../../../core/api/referential-admin-api.service';
import {issueTranslation} from './referential-admin.util';

/** Libellés traduits (référentiel, champ, valeur, anomalie), avec repli sur le texte fourni par le backend. */
@Injectable({providedIn: 'root'})
export class ReferentialLabels {
  private readonly translate = inject(TranslateService);

  kind(kind: ReferentialKindDef): string {
    return this.orFallback(`ADMIN.REFERENTIALS.KINDS.${kind.slug}`, kind.label);
  }

  field(field: ReferentialFieldDef): string {
    return this.orFallback(`ADMIN.REFERENTIALS.FIELDS.${field.key}`, field.label);
  }

  enumValue(value: string): string {
    return this.orFallback(`ADMIN.REFERENTIALS.ENUM.${value}`, value);
  }

  /** Message d'une anomalie dans la langue courante ; à défaut, le message français du serveur. */
  issue(issue: ValidationIssue, kind: ReferentialKindDef | null): string {
    const field = kind?.fields.find((f) => f.key === issue.field);
    const {key, params} = issueTranslation(issue, field ? this.field(field) : (issue.field ?? ''));
    const target = issue.params['target'] ? this.orFallback(`ADMIN.REFERENTIALS.KINDS.${issue.params['target']}`, issue.params['target']) : '';
    const translated = this.translate.instant(key, {...params, target});
    return translated && translated !== key ? translated : issue.message;
  }

  private orFallback(key: string, fallback: string): string {
    const translated = this.translate.instant(key);
    return translated && translated !== key ? translated : fallback;
  }
}

