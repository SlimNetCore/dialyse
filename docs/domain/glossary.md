# Glossaire metier

## Termes principaux
- Patient: personne prise en charge pour hemodialyse dans un centre donne.
- Assure: personne rattachee au patient pour la couverture assurance.
- Caisse: organisme d assurance qui contient plusieurs agences.
- Agence: entite dependante d une caisse qui contient plusieurs centres payeurs.
- Centre payeur: entite qui regle la prise en charge.
- Attestation d ouverture de droit: document de validite des droits assurance (date debut/date fin).
- Prise en charge (PEC): autorisation financiere de seances, avec statut cycle de vie.
- Seance: acte d hemodialyse planifie puis realise.
- Vacancier: type patient avec regles assurance specifiques.
- Centre d hemodialyse: unite organisationnelle de soins scopee par `center_id`.

## Identifiants et unicites
- `patient_code`: code interne genere automatiquement.
- `numero_assurance`: obligatoire, unique par centre.
- `numero_securite`: identifiant unique metier du patient, unique par centre.

## Statuts metier
- PEC `CREE`: demande initiale, non utilisable pour seance.
- PEC `VALIDEE`: accord active, autorise les seances.
- PEC `CLOTUREE`: fin de validite, non utilisable pour seance.

## Regles cardinales
- Toute donnee metier est isolee par `center_id`.
- Aucun acces cross-centre sans autorisation explicite.
- Aucune seance si PEC non `VALIDEE`.
- Pour non-vacancier, attestation valide obligatoire.

