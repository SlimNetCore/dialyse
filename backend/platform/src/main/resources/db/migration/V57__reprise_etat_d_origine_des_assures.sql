-- S6b-3 (ADR 0134, KB-S6a-2 CORRIGÉ) : l'annulation d'un lot de reprise restaure un assuré seulement rapproché.
-- Patient et couverture mémorise l'état de l'assuré (texte encodé v1) dans la correspondance d'identifiants, juste avant sa première
-- reprise. Les correspondances existantes gardent NULL : leur annulation reste ce qu'elle était (rien n'est restauré).
--
-- Additive : une colonne nullable, aucune donnée lue ni modifiée, aucune contrainte, aucun risque pour le démarrage.
-- Contrôle de lecture seule après déploiement : `.ddd/migration/verifications-production.md` §87.
--
-- TEXT et non VARCHAR(4000) : le mémento URL-encodé de neuf champs peut atteindre 18 467 caractères au pire cas du schéma `assure`
-- (huit champs de 255 caractères de trois octets UTF-8, encodés en neuf caractères chacun) ; voir l'ADR 0134.

ALTER TABLE public.migration_id_map ADD COLUMN IF NOT EXISTS etat_origine TEXT;
