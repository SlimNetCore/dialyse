#!/bin/bash
# Rejeu du legacy PATIENT / ASSURE / PEC sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql),
# puis verification equivalente de `ddl-auto: validate` pour les entites JPA patient, assure, affectation d'assure, attestation de droits et prise en charge
# (tables ET colonnes declarees par @Table / @Column / @JoinColumn contre information_schema), sans demarrer Spring. Prouve aussi que le schema Flyway pur
# ne contient NI la colonne patients.date_sortie (lue par le module gele patient-couverture) NI de table parallele, et que patients.date_evenement_etat existe.
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-pg16-rejeu.sh 55462 > rejeu-patient-couverture.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55462}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U patient_couverture_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U patient_couverture_test)
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
TABLES="patients assure assure_patient attestation_droit prise_en_charge"
echo "=== Entites JPA patient / assure / PEC contre information_schema (equivalent de ddl-auto: validate) ==="
NB_ENTITES=0
for fichier in "$ENT"/*.java; do
  table="$(grep -m1 -oE '@Table\(name = "[a-z_]+"' "$fichier" | sed -E 's/.*"([a-z_]+)"/\1/')"
  [ -n "$table" ] || continue
  case " $TABLES " in *" $table "*) ;; *) continue ;; esac
  NB_ENTITES=$((NB_ENTITES + 1))
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$table'")" = "1" ] \
    || { echo "TABLE ABSENTE : $table ($(basename "$fichier"))"; RC=1; continue; }
  FICHIERS=("$fichier")
  [ "$table" = "assure_patient" ] && FICHIERS+=("$ENT/AssurePatientId.java")
  for col in $(grep -hoE '@(Column|JoinColumn)\((name = )?"[a-z_]+"' "${FICHIERS[@]}" | sed -E 's/.*"([a-z_]+)"/\1/' | sort -u); do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$table' AND column_name='$col'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $table.$col ($(basename "$fichier"))"; RC=1; }
  done
  echo "ok : $table ($(basename "$fichier"))"
done
[ "$NB_ENTITES" -eq 5 ] && echo "5 entites patient / assure / PEC verifiees" || { echo "ATTENDU : 5 entites, trouve $NB_ENTITES"; RC=1; }

echo "=== Colonnes et table lues par le legacy et par Planification / Absences ==="
for tc in "patients:date_evenement_etat" "patients:etat_patient" "patients:type_patient" "mouvement_patient:patient_id"; do
  t="${tc%%:*}"; c="${tc##*:}"
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
    && echo "presente : $t.$c" || { echo "ABSENTE : $t.$c"; RC=1; }
done

echo "=== Ecarts du module gele : colonne et tables inexistantes dans le schema Flyway ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_name='patients' AND column_name='date_sortie'")" = "0" ] \
  && echo "absente : patients.date_sortie (le module gele la lit : raison du gel)" || { echo "INATTENDU : patients.date_sortie presente"; RC=1; }
for t in patient mouvements_patient pec assures attestations attestation_droits; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done
echo "=== Code de sortie : $RC ==="
exit $RC
