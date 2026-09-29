# Jeu de données RENADIAL (PostgreSQL)

| Fichier                   | Rôle                                                                                                    |
|---------------------------|---------------------------------------------------------------------------------------------------------|
| `00_purge.sql`            | Vide **toutes** les tables du schéma `public` (liste dynamique, `TRUNCATE … RESTART IDENTITY CASCADE`). |
| `01_dataset_renadial.sql` | Charge le jeu complet (77 tables, sauf `license`). Refuse de s'exécuter si la base n'est pas vide.      |
| `schema_prod.sql`         | Schéma de référence extrait de la production (`pg_dump --schema-only`).                                 |

## Exécution

```bash
# depuis le serveur (conteneur Docker)
docker exec -i hemodialyse-postgres-1 psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 < 00_purge.sql
docker exec -i hemodialyse-postgres-1 psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 < 01_dataset_renadial.sql
```

Durée mesurée : environ 70 s. Après le chargement, **redémarrer le backend** pour vider ses caches (licences,
référentiels).

## Contenu (30 patients/centre, paramètre `_cfg.nb_patients`)

- Société **RENADIAL** et 17 centres : ROUIBA, DEB, BAINEM, ANNABA 1, ANNABA 2, SKIKDA, CONSTANTINE, EL KHROUB,
  GUELMA, BATNA, KHENCHELA, SIDI BEL ABBES 1, SIDI BEL ABBES 2, ESSEDIKIA, HAI ESSALAM, TLEMCEN 1, TLEMCEN 2.
- 5 forfaits par centre : `HD-CONV` 5 600 DA, `HD-EPO` 7 200, `HDF-OL` 7 800, `HD-KT` 6 200, `HD-VAC` 9 000.
- Pour chaque patient : 1 assuré, 1 attestation, 1 prise en charge validée, et un dossier médical complet.
  Le dossier contient : antécédents, allergie, sérologies semestrielles, abords vasculaires, prescriptions, bilans
  mensuels, observations LOINC, examens, ordonnances trimestrielles, EPO et fer, alertes, et un bilan pré-greffe
  pour les patients éligibles.
- Cas particuliers dans chaque centre : 1 vacancier (séjour de 4 semaines), 1 patient décédé en cours de période,
  2 admissions récentes et des porteurs VHB/VHC en salle d'isolement.
- Séances sur les 12 mois clos, toutes au statut `FACTUREE`. Le mois en cours contient des séances `SIGNEE`,
  `VALIDEE` et `CREE`.
- Factures mensuelles, dont certaines regroupent plusieurs forfaits. Les règlements sont :
    - complets à M-3 et avant ;
    - partiels à M-2 ;
    - absents pour les mois plus récents.
- Stock complet : bons de commande, bons de réception, lots, sorties par séance, mouvements et PMP.
- Comptabilité SCF : écritures VE et BQ équilibrées. Pilotage direction : 12 instantanés et historique d'alertes.

Toutes les dates sont calculées par rapport à la date d'exécution (`current_date`). Les UUID sont déterministes :
la société et le centre ROUIBA reprennent les identifiants de production.

## Comptes (mot de passe : `Renadial@2026!`, à changer)

| Identifiant                                                                                       | Rôle                                                                                                    |
|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `superadmin`                                                                                      | SUPERADMIN (sociétés, centres, licences)                                                                |
| `direction.renadial`                                                                              | DIRECTION (session société)                                                                             |
| `admin.renadial`                                                                                  | ADMIN sur les 17 centres                                                                                |
| `admin.<c>`, `medecin1.<c>`, `medecin2.<c>`, `infirmier1.<c>`, `infirmier2.<c>`, `secretaire.<c>` | par centre, `<c>` ∈ rou, deb, bai, an1, an2, ski, cst, khr, gue, bat, khe, sb1, sb2, ess, hai, tl1, tl2 |

## ⚠ Licences

La table `license` reste vide. Une licence est un jeton RS256 signé par la clé privée de l'autorité, on ne peut donc
pas la créer en SQL. Tant qu'un centre n'a pas de licence, ses utilisateurs reçoivent une erreur HTTP 402.

Après le chargement :

1. se connecter en `superadmin` ;
2. émettre une licence par centre, avec au moins 7 postes par centre (6 comptes locaux + `admin.renadial`).

