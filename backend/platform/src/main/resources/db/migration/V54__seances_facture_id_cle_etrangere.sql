-- Migration V54 : clé étrangère seances.facture_id -> factures.id (KB-SEA-74, ADR 0114, lot L3). ARGENT.
--
-- ADDITIVE ET SANS RISQUE POUR LE DÉMARRAGE : une seule contrainte, ajoutée NOT VALID. PostgreSQL ne relit PAS les lignes existantes à
-- l'ajout : une donnée de production sale (séance dont facture_id désigne une facture inexistante) NE PEUT PAS faire échouer Flyway ni
-- empêcher l'application de démarrer. Aucun DROP, aucun DELETE, aucune donnée modifiée. La contrainte s'applique dès maintenant aux
-- NOUVELLES écritures de facture_id (insertion, ou mise à jour de la colonne) : un identifiant orphelin est refusé, et une facture encore
-- liée à une séance ne peut plus être supprimée (refus voulu ; pas de ON DELETE CASCADE).
--
-- VALIDATION (À FAIRE À LA MAIN, hors Flyway, après nettoyage) :
--   1. Détecter les orphelins (SELECT seule ; point 62.2 de .ddd/migration/verifications-production.md) :
--        SELECT s.center_id, s.id, s.facture_id FROM seances s
--        LEFT JOIN factures f ON f.id = s.facture_id
--        WHERE s.facture_id IS NOT NULL AND f.id IS NULL;
--      Toute ligne est à réparer par décision écrite (restaurer la facture, ou remettre facture_id à NULL et le statut à VALIDEE).
--   2. Quand la requête rend 0 ligne :
--        ALTER TABLE seances VALIDATE CONSTRAINT fk_seances_facture;
--      (verrou SHARE UPDATE EXCLUSIVE : n'interrompt pas les lectures ni les écritures.)
-- Tant que la contrainte n'est pas validée, elle reste active pour les nouvelles écritures ; la garde applicative (liaison à une facture
-- existante du même centre, LiaisonDesSeancesJdbc) protège dans tous les cas, y compris sur H2.
--
-- PostgreSQL seulement : la base H2 des tests est bâtie par db/schema.sql (sans Flyway) et ne reçoit pas cette contrainte (H2 la
-- validerait et casserait les fixtures qui posent un facture_id sans créer la facture) ; le contrat PostgreSQL 16 l'exécute.
-- Pas de clé composite (id, center_id) : l'appartenance au centre reste contrôlée par la garde applicative et par la requête 62.2.

ALTER TABLE seances
    ADD CONSTRAINT fk_seances_facture FOREIGN KEY (facture_id) REFERENCES factures (id) NOT VALID;
