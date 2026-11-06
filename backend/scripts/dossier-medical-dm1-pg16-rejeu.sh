#!/bin/bash
# Rejeu de la slice DM1 (constantes) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie.
# Chaine Flyway V1..Vn (psql) ; equivalent de `ddl-auto: validate` pour les 14 entites JPA medicales (tables ET colonnes declarees
# par @Table / @Column / @JoinColumn contre information_schema, sans demarrer Spring) ; colonnes lues par l'adapter JDBC de DM1
# (seances, volet_paramedical) ; aucune table parallele ; requete de parite SELECT seule ; puis le contrat PostgreSQL du port
# HistoriqueDesConstantes (meme jeu de tests que le double en memoire et que H2).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm1-pg16-rejeu.sh 55471 > rejeu-dm1.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55471}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U dossier_medical_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U dossier_medical_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

ENT="$RACINE/backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/entity"
TABLES="allergies_patient antecedents_medicaux bilans_pre_greffe demandes_examen dossier_medical_patient etapes_bilan_pre_greffe lignes_demande_examen lignes_ordonnance observations_biologiques ordonnances prescriptions_medicales serologies_patient volet_medical volet_paramedical"
echo "=== Entites JPA medicales contre information_schema (equivalent de ddl-auto: validate) ==="
NB_ENTITES=0
for fichier in "$ENT"/*.java; do
  table="$(grep -m1 -oE '@Table\(name = "[a-z_]+"' "$fichier" | sed -E 's/.*"([a-z_]+)"/\1/')"
  [ -n "$table" ] || continue
  case " $TABLES " in *" $table "*) ;; *) continue ;; esac
  NB_ENTITES=$((NB_ENTITES + 1))
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$table'")" = "1" ] \
    || { echo "TABLE ABSENTE : $table ($(basename "$fichier"))"; RC=1; continue; }
  for col in $(grep -oE '@(Column|JoinColumn)\((name = )?"[a-z_]+"' "$fichier" | sed -E 's/.*"([a-z_]+)"/\1/' | sort -u); do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$table' AND column_name='$col'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $table.$col ($(basename "$fichier"))"; RC=1; }
  done
  echo "ok : $table ($(basename "$fichier"))"
done
[ "$NB_ENTITES" -eq 14 ] && echo "14 entites medicales verifiees" || { echo "ATTENDU : 14 entites, trouve $NB_ENTITES"; RC=1; }

echo "=== Colonnes lues par l'adapter JDBC de DM1 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes seances id center_id patient_id date_seance created_at
verifier_colonnes volet_paramedical id center_id seance_id poids_avant_kg poids_apres_kg ta_avant ta_apres debit_sang_ml_min ultrafiltration_ml duree_minutes

echo "=== Aucune table parallele (module gele) ==="
for t in constantes serologies antecedents allergies bilans_pregreffe etapes_bilan_pregreffe; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM1 : requete de parite de verifications-production.md (SELECT seule), executable sur le schema reel ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT s.center_id, count(*) AS seances, count(v.id) AS avec_volet FROM seances s LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id GROUP BY s.center_id" >/dev/null \
  && echo "requete DM1 executable" || { echo "ATTENDU : requete DM1 executable"; RC=1; }

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du port HistoriqueDesConstantes ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.HistoriqueDesConstantesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
