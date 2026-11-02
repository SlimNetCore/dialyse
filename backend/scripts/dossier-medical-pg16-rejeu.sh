#!/bin/bash
# Rejeu du legacy MEDICAL sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql),
# puis verification equivalente de `ddl-auto: validate` pour les entites JPA medicales (tables ET colonnes declarees par
# @Table / @Column / @JoinColumn contre information_schema), sans demarrer Spring. Prouve aussi que le schema Flyway pur ne contient
# NI la colonne lignes_demande_examen.center_id (revert de 21f6ff7a) NI les tables paralleles du module gele dossier-medical.
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-pg16-rejeu.sh 55461 > rejeu-dossier-medical.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55461}"
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

echo "=== Revert de 21f6ff7a : pas de colonne center_id sur lignes_demande_examen, ni dans l'entite ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_name='lignes_demande_examen' AND column_name='center_id'")" = "0" ] \
  && echo "schema Flyway : lignes_demande_examen sans center_id (production inchangee)" || { echo "INATTENDU : center_id present"; RC=1; }
grep -q 'center_id' "$ENT/LigneDemandeExamenJpaEntity.java" && { echo "INATTENDU : l'entite declare center_id"; RC=1; } || echo "entite LigneDemandeExamenJpaEntity sans center_id"

echo "=== Tables paralleles du module gele : absentes du schema Flyway ==="
for t in serologies antecedents allergies bilans_pregreffe etapes_bilan_pregreffe; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done
echo "=== Code de sortie : $RC ==="
exit $RC
