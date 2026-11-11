#!/bin/bash
# Rejeu de S6b-3 (ADR 0134, KB-S6a-2 : etat d'origine d'un assure rapproche, colonne migration_id_map.etat_origine) sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql, V57 comprise), verification de la colonne (additive,
# nullable, TEXT), puis les requetes ecrites par IdMappingJdbcAdapter (INSERT avec et sans etat d'origine, SELECT du lot, memento du
# pire cas de 18467 caracteres) et celles de PatientsDeLaRepriseJdbc (INSERT / UPDATE de la fiche) dans des transactions ANNULEES
# (rien n'est conserve).
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-s6b3-pg16-rejeu.sh 55851
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55851}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
[ -n "$(printf '%s' "$PW" | tee "$TMP/pw")" ] || exit 3 # le mot de passe va dans le fichier, jamais a l'ecran
trap '"$PGBIN/pg_ctl" -D "$TMP/data" -s stop -m fast || true; rm -rf "$TMP"' EXIT
SORTIE="$("$PGBIN/initdb" -D "$TMP/data" -U patient_couverture_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C 2>&1)" || { echo "$SORTIE"; exit 3; }
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 -s start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U patient_couverture_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do
  SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" 2>&1)" || { echo "ECHEC de $f"; echo "$SORTIE"; exit 3; }
done
echo "Chaine Flyway appliquee (derniere : $(cd "$MIG" && ls V*.sql | sort -V | tail -1))"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonne etat_origine : presente, nullable, TEXT (non bornee) ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='migration_id_map' AND column_name='etat_origine' AND is_nullable='YES' AND data_type='text'")" = "1" ] \
  || { echo "ATTENDU : migration_id_map.etat_origine nullable text"; RC=1; }
echo "colonne verifiee"

echo "=== Requetes de IdMappingJdbcAdapter sur le schema reel (transaction annulee) ==="
SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtA <<'SQL' 2>&1
BEGIN;
INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, etat_origine)
  VALUES (gen_random_uuid(), '99997800-0000-0000-0000-00000000000a', '99997800-0000-0000-0000-00000000b001', 'ASSURES', 'A1', 'ZR1', 'UPDATED', now(), 'v=1&nom=EXISTANT');
INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, etat_origine)
  VALUES (gen_random_uuid(), '99997800-0000-0000-0000-00000000000a', '99997800-0000-0000-0000-00000000b001', 'ASSURES', 'A2', 'ZR2', 'CREATED', now(), NULL);
SELECT entity, legacy_id, target_id, operation, etat_origine FROM migration_id_map
  WHERE center_id = '99997800-0000-0000-0000-00000000000a' AND batch_id = '99997800-0000-0000-0000-00000000b001' ORDER BY created_at;
ROLLBACK;
SQL
)" || { echo "ECHEC des requetes"; echo "$SORTIE"; RC=1; }
echo "$SORTIE"
echo "$SORTIE" | grep -q "ASSURES|A1|ZR1|UPDATED|v=1&nom=EXISTANT" || { echo "ATTENDU : etat d'origine relu"; RC=1; }

echo "=== Memento du pire cas du schema assure (8 champs de 255 caracteres de 3 octets, URL-encodes) : 18467 caracteres relus ==="
SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtA <<'SQL' 2>&1
BEGIN;
INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, etat_origine)
  VALUES (gen_random_uuid(), '99997800-0000-0000-0000-00000000000a', '99997800-0000-0000-0000-00000000b002', 'ASSURES', 'A9', 'ZR9', 'UPDATED', now(),
  'v=1&dateNaissance=1970-01-01' || repeat('&nom=' || repeat('%E2%82%AC', 255), 1) || repeat('&prenom=' || repeat('%E2%82%AC', 255), 1)
  || repeat('&sexe=' || repeat('%E2%82%AC', 255), 1) || repeat('&telPersonnel=' || repeat('%E2%82%AC', 255), 1)
  || repeat('&telMobile=' || repeat('%E2%82%AC', 255), 1) || repeat('&telBureau=' || repeat('%E2%82%AC', 255), 1)
  || repeat('&adresse=' || repeat('%E2%82%AC', 255), 1) || repeat('&groupeSanguin=' || repeat('%E2%82%AC', 255), 1));
SELECT 'LONGUEUR|' || length(etat_origine) FROM migration_id_map WHERE batch_id = '99997800-0000-0000-0000-00000000b002';
ROLLBACK;
SQL
)" || { echo "ECHEC du memento maximal"; echo "$SORTIE"; RC=1; }
echo "$SORTIE"
echo "$SORTIE" | grep -q "^LONGUEUR|18467$" || { echo "ATTENDU : memento maximal de 18467 caracteres relu"; RC=1; }

# Requetes de PatientsDeLaRepriseJdbc (INSERTION et MISE_A_JOUR, lignes 48-53) : meme liste de colonnes, meme ordre, memes litteraux
# `false`, preparees par PostgreSQL (types des parametres deduits du schema reel) puis executees dans une transaction ANNULEE.
echo "=== Requetes de PatientsDeLaRepriseJdbc sur le schema reel (transaction annulee) ==="
COLONNES=(code_patient numero_assurance nom prenom sexe date_naissance date_admission type_patient etat_patient qualite_assure civilite
  lieu_naissance situation_familiale nombre_enfants profession1 adresse tel_personnel tel_mobile tel_bureau email groupe_sanguin
  date_evenement_etat observation centre_payeur_id medecin_traitant_id salle_id position_id generateur_id transporteur_aller_id
  transporteur_retour_id jour_dimanche jour_lundi jour_mardi jour_mercredi jour_jeudi jour_vendredi jour_samedi assure_numero_assurance
  assure_sexe assure_nom assure_prenom assure_date_naissance assure_tel_personnel assure_adresse assure_groupe_sanguin assure_tel_mobile
  assure_tel_bureau)
# Garde de derive : la liste ci-dessus est celle du source Java (47 colonnes, meme ordre).
JAVA="$RACINE/backend/contexts/patient-couverture/src/main/java/com/hemodialyse/backend/contexts/patientcouverture/infrastructure/persistence/PatientsDeLaRepriseJdbc.java"
for c in "${COLONNES[@]}"; do grep -q "\"$c\"" "$JAVA" || { echo "DERIVE : colonne $c absente de PatientsDeLaRepriseJdbc"; RC=1; }; done
INS_COLS="$(IFS=,; printf '%s' "${COLONNES[*]}")"
INS_COLS="${INS_COLS//,/, }"
N=${#COLONNES[@]}
INS_VALS="" ; UPD_SET=""
for i in $(seq 1 "$N"); do
  INS_VALS="$INS_VALS, \$$((i + 3))"
  UPD_SET="$UPD_SET${UPD_SET:+, }${COLONNES[$((i - 1))]} = \$$i"
done
INSERTION="INSERT INTO patients (id, center_id, created_at, en_sommeil, sous_kt, epo_enabled, fer_enabled, $INS_COLS) VALUES (\$1, \$2, \$3, false, false, false, false$INS_VALS)"
MISE_A_JOUR="UPDATE patients SET $UPD_SET WHERE id = \$$((N + 1)) AND center_id = \$$((N + 2))"
FICHE="'ZP1', 'ZA1', 'NOM', 'Prenom', 'M', '1970-01-01', '2020-01-01', 'NON_VACANCIER', 'EN_TRAITEMENT', 'ASSURE_LUI_MEME', 'M.', 'Alger', 'MARIE', 2, 'Medecin', 'Adresse', '0550', '0661', '0770', 'a@b.dz', 'O+', '2020-02-02', 'observation', NULL, NULL, NULL, NULL, NULL, NULL, NULL, true, false, true, false, true, false, true, 'ZA1', 'M', 'NOM', 'Prenom', '1970-01-01', '0550', 'Adresse', 'O+', '0661', '0770'"
FICHE2="'ZP1', 'ZA1', 'NOM2', 'Prenom2', 'F', '1971-01-01', '2020-01-01', 'VACANCIER', 'EN_SOMMEIL', 'ENFANT', 'Mme', 'Oran', 'CELIBATAIRE', 3, 'Autre', 'Adresse2', '0551', '0662', '0771', 'c@d.dz', 'A+', '2020-03-03', 'observation2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, false, true, false, true, false, true, false, 'ZA1', 'F', 'NOM2', 'Prenom2', '1971-01-01', '0551', 'Adresse2', 'A+', '0662', '0771'"
SORTIE="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtA <<SQL 2>&1
BEGIN;
PREPARE insertion_fiche AS $INSERTION;
PREPARE mise_a_jour_fiche AS $MISE_A_JOUR;
EXECUTE insertion_fiche('99997800-0000-0000-0000-00000000f001', '99997800-0000-0000-0000-00000000000a', now(), $FICHE);
SELECT 'APRES-INSERTION|' || nom || '|' || en_sommeil || '|' || sous_kt || '|' || epo_enabled || '|' || fer_enabled || '|' || jour_dimanche || '|' || jour_lundi
  FROM patients WHERE id = '99997800-0000-0000-0000-00000000f001';
EXECUTE mise_a_jour_fiche($FICHE2, '99997800-0000-0000-0000-00000000f001', '99997800-0000-0000-0000-00000000000a');
SELECT 'APRES-MISE-A-JOUR|' || nom || '|' || etat_patient || '|' || jour_dimanche || '|' || jour_lundi || '|' || nombre_enfants
  FROM patients WHERE id = '99997800-0000-0000-0000-00000000f001' AND center_id = '99997800-0000-0000-0000-00000000000a';
ROLLBACK;
SQL
)" || { echo "ECHEC des requetes de la reprise des patients"; echo "$SORTIE"; RC=1; }
echo "$SORTIE"
echo "$SORTIE" | tr -d '\r' | grep -q "^APRES-INSERTION|NOM|false|false|false|false|true|false$" || { echo "ATTENDU : fiche inseree (litteraux false, jours)"; RC=1; }
echo "$SORTIE" | tr -d '\r' | grep -q "^APRES-MISE-A-JOUR|NOM2|EN_SOMMEIL|false|true|3$" || { echo "ATTENDU : fiche mise a jour"; RC=1; }
echo "=== Code de sortie : $RC ==="
exit $RC
