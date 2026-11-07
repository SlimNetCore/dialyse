#!/bin/bash
# Rejeu de la slice DM3 (demandes d'examen) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par l'adapter JDBC DemandesDExamenJdbc contre information_schema
# (et ABSENCE de center_id sur lignes_demande_examen : ADR 0072) ; aucune table parallele ; requetes de parite de
# verifications-production.md section 47 (SELECT seule) executables sur le schema reel ; puis le contrat PostgreSQL du port
# DemandesDExamen (meme jeu de tests que le double en memoire et que H2).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm3-pg16-rejeu.sh 55473 > rejeu-dm3.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55473}"
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

echo "=== Colonnes lues et ecrites par l'adapter JDBC de DM3 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes demandes_examen id center_id patient_id prescripteur_id date_demande categorie urgent motif statut conclusion created_at updated_at
verifier_colonnes lignes_demande_examen id demande_id analyte_code_system analyte_code analyte_code_display libelle commentaire
verifier_colonnes patients id center_id
# ADR 0072 : le centre n'est porte que par demandes_examen ; lignes_demande_examen est jointe par demande_id (jamais de center_id).
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='lignes_demande_examen' AND column_name='center_id'")" = "0" ] \
  && echo "ok : lignes_demande_examen n'a pas de center_id" || { echo "INATTENDU : lignes_demande_examen.center_id"; RC=1; }

echo "=== Aucune table parallele ==="
for t in demande_examen demandes_examens examens lignes_examen dossier_medical_demandes; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM3 : requetes de parite de verifications-production.md section 47 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, statut, count(*) AS demandes FROM demandes_examen GROUP BY center_id, statut" \
  "SELECT d.center_id, count(*) AS sans_ligne FROM demandes_examen d WHERE NOT EXISTS (SELECT 1 FROM lignes_demande_examen l WHERE l.demande_id = d.id) GROUP BY d.center_id" \
  "SELECT l.demande_id FROM lignes_demande_examen l WHERE NOT EXISTS (SELECT 1 FROM demandes_examen d WHERE d.id = l.demande_id)" \
  "SELECT center_id, id, statut, categorie FROM demandes_examen WHERE statut NOT IN ('DEMANDE','PRELEVE','RESULTAT_DISPONIBLE','VALIDE','ANNULE') OR categorie NOT IN ('BIOLOGIE','IMAGERIE','FONCTIONNEL','ANAPATH')" \
  "SELECT d.center_id, count(*) AS patient_absent FROM demandes_examen d LEFT JOIN patients p ON p.id = d.patient_id AND p.center_id = d.center_id WHERE p.id IS NULL GROUP BY d.center_id" \
  "SELECT center_id, id FROM demandes_examen WHERE statut = 'VALIDE' AND conclusion IS NULL" \
  "SELECT o.center_id, count(*) AS observations_vers_demande_inconnue FROM observations_biologiques o WHERE o.demande_examen_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM demandes_examen d WHERE d.id = o.demande_examen_id AND d.center_id = o.center_id) GROUP BY o.center_id"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du port DemandesDExamen ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.DemandesDExamenPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
