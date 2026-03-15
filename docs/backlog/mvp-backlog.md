# Backlog MVP - Hemodialyse

## Epic E1 - Gestion patient
- US1: Creer un patient avec champs obligatoires et validations.
  - AC1: echec si nom/prenom/sexe/date admission absents.
  - AC2: echec si numero assurance duplique dans le meme centre.
- US2: Modifier et consulter patient dans le centre courant.
  - AC1: utilisateur non assigne au centre -> 403.

## Epic E2 - Assurance
- US3: Gerer caisse/agence/centre payeur.
  - AC1: relation Caisse -> Agence -> Centre payeur respectee.
- US4: Gerer assures multiples avec historique.
  - AC1: pas de suppression destructive d historique.

## Epic E3 - Attestation et PEC
- US5: Enregistrer attestation ouverture de droit.
  - AC1: date debut <= date fin.
- US6: Workflow PEC (`CREE`, `VALIDEE`, `CLOTUREE`).
  - AC1: transitions invalides refusees.
- US7: Bloquer seance si PEC non `VALIDEE`.
  - AC1: creation seance refusee en `CREE` et `CLOTUREE`.

## Epic E4 - Multi-centre et securite
- US8: Isoler toutes lectures/ecritures par `center_id`.
  - AC1: aucun enregistrement hors centre de l utilisateur.
- US9: Journaliser refus cross-centre.
  - AC1: event audit cree sur chaque refus.

## Epic E5 - Ressources centre
- US10: Gerer medecins, creneaux, salles, generateurs, transporteurs.
  - AC1: donnees scopees par centre.

