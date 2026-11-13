---
status: proposed
date: 2026-11-12
context: Documents (generic, SD16)
sources: [ADR 0140, DocumentRestController.java, ModeleDocumentCatalog.java, ModeleDocumentPrinter.java, ModeleDocumentTemplateService.java]
---
# Glossaire — Documents

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Document | Pièce imprimable ou exportable produite pour un centre (PDF, Excel) | `DocumentRendu` | `Document` (record du printer) | rapport, édition (sauf « rapport métier » côté contexte émetteur) | octets + type MIME + nom de fichier | proposed |
| Type de document | Nature d'un document, identifiée par un code stable (ex. cahier de dialyse, ordonnance) ; déclarée par le contexte qui possède la mise en page | `TypeDeDocument`, `TypeDeDocumentDeclare` | `type_document`, `ModeleDocumentCatalog.Entry` | catégorie, template type | code en majuscules, `DOCUMENT_TYPE_UNKNOWN` si absent | proposed |
| Modèle de document | Réglage d'un type pour un centre : gabarit actif, libellé, format d'impression, actif ou désactivé | `ModeleDeDocument` | `modele_document` | template, modèle (seul : ambigu avec « modèle de domaine ») | un seul actif par (centre, type) ; désactivé ⇒ `DOCUMENT_MODEL_INACTIVE` | proposed |
| Version de modèle | Dépôt daté d'un gabarit téléversé pour un modèle ; une seule active ; réinitialisable vers le gabarit par défaut | `VersionDeModele` | `modele_document_version` | révision | `activate`, `reset` | proposed |
| Gabarit | Fichier de mise en page (JRXML ou HTML) ; le gabarit par défaut est fourni par le contexte qui déclare le type | `Gabarit` | `chemin_jrxml`, `.jrxml` | modèle (réservé au modèle de document) | validé par liste blanche avant compilation | proposed |
| Rendu | Production d'un document à partir d'un gabarit et de données fournies par le contexte demandeur | `RenduDeDocument.rendre` | `JasperReportService`, `ModeleDocumentPrinter.print` | génération, impression (l'impression au sens papier est l'acte de l'utilisateur) | pas de SQL dans un gabarit livré | proposed |
| Format d'impression | Mise en page du support (ex. A4 portrait/paysage) du modèle | `FormatDImpression` | `format_impression` | — | propriété du modèle | proposed |
| Papier en-tête | En-tête et pied de page d'identité du centre posés sur un document | `PapierEnTeteDuCentre.habiller` | `SocieteLetterheadPort`, `DocumentIdentityProvider` | entête (sans tiret), letterhead | rendu tel quel si le document n'a pas de corps | proposed |
| Identité imprimée | Nom, adresse, logo et pied de la société et du centre tels qu'imprimés ; donnée d'Organisation, composée ici | `IdentiteImprimable` (port d'Organisation) | `SocieteJpaEntity`, `DocumentIdentityProvider` | — | Documents ne la possède pas | proposed |
| Logo | Image de la société imprimée dans l'en-tête ; validée à l'enregistrement | `LogoImage` | `LogoImageValidator`, `SocieteLogoService` | — | stockée avec la société (Organisation) | proposed |
| Provisionnement | Création du modèle par défaut d'un type pour un centre à la première impression (jamais en lecture seule) | `ProvisionnerLeModele` | `ModeleDocumentProvisioner` | seed | KB-PLN-51 | proposed |
| Demande d'impression | Requête d'un utilisateur pour un document : centre, type ou modèle, format souhaité, paramètres du client (DOC1) | `DemandeDImpression` | `PrintRequest` | commande d'impression | centre = centre de la session (sinon 403) ; entrée illisible = 400 | proposed |
| Paramètre réservé | Paramètre d'identité (`CENTER_ID`, `SOCIETE_*`, `CENTRE_*`) toujours fourni par le serveur ; la valeur du client est ignorée (KB-DOC1-2) | `ParametresReserves` | `stripReservedParams` | — | inventaire dans le domaine | proposed |
| Format de sortie | Forme du fichier rendu : tableur, HTML ou PDF ; un format inconnu est servi en PDF (dette KB-DOC1-5) | `FormatDeSortie` | `formatOverride` | — | propriété de la demande | proposed |
| Document imprimé | Résultat d'une impression : octets, format et nom de fichier | `DocumentImprime` | `ModeleDocumentPrinter.Document` | — | PDF en ligne, tableur en pièce jointe | proposed |
| Homonymes | « Rapport » : rendu technique (Documents) ≠ « Rapport PDF Direction » (Pilotage) ; « Modèle » ≠ modèle de domaine ; « Type » ≠ type de TVA/de notification | — | — | — | toujours qualifier : « type de document » | proposed |
| Moteur de rendu | Composant qui transforme un gabarit et des données en document (Jasper) ; porté par Documents depuis DOC2 (ADR 0162) | `MoteurDeRendu`, `MoteurDeRenduJasper` | `ModeleDocumentPrinter`, `JasperReportService` | générateur, imprimeur | refus = `RenduRefuse` (422, code stable) | proposed |
| Refus de rendu | Impossibilité de rendre : type inconnu, modèle désactivé, génération impossible | `RenduRefuse` | `BusinessException` (DOCUMENT_*) | erreur d'impression | codes `DOCUMENT_TYPE_UNKNOWN`, `DOCUMENT_MODEL_INACTIVE`, `DOCUMENT_GENERATION_FAILED` | proposed |
| Validation de sécurité d'un gabarit | Contrôle par liste blanche d'un gabarit téléversé avant compilation (aucune requête, ressource externe ni script) | `JrxmlSecurityValidator` | idem | antivirus | jamais affaiblie sans ADR | proposed |
