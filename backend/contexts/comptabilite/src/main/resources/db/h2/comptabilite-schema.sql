-- DDL H2 (tests et poste de développement) des tables dont la Comptabilité est propriétaire (S-DDL2b, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V1).

-- PL1 (d) : compteurs de numérotation des pièces (JDBC : NumerotationDesDocumentsJdbc, ADR 0185). Entités AppSetting, AppSettingId et
-- UserCenterAssignment retirées ; PL16 (ADR 0184) : la table morte user_center_assignment n'existe plus en H2 (Flyway inchangé).
CREATE TABLE IF NOT EXISTS app_settings (
    center_id UUID NOT NULL,
    cle VARCHAR(50) NOT NULL,
    dernier_compteur BIGINT NOT NULL,
    prefixe VARCHAR(20) NOT NULL,
    PRIMARY KEY (center_id, cle)
);
