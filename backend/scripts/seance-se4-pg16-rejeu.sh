#!/bin/bash
# Rejeu de la slice SE4 (rappel quotidien des seances a regulariser) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; le journal notification_evenement garde sa cle primaire (identifiant
# deterministe du rappel, aucune table ajoutee : KB-SEA-35) ; la table derogations_regularisation (V50, KB-SEA-40) est CONSERVEE,
# VIDE et jamais ecrite par le module ; les requetes de verifications-production.md section 56 (SELECT seule) sont executables sur le
# schema reel ; puis le contrat PostgreSQL du verrou consultatif du rappel (deux connexions : verrou -> ecriture concurrente).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se4-pg16-rejeu.sh 55504 > rejeu-seance-se4.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55504}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${SEANCE_PG16_PASSWORD:-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)}"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U seance_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
export SEANCE_PG16_PASSWORD="$PW"
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

echo "=== notification_evenement : cle primaire sur l'identifiant (identifiant deterministe du rappel, aucune table ajoutee) ==="
T=notification_evenement
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$T' AND indexdef ILIKE '%UNIQUE%(id)%'")" -ge "1" ] \
  && echo "ok : $T unique sur id (la double emission par deux instances echoue a l'insertion)" || { echo "INATTENDU : cle de $T"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T'")" -ge "6" ] \
  && echo "ok : $T porte au moins six colonnes (id, center_id, type, payload, target_roles, cree_le)" || { echo "INATTENDU : colonnes de $T"; RC=1; }

echo "=== derogations_regularisation (V50, KB-SEA-40) : conservee, vide, jamais ecrite ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='derogations_regularisation'")" = "1" ] \
  && echo "presente : derogations_regularisation (conservee, aucune operation destructive)" || { echo "INATTENDU : table derogations_regularisation absente"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM derogations_regularisation")" = "0" ] \
  && echo "ok : vide apres la chaine Flyway" || { echo "INATTENDU : derogations_regularisation non vide"; RC=1; }

echo "=== Aucune table parallele ==="
for t in rappels_regularisation seance_rappels rappels_seances; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== SE4 : requetes de controle (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT count(*) FROM derogations_regularisation" \
  "SELECT center_id, count(*) FROM derogations_regularisation GROUP BY center_id" \
  "SELECT s.center_id, count(*) AS oubliees_hors_fenetre, min(s.date_seance) AS plus_ancienne FROM seances s WHERE s.statut = 'CREE' AND s.date_seance < current_date - 7 GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS creees_deverrouillees FROM seances s WHERE s.statut = 'CREE' AND s.regularisation_deverrouillee_at IS NOT NULL GROUP BY s.center_id" \
  "SELECT s.center_id, s.id FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.statut = 'CREE' AND p.id IS NULL" \
  "SELECT center_id, count(*) AS rappels, count(DISTINCT left(payload, 200)) AS charges FROM notification_evenement WHERE type = 'SEANCES_A_REGULARISER' GROUP BY center_id, cree_le::date HAVING count(*) > 1"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du verrou du rappel (deux connexions : verrou -> ecriture concurrente, autre centre ou autre jour sans attente) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.VerrousDesRappelsPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
