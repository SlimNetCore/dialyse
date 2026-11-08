#!/bin/bash
# Rejeu de la slice SE3 (consommables declares et raccourcis) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire
# detruit en sortie. Chaine Flyway V1..Vn (psql) ; la table seance_raccourcis_articles garde EXACTEMENT ses trois colonnes et sa cle primaire
# (centre, article) (aucune colonne ni contrainte ajoutee par le module), aucune table parallele, les requetes de verifications-production.md
# (SELECT seule) sont executables sur le schema reel ; puis le contrat PostgreSQL du port RaccourcisDesCentres (meme jeu de tests que le
# double en memoire et que H2, plus le verrou consultatif a deux connexions : verrou -> 409).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se3-pg16-rejeu.sh 55503 > rejeu-seance-se3.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55503}"
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

echo "=== seance_raccourcis_articles : trois colonnes, cle primaire (centre, article) ==="
T=seance_raccourcis_articles
for c in center_id article_id ordre; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T' AND column_name='$c' AND is_nullable='NO'")" = "1" ] \
    || { echo "COLONNE ABSENTE OU NULLABLE : $T.$c"; RC=1; }
done
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T'")" = "3" ] \
  && echo "ok : $T a exactement 3 colonnes (aucune ajoutee par le module)" || { echo "INATTENDU : colonnes de $T"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$T' AND indexdef ILIKE '%UNIQUE%(center_id, article_id)%'")" = "1" ] \
  && echo "ok : cle primaire (center_id, article_id)" || { echo "INATTENDU : cle de $T"; RC=1; }

echo "=== Aucune table parallele ==="
for t in seance_consommables seance_consommations seances_consommables raccourcis_consommables; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== SE3 : requetes de controle (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, count(*) AS raccourcis FROM seance_raccourcis_articles GROUP BY center_id HAVING count(*) > 12" \
  "SELECT r.center_id, r.article_id FROM seance_raccourcis_articles r LEFT JOIN articles a ON a.id = r.article_id AND a.center_id = r.center_id WHERE a.id IS NULL" \
  "SELECT r.center_id, r.article_id, a.code FROM seance_raccourcis_articles r JOIN articles a ON a.id = r.article_id AND a.center_id = r.center_id WHERE NOT a.active" \
  "SELECT center_id, ordre, count(*) FROM seance_raccourcis_articles GROUP BY center_id, ordre HAVING count(*) > 1"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du port RaccourcisDesCentres (meme jeu que le double en memoire et H2, plus le verrou a deux connexions) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.RaccourcisPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
