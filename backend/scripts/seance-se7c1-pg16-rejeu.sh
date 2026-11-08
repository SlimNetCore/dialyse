#!/bin/bash
# Rejeu de la sous-slice SE7c1 (lectures publiees de la Seance pour le Stock : facturation et place d'une seance) sur un PostgreSQL 16 REEL et
# JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes reelles lues par l'adapter (types) ; requetes de
# l'adapter executees sur le schema reel (SELECT seule) ; requetes de controle de verifications-production.md section 61 (SELECT seule)
# executables ; puis le contrat PostgreSQL des lectures publiees (ecrit dans la base jetable uniquement).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7c1-pg16-rejeu.sh 55543 > rejeu-seance-se7c1.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55543}"
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
colonne() { # table colonne type
  local n
  n="$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$1' AND column_name='$2' AND data_type='$3'")"
  [ "$n" = "1" ] && echo "ok : $1.$2 $3" || { echo "INATTENDU : $1.$2 ($3)"; RC=1; }
}

echo "=== Colonnes reelles lues par l'adapter des lectures publiees (types) ==="
for c in id center_id salle_id creneau_id; do colonne seances $c uuid; done
colonne seances statut "character varying"
for t in salle position_creneau; do colonne $t id uuid; colonne $t center_id uuid; colonne $t code "character varying"; done
colonne position_creneau libelle "character varying"

echo "=== Requetes de l'adapter, executees sur le schema reel (SELECT seule) ==="
CA="'00000000-0000-0000-0000-00000000000a'"
SE="'00000000-0000-0000-0000-000000000001'"
for q in \
  "SELECT statut FROM seances WHERE id = $SE AND center_id = $CA" \
  "SELECT salle_id, creneau_id FROM seances WHERE id = $SE AND center_id = $CA" \
  "SELECT code FROM salle WHERE id = $SE AND center_id = $CA" \
  "SELECT code, libelle FROM position_creneau WHERE id = $SE AND center_id = $CA"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

echo "=== SE7c1 : requetes de controle (SELECT seule, section 61), executables sur le schema reel ==="
for q in \
  "SELECT s.center_id, count(*) AS seances_avec_une_salle_d_un_autre_centre FROM seances s INNER JOIN salle sa ON sa.id = s.salle_id WHERE sa.center_id <> s.center_id GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_un_creneau_d_un_autre_centre FROM seances s INNER JOIN position_creneau p ON p.id = s.creneau_id WHERE p.center_id <> s.center_id GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_une_salle_disparue FROM seances s WHERE s.salle_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM salle sa WHERE sa.id = s.salle_id) GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_un_creneau_disparu FROM seances s WHERE s.creneau_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM position_creneau p WHERE p.id = s.creneau_id) GROUP BY s.center_id" \
  "SELECT s.center_id, s.statut, count(*) AS seances_au_statut_facturee_hors_casse_canonique FROM seances s WHERE upper(s.statut) = 'FACTUREE' AND s.statut <> 'FACTUREE' GROUP BY s.center_id, s.statut" \
  "SELECT s.center_id, count(*) AS seances_facturees_sans_facture FROM seances s WHERE s.statut = 'FACTUREE' AND s.facture_id IS NULL GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_facture_sans_statut_facturee FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL : LecturesPublieesDesSeancesPostgresContractIT ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.LecturesPublieesDesSeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
exit $RC
