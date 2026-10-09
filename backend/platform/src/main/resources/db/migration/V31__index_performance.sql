-- Index de performance : requêtes par centre + patient/période sur les tables les plus lues (séances, stock,
-- comptabilité, dossier médical). Constat : seances n'avait que (center_id, facture_id), stock_movements aucun index
-- sur center_id, lignes_ecriture / administrations_anemie / biologie / prescriptions aucun index du tout.
-- Les tables ont moins d'un million de lignes : CREATE INDEX simple (verrou de courte durée), idempotent.
-- Un index (a, b, c) sert aussi les tris décroissants sur c (parcours inverse) : pas de DESC nécessaire.
-- Les mêmes index sont déclarés en @Index sur les entités JPA pour que H2 (développement) reste aligné.

CREATE INDEX IF NOT EXISTS idx_seances_center_date ON seances (center_id, date_seance);
CREATE INDEX IF NOT EXISTS idx_seances_center_patient ON seances (center_id, patient_id, date_seance);

CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_date ON stock_movements (center_id, created_at);

-- La clé étrangère vers ecritures_comptables n'indexe pas la table enfant : lecture des lignes d'une écriture.
CREATE INDEX IF NOT EXISTS idx_lignes_ecriture_ecriture ON lignes_ecriture (ecriture_id);

CREATE INDEX IF NOT EXISTS idx_admin_anemie_patient ON administrations_anemie (center_id, patient_id, date_administration);
CREATE INDEX IF NOT EXISTS idx_resultats_patient_date ON resultats_analyses (center_id, patient_id, date_prelevement);
CREATE INDEX IF NOT EXISTS idx_obs_bio_patient_date ON observations_biologiques (center_id, patient_id, date_prelevement);
CREATE INDEX IF NOT EXISTS idx_prescriptions_patient ON prescriptions_medicales (center_id, patient_id, date_prescription);
