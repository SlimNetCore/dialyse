ALTER TABLE report_template ADD COLUMN IF NOT EXISTS data_source_sql CLOB;
ALTER TABLE report_template ADD COLUMN IF NOT EXISTS header_image CLOB;
ALTER TABLE report_template ADD COLUMN IF NOT EXISTS footer_image CLOB;

-- Default advanced SQL examples with joins
UPDATE report_template
SET data_source_sql =
    'SELECT p.nom AS patient_nom, p.prenom AS patient_prenom, p.numero_assurance AS nss, cp.nom AS centre_payeur_nom, cp.code AS centre_payeur_code, a.nom AS agence_nom, ca.nom AS caisse_nom '
    || 'FROM patient p '
    || 'LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id '
    || 'LEFT JOIN agence a ON a.id = cp.agence_id '
    || 'LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id '
    || 'WHERE p.center_id = :centerId AND p.id = :patientId'
WHERE code = 'ATTESTATION_STD' AND (data_source_sql IS NULL OR data_source_sql = '');

UPDATE report_template
SET data_source_sql =
    'SELECT p.nom AS patient_nom, p.prenom AS patient_prenom, pe.date_debut_demande AS pec_debut, pe.date_fin_demande AS pec_fin, pe.statut AS pec_statut, f.libelle AS forfait_libelle '
    || 'FROM patient p '
    || 'LEFT JOIN pec pe ON pe.patient_id = p.id AND pe.center_id = p.center_id '
    || 'LEFT JOIN forfait f ON f.id = pe.forfait_demande_id '
    || 'WHERE p.center_id = :centerId AND p.id = :patientId '
    || 'ORDER BY pe.date_fin_demande DESC'
WHERE code = 'PEC_STD' AND (data_source_sql IS NULL OR data_source_sql = '');
