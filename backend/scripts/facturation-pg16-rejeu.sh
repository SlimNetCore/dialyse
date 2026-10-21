#!/bin/bash
# Rejeu des slices F1 (referentiel de facturation), F2 (simulation : migration V39, ACL des seances, sequence), F3 (validation :
# migration V40, factures, reservation atomique de la sequence sous concurrence) F4 (reglements : migration V41 mode / reference /
# cle d'idempotence, index unique, lecture des situations, enregistrements simultanes) et F5 (lectures de pilotage : tableau de bord,
# synthese, synthese financiere publiee pour la Direction : sommes, extraction annee/mois, comptages distincts, ordre) et F6 (ports
# publies : periode facturee, factures emises et reglements encaisses hors reprise, reprise des soldes) sur un
# PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql), constat KB-FAC-07 sur le schema Flyway pur (l'ancien MERGE ... KEY de
# FacturationSettingsJdbcAdapter, syntaxe H2, doit ECHOUER), puis contrats PostgreSQL : parametres de facturation (contexte
# facturation, UPDATE puis INSERT) et regles de TVA (contexte comptabilite : ajout, lecture par identifiant, remplacement,
# desactivation).
# A lancer en arriere-plan avec un log : backend/scripts/facturation-pg16-rejeu.sh 55442 > rejeu.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55442}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U facturation_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U facturation_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || exit 3; done
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0

echo "=== Constat sur le schema Flyway PUR ==="
echo "-- KB-FAC-07 : l'ancien MERGE INTO ... KEY (center_id) (syntaxe H2) doit ECHOUER sur PostgreSQL"
"${PSQL[@]}" -d hemo_test -c "MERGE INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) KEY (center_id) VALUES ('99996700-0000-0000-0000-00000000000a', 'FAC-{YEAR}-{SEQ}', true, CURRENT_TIMESTAMP, 'system')" \
  && { echo "ATTENDU : echec"; RC=1; } || echo "echec constate (attendu)"
echo "-- l'ecriture actuelle (UPDATE puis INSERT) passe"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -c "UPDATE facturation_settings SET code_format = 'FAC-{SEQ}' WHERE center_id = '99996700-0000-0000-0000-00000000000c'" \
  -c "INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) VALUES ('99996700-0000-0000-0000-00000000000c', 'FAC-{SEQ}', true, CURRENT_TIMESTAMP, 'rejeu')" \
  -c "DELETE FROM facturation_settings WHERE center_id = '99996700-0000-0000-0000-00000000000c'" || RC=1

echo "-- F2 (migration V39) : les deux colonnes de memoire de l'exclusion existent et sont nullables"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'seances' AND column_name IN ('statut_avant_exclusion', 'exclusion_absence_id') AND is_nullable = 'YES'" | tr -d '\r' | grep -qx 2 \
  && echo "colonnes presentes (2/2)" || { echo "ATTENDU : 2 colonnes nullables"; RC=1; }
echo "-- F2 : V39 est idempotente (rejeu sans erreur)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/V39__facturation_exclusion_de_seance.sql" >/dev/null && echo "rejeu V39 ok" || RC=1

echo "-- F3 (migration V40) : la colonne d'auteur de l'emission existe et est nullable"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'factures' AND column_name = 'emise_par' AND data_type = 'uuid' AND is_nullable = 'YES'" | tr -d '\r' | grep -qx 1 \
  && echo "colonne presente (1/1)" || { echo "ATTENDU : emise_par uuid nullable"; RC=1; }
echo "-- F3 : V40 est idempotente (rejeu sans erreur)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/V40__facturation_auteur_de_l_emission.sql" >/dev/null && echo "rejeu V40 ok" || RC=1

echo "-- F4 (migration V41) : mode, reference et cle d'idempotence des reglements existent et sont nullables"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.columns WHERE table_name = 'facture_reglements' AND column_name IN ('mode_reglement', 'reference_paiement', 'cle_idempotence') AND is_nullable = 'YES'" | tr -d '\r' | grep -qx 3 \
  && echo "colonnes presentes (3/3)" || { echo "ATTENDU : 3 colonnes nullables"; RC=1; }
echo "-- F4 : l'index unique de la cle d'idempotence existe"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM pg_indexes WHERE indexname = 'ux_facture_reglements_cle_idempotence'" | tr -d '\r' | grep -qx 1 \
  && echo "index present" || { echo "ATTENDU : index unique"; RC=1; }
echo "-- F4 : V41 est idempotente (rejeu sans erreur)"
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/V41__facturation_mode_reference_et_cle_des_reglements.sql" >/dev/null && echo "rejeu V41 ok" || RC=1

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL des parametres (contexte facturation) ==="
./mvnw -o -pl contexts/facturation test -Dtest='ParametresPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrats PostgreSQL de la simulation F2 (seances : exclusion, reinclusion, surcharge ; sequence) ==="
./mvnw -o -pl contexts/facturation test -Dtest='SeancesPostgresContractIT,SequencePostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrats PostgreSQL de la validation F3 (factures, numero unique par centre, auteur ; reservation atomique de la sequence sous concurrence) ==="
./mvnw -o -pl contexts/facturation test -Dtest='FacturesPostgresContractIT,SequenceConcurrentePostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrats PostgreSQL des reglements F4 (colonnes V41, index unique, lecture des situations ; idempotence et remboursement sous concurrence) ==="
./mvnw -o -pl contexts/facturation test -Dtest='ReglementsPostgresContractIT,ReglementsConcurrentsPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrats PostgreSQL des lectures de pilotage F5 (totaux stockes, annee/mois, comptages, ordre) ==="
./mvnw -o -pl contexts/facturation test -Dtest='PilotagePostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrats PostgreSQL des ports publies F6 (periode facturee, factures emises et reglements encaisses hors reprise ; reprise des soldes) ==="
./mvnw -o -pl contexts/facturation -am test -Dtest='LecturesPubliquesPostgresContractIT,RepriseDesSoldesPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test -Dfacturation.pg16.password="$PW" || RC=1
echo "=== Contrat PostgreSQL des regles de TVA (contexte comptabilite) ==="
./mvnw -o -pl contexts/comptabilite test -Dtest='RegleTvaPostgresContractIT' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcomptabilite.pg16.url="$URL" -Dcomptabilite.pg16.user=facturation_test -Dcomptabilite.pg16.password="$PW" || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
