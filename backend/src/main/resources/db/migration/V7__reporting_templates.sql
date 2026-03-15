CREATE TABLE IF NOT EXISTS report_template (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    page_format VARCHAR(20) NOT NULL DEFAULT 'A4',
    orientation VARCHAR(20) NOT NULL DEFAULT 'PORTRAIT',
    layout_mode VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    field_schema CLOB,
    template_html CLOB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_report_template_center_code ON report_template(center_id, code);
CREATE INDEX IF NOT EXISTS ix_report_template_center_type ON report_template(center_id, report_type);

-- Default templates (center principal)
MERGE INTO report_template (id, center_id, code, name, report_type, page_format, orientation, layout_mode, field_schema, template_html, active)
KEY (id)
VALUES (
    '70000001-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'ATTESTATION_STD',
    'Attestation ouverture de droit - Standard',
    'ATTESTATION',
    'A4',
    'PORTRAIT',
    'STANDARD',
    '{"sections":["header","patient","dates","signature"]}',
    '<html><head><meta charset="UTF-8"><style>body{font-family:Arial;padding:30px}h1{color:#1b5e20}.box{border:1px solid #ddd;padding:12px;border-radius:8px}</style></head><body><h1>Attestation d\'ouverture de droit</h1><div class="box"><p><b>Patient:</b> {{patient.nom}} {{patient.prenom}}</p><p><b>NSS:</b> {{patient.numeroAssurance}}</p><p><b>Date début:</b> {{attestation.dateDebut}}</p><p><b>Date fin:</b> {{attestation.dateFin}}</p><p><b>Centre:</b> {{center.name}}</p></div></body></html>',
    TRUE
);

MERGE INTO report_template (id, center_id, code, name, report_type, page_format, orientation, layout_mode, field_schema, template_html, active)
KEY (id)
VALUES (
    '70000001-0000-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'PEC_STD',
    'Prise en charge - Standard',
    'PEC',
    'A4',
    'PORTRAIT',
    'STANDARD',
    '{"sections":["header","patient","demande","accord"]}',
    '<html><head><meta charset="UTF-8"><style>body{font-family:Arial;padding:30px}h1{color:#1b5e20}.box{border:1px solid #ddd;padding:12px;border-radius:8px}</style></head><body><h1>Prise en charge</h1><div class="box"><p><b>Patient:</b> {{patient.nom}} {{patient.prenom}}</p><p><b>Début demande:</b> {{pec.dateDebutDemande}}</p><p><b>Fin demande:</b> {{pec.dateFinDemande}}</p><p><b>Statut:</b> {{pec.statut}}</p><p><b>Centre:</b> {{center.name}}</p></div></body></html>',
    TRUE
);

MERGE INTO report_template (id, center_id, code, name, report_type, page_format, orientation, layout_mode, field_schema, template_html, active)
KEY (id)
VALUES (
    '70000001-0000-0000-0000-000000000003',
    '11111111-1111-1111-1111-111111111111',
    'FICHE_SIGNALETIQUE_STD',
    'Fiche signalétique - Standard',
    'FICHE_SIGNALETIQUE',
    'A4',
    'PORTRAIT',
    'STANDARD',
    '{"sections":["identite","contact","affectation"]}',
    '<html><head><meta charset="UTF-8"><style>body{font-family:Arial;padding:30px}h1{color:#1b5e20}.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.card{border:1px solid #ddd;padding:10px;border-radius:8px}</style></head><body><h1>Fiche signalétique patient</h1><div class="grid"><div class="card"><b>Nom:</b> {{patient.nom}}</div><div class="card"><b>Prénom:</b> {{patient.prenom}}</div><div class="card"><b>Sexe:</b> {{patient.sexe}}</div><div class="card"><b>Date naissance:</b> {{patient.dateNaissance}}</div><div class="card"><b>Adresse:</b> {{patient.adresse}}</div><div class="card"><b>Tél:</b> {{patient.telMobile}}</div></div></body></html>',
    TRUE
);

