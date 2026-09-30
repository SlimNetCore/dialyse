-- ============================================================
-- V2 : poids sec cible prescrit par le médecin
-- Porté par la prescription médicale (avec Qb, Qd, UF max, durée) : le poids sec en vigueur
-- pour une séance est celui de la dernière prescription datée au plus tard du jour de la séance.
-- ============================================================

ALTER TABLE public.prescriptions_medicales
    ADD COLUMN IF NOT EXISTS poids_sec_cible_kg numeric (5, 2);

ALTER TABLE public.prescriptions_medicales
DROP
CONSTRAINT IF EXISTS ck_prescription_poids_sec_cible;

ALTER TABLE public.prescriptions_medicales
    ADD CONSTRAINT ck_prescription_poids_sec_cible
        CHECK (poids_sec_cible_kg IS NULL OR poids_sec_cible_kg BETWEEN 20 AND 300);

