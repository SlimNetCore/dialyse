#!/bin/bash
# Rejeu du legacy SEANCE sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql),
# puis verification equivalente de `ddl-auto: validate` pour l'entite JPA SeanceJpaEntity (table ET colonnes declarees par @Table / @Column
# contre information_schema), sans demarrer Spring. Prouve aussi : aucune version Flyway en doublon, les tables machine / protocole / salle et
# derogations_regularisation (V50, additive, inutilisee par le code deploye) existent, et que le schema Flyway pur ne contient NI les colonnes
# seances.pec_id / type_seance / verrouille_facturation (lues et ecrites par le module gele contexts/seance) NI de table parallele.
# A lancer en arriere-plan avec un log : backend/scripts/seance-pg16-rejeu.sh 55463 > rejeu-seance.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55463}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U seance_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U seance_test)
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
echo "=== Entite JPA Seance contre information_schema (equivalent de ddl-auto: validate) ==="
fichier="$ENT/SeanceJpaEntity.java"
table="$(grep -m1 -oE '@Table\(name = "[a-z_]+"' "$fichier" | sed -E 's/.*"([a-z_]+)"/\1/')"
[ "$table" = "seances" ] || { echo "ATTENDU : @Table seances, trouve '$table'"; RC=1; }
NB_COLONNES=0
for col in $(grep -hoE '@(Column|JoinColumn)\((name = )?"[a-z_]+"' "$fichier" | sed -E 's/.*"([a-z_]+)"/\1/' | sort -u); do
  NB_COLONNES=$((NB_COLONNES + 1))
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='seances' AND column_name='$col'")" = "1" ] \
    || { echo "COLONNE ABSENTE : seances.$col"; RC=1; }
done
echo "ok : seances ($NB_COLONNES colonnes declarees par SeanceJpaEntity)"
[ "$NB_COLONNES" -ge 20 ] || { echo "ATTENDU : au moins 20 colonnes, trouve $NB_COLONNES"; RC=1; }

echo "=== Colonnes et tables lues par le legacy, Planification, Absences, Facturation, Stock ==="
for tc in "seances:facture_id" "seances:statut_avant_exclusion" "seances:exclusion_absence_id" "seances:salle_id" "seances:creneau_id" "seances:generateur_id" "seances:hors_planning"; do
  t="${tc%%:*}"; c="${tc##*:}"
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
    && echo "presente : $t.$c" || { echo "ABSENTE : $t.$c"; RC=1; }
done
for t in salle machine protocole derogations_regularisation; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "1" ] \
    && echo "presente : table $t" || { echo "ABSENTE : table $t"; RC=1; }
done
[ "$(scalaire "SELECT count(*) FROM derogations_regularisation")" = "0" ] \
  && echo "derogations_regularisation vide (V50 additive, inutilisee par le code deploye)" || { echo "INATTENDU : derogations_regularisation non vide"; RC=1; }

echo "=== Ecarts du module gele : colonnes et tables inexistantes dans le schema Flyway ==="
for c in pec_id type_seance verrouille_facturation; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_name='seances' AND column_name='$c'")" = "0" ] \
    && echo "absente : seances.$c (le module gele la lit : raison du gel)" || { echo "INATTENDU : seances.$c presente"; RC=1; }
done
for t in seance seance_facturation verrous_facturation; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done
echo "=== Code de sortie : $RC ==="
exit $RC
