-- Migration V39 : mémoire de l'exclusion d'une séance de la facturation (slice F2, KB-FAC-13, ADR 0031).
--
-- ADDITIVE : deux colonnes nullables, aucun DROP, aucun DELETE, aucune donnée modifiée.
-- Exclure une séance de la facturation la passe au statut ABSENT et déclare une absence de patient avec son motif. Pour pouvoir
-- la réinclure tant qu'elle n'est pas facturée, la séance garde :
--   statut_avant_exclusion : son statut d'avant l'exclusion (VALIDEE ou SIGNEE) ;
--   exclusion_absence_id   : l'absence (table absence_patient) déclarée par l'exclusion, retirée à la réinclusion.
-- Les séances déjà ABSENT (absences réelles, exclusions antérieures à la slice) gardent NULL dans les deux colonnes : elles ne
-- se réincluent pas. Contrôle de cohérence : .ddd/migration/verifications-production.md (point 18).

ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS statut_avant_exclusion VARCHAR (20);
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS exclusion_absence_id UUID;
