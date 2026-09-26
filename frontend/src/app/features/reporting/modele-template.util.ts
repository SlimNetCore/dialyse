import type {ModeleViolation} from '../../core/api/backend-api.service';

/** Taille maximale d'un modèle JRXML accepté par le serveur (octets). */
export const MAX_TEMPLATE_BYTES = 512 * 1024;

/** Contrôle rapide avant envoi ; le serveur reste la seule autorité de validation. */
export function precheckTemplateFile(file: { name: string; size: number }): ModeleViolation | null {
  if (!file.name.toLowerCase().endsWith('.jrxml')) {
    return {code: 'FILE_TYPE', detail: file.name};
  }
  if (file.size === 0) {
    return {code: 'EMPTY', detail: file.name};
  }
  if (file.size > MAX_TEMPLATE_BYTES) {
    return {code: 'TOO_LARGE', detail: `${Math.ceil(file.size / 1024)} Ko`};
  }
  return null;
}

/** Extrait les anomalies d'une réponse 422 du serveur ; `null` si l'erreur n'est pas un refus de validation. */
export function extractViolations(error: unknown): ModeleViolation[] | null {
  const body = (error as { status?: number; error?: { violations?: unknown } } | null);
  if (body?.status !== 422 || !Array.isArray(body.error?.violations)) {
    return null;
  }
  return (body.error!.violations as Array<Partial<ModeleViolation>>)
    .filter((v) => typeof v?.code === 'string')
    .map((v) => ({code: v.code as string, detail: v.detail ?? ''}));
}

/** Nom de fichier proposé pour le téléchargement d'un modèle. */
export function templateFileName(code: string, version?: number): string {
  const safe = (code || 'modele').replace(/[^A-Za-z0-9_-]/g, '_').toLowerCase();
  return `${safe}${version != null ? `-v${version}` : ''}.jrxml`;
}
