#!/bin/bash
# Rejeu de la slice DM5 (antecedents, allergies, abords vasculaires) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par les adapters JDBC AntecedentsJdbc,
# AllergiesJdbc et AbordsVasculairesJdbc contre information_schema (et ABSENCE de contrainte d'unicite sur les trois tables : KB-MED-64,
# l'unicite du diagnostic actif est protegee par un verrou consultatif) ; aucune table parallele ; requetes de parite de
# verifications-production.md section 49 (SELECT seule) executables sur le schema reel ; puis les contrats PostgreSQL des ports
# Antecedents, Allergies et AbordsVasculaires (meme jeu de tests que les doubles en memoire et que H2, plus les tests a deux connexions :
# verrou de ligne et verrou consultatif du diagnostic).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm5-pg16-rejeu.sh 55475 > rejeu-dm5.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55475}"
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

echo "=== Colonnes lues et ecrites par les adapters JDBC de DM5 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
  [ "$t" != "patients" ] || return 0   # patients n'est lue que par l'ACL (existence), hors tables de la slice
  # Aucune colonne ajoutee : exactement les colonnes lues par l'adapter.
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t'")" = "$#" ] \
    && echo "ok : $t a exactement $# colonnes" || { echo "INATTENDU : colonnes de $t"; RC=1; }
  # KB-MED-64 : aucune contrainte d'unicite autre que la cle primaire.
  [ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$t' AND indexdef ILIKE '%UNIQUE%' AND indexname <> '${t}_pkey'")" = "0" ] \
    && echo "ok : $t sans contrainte d'unicite supplementaire" || { echo "INATTENDU : index unique supplementaire sur $t"; RC=1; }
}
verifier_colonnes antecedents_medicaux id center_id patient_id type_antecedent diagnostic_code_system diagnostic_code diagnostic_code_display libelle_libre date_debut date_fin statut_clinique severite note created_at updated_at
verifier_colonnes allergies_patient id center_id patient_id substance_code_system substance_code substance_code_display categorie criticite type_reaction manifestations date_constatation statut_verification created_at updated_at
verifier_colonnes abords_vasculaires id center_id patient_id type_abord cote localisation date_creation date_fin actif complications created_at
verifier_colonnes patients id center_id

echo "=== Aucune table parallele (les schemas geles antecedents et allergies n'existent pas dans la chaine Flyway) ==="
for t in antecedents antecedent allergies allergie abords abord_vasculaire; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM5 : requetes de parite de verifications-production.md section 49 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT 'antecedents' AS table_, center_id, count(*) FROM antecedents_medicaux GROUP BY center_id UNION ALL SELECT 'allergies', center_id, count(*) FROM allergies_patient GROUP BY center_id UNION ALL SELECT 'abords', center_id, count(*) FROM abords_vasculaires GROUP BY center_id" \
  "SELECT center_id, patient_id, diagnostic_code_system, diagnostic_code, count(*) AS exemplaires FROM antecedents_medicaux WHERE statut_clinique = 'ACTIF' AND diagnostic_code IS NOT NULL GROUP BY center_id, patient_id, diagnostic_code_system, diagnostic_code HAVING count(*) > 1" \
  "SELECT 'antecedents' AS table_, a.center_id, count(*) AS patient_absent FROM antecedents_medicaux a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id UNION ALL SELECT 'allergies', a.center_id, count(*) FROM allergies_patient a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id UNION ALL SELECT 'abords', a.center_id, count(*) FROM abords_vasculaires a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id" \
  "SELECT a.center_id AS centre_de_l_abord, p.center_id AS centre_du_patient, count(*) FROM abords_vasculaires a JOIN patients p ON p.id = a.patient_id AND p.center_id <> a.center_id GROUP BY a.center_id, p.center_id" \
  "SELECT center_id, type_abord, count(*) FROM abords_vasculaires WHERE type_abord NOT IN ('KT_AIGU','KT_TUNNELISE') GROUP BY center_id, type_abord ORDER BY 3 DESC" \
  "SELECT 'antecedents' AS table_, center_id, id FROM antecedents_medicaux WHERE type_antecedent NOT IN ('MEDICAL','CHIRURGICAL','FAMILIAL','OBSTETRICAL','COMORBIDITE') OR statut_clinique NOT IN ('ACTIF','RESOLU','INACTIF') OR (diagnostic_code IS NOT NULL AND (diagnostic_code_system IS NULL OR diagnostic_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL'))) UNION ALL SELECT 'allergies', center_id, id FROM allergies_patient WHERE categorie NOT IN ('MEDICAMENT','ALIMENT','ENVIRONNEMENT','BIOLOGIQUE') OR criticite NOT IN ('BASSE','HAUTE') OR type_reaction NOT IN ('ALLERGIE','INTOLERANCE') OR statut_verification NOT IN ('SUSPECTEE','CONFIRMEE','REFUTEE') OR substance_code_system IS NULL OR substance_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL')" \
  "SELECT 'allergies_hautes_sans_manifestations' AS controle, center_id, count(*) FROM allergies_patient WHERE criticite = 'HAUTE' AND (manifestations IS NULL OR btrim(manifestations) = '') GROUP BY center_id UNION ALL SELECT 'abords_fin_avant_creation', center_id, count(*) FROM abords_vasculaires WHERE date_fin < date_creation GROUP BY center_id UNION ALL SELECT 'antecedents_fin_avant_debut', center_id, count(*) FROM antecedents_medicaux WHERE date_fin < date_debut GROUP BY center_id UNION ALL SELECT 'allergies_dans_le_futur', center_id, count(*) FROM allergies_patient WHERE date_constatation > current_date GROUP BY center_id" \
  "SELECT max(length(severite)) AS severite_max, max(length(libelle_libre)) AS libelle_max, max(length(diagnostic_code)) AS code_max FROM antecedents_medicaux"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des ports Antecedents, Allergies et AbordsVasculaires ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.AntecedentsPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.AllergiesPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.AbordsVasculairesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
