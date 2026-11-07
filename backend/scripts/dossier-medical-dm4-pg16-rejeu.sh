#!/bin/bash
# Rejeu de la slice DM4 (serologies) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par l'adapter JDBC SerologiesJdbc contre information_schema
# (et ABSENCE de contrainte d'unicite sur serologies_patient : KB-MED-56, l'unicite est protegee par un verrou consultatif) ; aucune
# table parallele ; requetes de parite de verifications-production.md section 48 (SELECT seule) executables sur le schema reel ;
# puis le contrat PostgreSQL du port Serologies (meme jeu de tests que le double en memoire et que H2, plus les deux tests a deux
# connexions : verrou de ligne et verrou consultatif du triplet).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm4-pg16-rejeu.sh 55474 > rejeu-dm4.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55474}"
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

echo "=== Colonnes lues et ecrites par l'adapter JDBC de DM4 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes serologies_patient id center_id patient_id marqueur resultat titre unite date_prelevement laboratoire date_prochain_controle conduite_a_tenir created_at updated_at
verifier_colonnes patients id center_id
# Aucune colonne ajoutee : exactement les 13 colonnes lues par l'adapter.
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='serologies_patient'")" = "13" ] \
  && echo "ok : serologies_patient a exactement 13 colonnes" || { echo "INATTENDU : colonnes de serologies_patient"; RC=1; }
# KB-MED-56 : aucune contrainte d'unicite autre que la cle primaire (l'unicite applicative est protegee par un verrou consultatif).
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='serologies_patient' AND indexdef ILIKE '%UNIQUE%' AND indexname <> 'serologies_patient_pkey'")" = "0" ] \
  && echo "ok : aucune contrainte d'unicite supplementaire (verrou consultatif)" || { echo "INATTENDU : index unique supplementaire sur serologies_patient"; RC=1; }

echo "=== Aucune table parallele (le schema gele serologies n'existe pas dans la chaine Flyway) ==="
for t in serologies serologie serologies_dossier_medical dossier_medical_serologies; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM4 : requetes de parite de verifications-production.md section 48 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, marqueur, resultat, count(*) AS serologies FROM serologies_patient GROUP BY center_id, marqueur, resultat" \
  "SELECT center_id, patient_id, marqueur, date_prelevement, count(*) AS exemplaires FROM serologies_patient GROUP BY center_id, patient_id, marqueur, date_prelevement HAVING count(*) > 1" \
  "SELECT center_id, id, marqueur, resultat FROM serologies_patient WHERE marqueur NOT IN ('VIH_AC','AG_HBS','AC_HBS','AC_HBC','AC_VHC','ARN_VHC','TPHA','CMV_IGG','CMV_IGM','TOXO_IGG','TOXO_IGM','EBV_IGG','EBV_IGM') OR resultat NOT IN ('POSITIF','NEGATIF','DOUTEUX','EN_COURS')" \
  "SELECT center_id, count(*) FILTER (WHERE titre < 0) AS titres_negatifs, count(*) FILTER (WHERE abs(titre) >= 10000000) AS titres_hors_borne, count(*) FILTER (WHERE length(unite) > 20) AS unites_trop_longues, count(*) FILTER (WHERE length(laboratoire) > 255) AS laboratoires_trop_longs FROM serologies_patient GROUP BY center_id" \
  "SELECT center_id, count(*) AS positifs_sans_conduite FROM serologies_patient WHERE resultat = 'POSITIF' AND (conduite_a_tenir IS NULL OR btrim(conduite_a_tenir) = '') GROUP BY center_id" \
  "SELECT s.center_id, count(*) AS patient_absent FROM serologies_patient s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE p.id IS NULL GROUP BY s.center_id" \
  "SELECT center_id, count(*) FILTER (WHERE date_prelevement > current_date) AS futures, count(*) FILTER (WHERE date_prelevement < DATE '1990-01-01') AS tres_anciennes, count(*) FILTER (WHERE date_prochain_controle < date_prelevement) AS controle_avant_prelevement FROM serologies_patient GROUP BY center_id"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL du port Serologies ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.SerologiesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
