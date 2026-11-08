#!/bin/bash
# Rejeu de la slice SE6 (modification de date et suppression d'une seance) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes de seances (facture_id, date_seance) et de seance_suppression
# (schema reel, types et bornes) ; aucune table ni colonne ajoutee ; requetes de controle de verifications-production.md section 58
# (SELECT seule) executables sur le schema reel ; puis le contrat PostgreSQL des seances (dont l'ecriture de la seule date, la suppression et le
# verrou de ligne) et le contrat PostgreSQL du journal des suppressions.
# A lancer en arriere-plan avec un log : backend/scripts/seance-se6-pg16-rejeu.sh 55506 > rejeu-seance-se6.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55506}"
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
colonne() { # table colonne type longueur(ou vide)
  local n
  if [ -n "$4" ]; then
    n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3' AND character_maximum_length=$4")"
  else
    n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3'")"
  fi
  [ "$n" = "1" ] && echo "ok : $1.$2 $3 $4" || { echo "INATTENDU : $1.$2 ($3 $4)"; RC=1; }
}

echo "=== Colonnes de la seance lues et ecrites par SE6 (schema reel) ==="
colonne seances date_seance date ""
colonne seances facture_id uuid ""
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='seances'")" = "28" ] \
  && echo "ok : seances a exactement 28 colonnes (aucune ajoutee par SE6)" || { echo "INATTENDU : nombre de colonnes de seances"; RC=1; }

echo "=== Journal des suppressions (V27) : neuf colonnes, types et bornes, aucune ajoutee ==="
colonne seance_suppression id uuid ""
colonne seance_suppression center_id uuid ""
colonne seance_suppression seance_id uuid ""
colonne seance_suppression patient_id uuid ""
colonne seance_suppression date_seance date ""
colonne seance_suppression statut "character varying" 20
colonne seance_suppression motif "character varying" 500
colonne seance_suppression supprime_par "character varying" 255
colonne seance_suppression supprime_le "timestamp with time zone" ""
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='seance_suppression'")" = "9" ] \
  && echo "ok : seance_suppression a exactement neuf colonnes" || { echo "INATTENDU : colonnes de seance_suppression"; RC=1; }

echo "=== Aucune cle etrangere vers seances (la suppression ne depend d'aucun ordre d'effacement) ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.table_constraints tc JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name WHERE tc.constraint_type='FOREIGN KEY' AND ccu.table_name='seances'")" = "0" ] \
  && echo "ok : aucune cle etrangere vers seances" || { echo "A EXAMINER : une cle etrangere reference seances (ordre d'effacement de la suppression)"; RC=1; }

echo "=== SE6 : 8 requetes de controle (SELECT seule, section 58, meme numerotation 1 a 8), executables sur le schema reel ==="
for q in \
  "SELECT f.center_id, f.id, f.numero_facture, COALESCE(l.declarees, 0) AS seances_declarees, COALESCE(s.rattachees, 0) AS seances_rattachees FROM factures f LEFT JOIN (SELECT facture_id, center_id, sum(seance_count) AS declarees FROM facture_lignes GROUP BY facture_id, center_id) l ON l.facture_id = f.id AND l.center_id = f.center_id LEFT JOIN (SELECT facture_id, center_id, count(*) AS rattachees FROM seances WHERE facture_id IS NOT NULL GROUP BY facture_id, center_id) s ON s.facture_id = f.id AND s.center_id = f.center_id WHERE COALESCE(l.declarees, 0) > COALESCE(s.rattachees, 0)" \
  "SELECT ss.center_id, ss.seance_id, ss.patient_id, ss.date_seance, ss.statut, ss.supprime_le, f.id AS facture, f.numero_facture FROM seance_suppression ss INNER JOIN factures f ON f.center_id = ss.center_id AND f.patient_id = ss.patient_id AND ss.date_seance BETWEEN f.period_start AND f.period_end ORDER BY ss.supprime_le DESC LIMIT 100" \
  "SELECT s.center_id, s.statut, count(*) AS rattachees_non_facturees FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id, s.statut" \
  "SELECT s.center_id, s.id, s.statut, s.date_seance, s.facture_id FROM seances s INNER JOIN factures f ON f.id = s.facture_id AND f.center_id = s.center_id WHERE s.date_seance NOT BETWEEN f.period_start AND f.period_end LIMIT 100" \
  "SELECT a.center_id, a.entity_id, a.username, a.occurred_at, a.status_code FROM audit_log a WHERE a.action_code = 'SEANCES_MODIFICATION' AND a.http_method = 'PUT' AND a.route_template = '/api/v1/seances/{seanceId}' AND a.status_code BETWEEN 200 AND 299 ORDER BY a.occurred_at DESC LIMIT 100" \
  "SELECT a.center_id, a.username, count(*) AS suppressions FROM audit_log a WHERE a.action_code = 'SEANCES_SUPPRESSION' AND a.status_code = 204 GROUP BY a.center_id, a.username ORDER BY 3 DESC LIMIT 50" \
  "SELECT ss.center_id, ss.statut, count(*) AS suppressions_de_seances_validees_ou_signees FROM seance_suppression ss WHERE ss.statut IN ('VALIDEE','SIGNEE') GROUP BY ss.center_id, ss.statut" \
  "SELECT s.center_id, s.id, s.statut, s.date_seance, f.id AS facture, f.numero_facture FROM seances s INNER JOIN factures f ON f.center_id = s.center_id AND f.patient_id = s.patient_id AND s.date_seance BETWEEN f.period_start AND f.period_end WHERE s.facture_id IS NULL AND s.statut IN ('CREE','VALIDEE','SIGNEE') ORDER BY s.date_seance DESC LIMIT 100"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL des seances (ecriture de la seule date, suppression, verrou de ligne) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.SeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Contrat PostgreSQL du journal des suppressions ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.JournalDesSuppressionsPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
