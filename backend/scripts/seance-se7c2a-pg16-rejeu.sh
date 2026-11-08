#!/bin/bash
# Rejeu de la sous-slice SE7c2a (lectures et ecritures publiees de la Seance pour la Facturation : seances a facturer, exclusion, reinclusion,
# forfait impose, comptages) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes
# reelles lues et ecrites par l'adapter (types) ; requetes de lecture de l'adapter executees sur le schema reel (SELECT seule) ; puis le contrat
# PostgreSQL (ecrit dans la base jetable uniquement).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7c2a-pg16-rejeu.sh 55546
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55546}"
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

echo "=== Colonnes reelles lues ou ecrites par l'adapter (types) ==="
for c in id center_id patient_id facture_id exclusion_absence_id forfait_override_id; do colonne seances $c uuid; done
for c in statut statut_avant_exclusion forfait_override_nom forfait_override_updated_by; do colonne seances $c "character varying"; done
colonne seances date_seance date
colonne seances forfait_override_prix numeric
colonne seances forfait_override_updated_at "timestamp with time zone"
for c in id center_id centre_payeur_id; do colonne patients $c uuid; done
for c in code_patient nom prenom etat_patient numero_assurance; do colonne patients $c "character varying"; done
for c in id center_id agence_id; do colonne centre_payeur $c uuid; done

echo "=== Requetes de lecture de l'adapter, executees sur le schema reel (SELECT seule) ==="
CA="'00000000-0000-0000-0000-00000000000a'"
SE="'00000000-0000-0000-0000-000000000001'"
for q in \
  "SELECT s.id AS seance_id, s.patient_id, s.date_seance, s.statut, s.forfait_override_id, s.forfait_override_nom, s.forfait_override_prix, p.code_patient, p.nom, p.prenom, p.etat_patient, p.numero_assurance, p.centre_payeur_id, cp.agence_id FROM seances s INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id WHERE s.center_id = $CA AND s.date_seance BETWEEN '2026-08-01' AND '2026-08-31' AND s.statut IN ('VALIDEE', 'SIGNEE') AND s.facture_id IS NULL ORDER BY p.nom, p.prenom, s.date_seance, s.id" \
  "SELECT id, patient_id, date_seance, statut, facture_id, statut_avant_exclusion, exclusion_absence_id FROM seances WHERE id = $SE AND center_id = $CA" \
  "SELECT facture_id, COUNT(1) AS nombre FROM seances WHERE center_id = $CA AND facture_id IN ($SE, $CA) GROUP BY facture_id" \
  "SELECT COUNT(1) FROM seances WHERE center_id = $CA AND statut = 'FACTUREE' AND date_seance BETWEEN '2026-08-01' AND '2026-08-31'"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL : FacturationDesSeancesPostgresContractIT et course de deux emissions (LiaisonConcurrentePostgresContractIT) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.FacturationDesSeancesPostgresContractIT,com.hemodialyse.backend.contexts.seance.contrat.LiaisonConcurrentePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
exit $RC
