import {inject, Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {MigrationEntityDef} from '../../../core/api/migration-api.service';
import {ValidationIssue} from '../../../core/api/referential-admin-api.service';

/** Libellés de la reprise dans la langue courante, avec repli sur le texte français du serveur. */
@Injectable({providedIn: 'root'})
export class MigrationLabels {
  private readonly translate = inject(TranslateService);

  entity(entity: MigrationEntityDef): string {
    return this.orFallback(`ADMIN.MIGRATION.ENTITIES.${entity.slug}`, entity.label);
  }

  column(entity: MigrationEntityDef | null, key: string | null): string {
    if (!key) return '—';
    const column = entity?.columns.find((c) => c.key === key);
    return this.orFallback(`ADMIN.MIGRATION.COLUMNS.${key}`, column?.label ?? key);
  }

  value(code: string): string {
    return this.orFallback(`ADMIN.MIGRATION.VALUES.${code}`, code);
  }

  issue(issue: ValidationIssue, entity: MigrationEntityDef | null): string {
    const key = `ADMIN.MIGRATION.ISSUES.${issue.code}`;
    const target = issue.params['target']
      ? this.orFallback(`ADMIN.REFERENTIALS.KINDS.${issue.params['target']}`, issue.params['target']) : '';
    const translated = this.translate.instant(key, {...issue.params, field: this.column(entity, issue.field), target});
    return translated && translated !== key ? translated : issue.message;
  }

  private orFallback(key: string, fallback: string): string {
    const translated = this.translate.instant(key);
    return translated && translated !== key ? translated : fallback;
  }
}

