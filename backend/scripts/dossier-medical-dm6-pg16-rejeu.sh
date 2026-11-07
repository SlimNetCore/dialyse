#!/bin/bash
# Rejeu de la slice DM6 (observations biologiques et analyses) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par les adapters JDBC ObservationsBiologiquesJdbc
# et AnalysesJdbc contre information_schema (et ABSENCE de contrainte d'unicite sur les deux tables : deux envois identiques creent
# deux lignes, plusieurs bilans le meme jour sont permis) ; aucune table parallele ; requetes de parite de verifications-production.md
# section 50 (SELECT seule) executables sur le schema reel ; puis les contrats PostgreSQL des ports ObservationsBiologiques et Analyses
# (meme jeu de tests que les doubles en memoire et que H2, plus les tests a deux connexions : verrou de ligne -> 409).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm6-pg16-rejeu.sh 55476 > rejeu-dm6.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55476}"
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

echo "=== Colonnes lues et ecrites par les adapters JDBC de DM6 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
  [ "$t" != "patients" ] && [ "$t" != "demandes_examen" ] || return 0   # lues seulement (existence), hors tables de la slice
  # Aucune colonne ajoutee : exactement les colonnes lues par l'adapter.
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t'")" = "$#" ] \
    && echo "ok : $t a exactement $# colonnes" || { echo "INATTENDU : colonnes de $t"; RC=1; }
  # Aucune contrainte d'unicite autre que la cle primaire.
  [ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$t' AND indexdef ILIKE '%UNIQUE%' AND indexname <> '${t}_pkey'")" = "0" ] \
    && echo "ok : $t sans contrainte d'unicite supplementaire" || { echo "INATTENDU : index unique supplementaire sur $t"; RC=1; }
}
verifier_colonnes observations_biologiques id center_id patient_id demande_examen_id analyte_code_system analyte_code analyte_code_display valeur_num unite valeur_texte date_prelevement statut source created_at updated_at
verifier_colonnes resultats_analyses id center_id patient_id date_prelevement hb_g_dl ht_pct plaquettes ferritine_ng_ml cstf_pct epo_endogene_mui_ml uree_pre_mg_dl uree_post_mg_dl creatinine_mg_dl kt_v_mensuel phosphore_mg_dl calcium_mg_dl pth_pg_ml albumine_g_dl proteines_g_dl crp_mg_l created_at updated_at
verifier_colonnes patients id center_id
verifier_colonnes demandes_examen id patient_id center_id

echo "=== lignes_demande_examen n'a PAS de center_id et n'en recoit pas (ADR 0072) ==="
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='lignes_demande_examen' AND column_name='center_id'")" = "0" ] \
  && echo "ok : pas de center_id sur lignes_demande_examen" || { echo "INATTENDU : center_id sur lignes_demande_examen"; RC=1; }

echo "=== Aucune table parallele (les schemas geles du module n'existent pas dans la chaine Flyway) ==="
for t in observations observation observation_biologique analyses analyse resultat_analyse; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM6 : requetes de parite de verifications-production.md section 50 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT 'observations' AS table_, center_id, count(*) FROM observations_biologiques GROUP BY center_id UNION ALL SELECT 'analyses', center_id, count(*) FROM resultats_analyses GROUP BY center_id" \
  "SELECT 'observations' AS table_, o.center_id, count(*) AS patient_absent FROM observations_biologiques o LEFT JOIN patients p ON p.id = o.patient_id AND p.center_id = o.center_id WHERE p.id IS NULL GROUP BY o.center_id UNION ALL SELECT 'analyses', a.center_id, count(*) FROM resultats_analyses a LEFT JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id WHERE p.id IS NULL GROUP BY a.center_id" \
  "SELECT a.center_id AS centre_du_bilan, p.center_id AS centre_du_patient, count(*) FROM resultats_analyses a JOIN patients p ON p.id = a.patient_id AND p.center_id <> a.center_id GROUP BY a.center_id, p.center_id" \
  "SELECT center_id, patient_id, analyte_code, date_prelevement, valeur_num, unite, valeur_texte, count(*) AS exemplaires FROM observations_biologiques GROUP BY center_id, patient_id, analyte_code, date_prelevement, valeur_num, unite, valeur_texte HAVING count(*) > 1" \
  "SELECT center_id, patient_id, date_prelevement, count(*) AS bilans FROM resultats_analyses GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1" \
  "SELECT o.center_id, count(*) AS demande_inconnue_ou_etrangere FROM observations_biologiques o LEFT JOIN demandes_examen d ON d.id = o.demande_examen_id AND d.patient_id = o.patient_id AND d.center_id = o.center_id WHERE o.demande_examen_id IS NOT NULL AND d.id IS NULL GROUP BY o.center_id" \
  "SELECT center_id, id FROM observations_biologiques WHERE analyte_code_system NOT IN ('CIM10','LOINC','ATC','LOCAL') OR statut NOT IN ('PRELIMINAIRE','FINAL','CORRIGE') OR source NOT IN ('SAISIE_DIRECTE','DERIVEE_BILAN','IMPORT') OR (valeur_num IS NOT NULL AND (unite IS NULL OR btrim(unite) = ''))" \
  "SELECT 'valeur_et_texte' AS controle, center_id, count(*) FROM observations_biologiques WHERE valeur_num IS NOT NULL AND valeur_texte IS NOT NULL AND btrim(valeur_texte) <> '' GROUP BY center_id UNION ALL SELECT 'ni_valeur_ni_texte', center_id, count(*) FROM observations_biologiques WHERE valeur_num IS NULL AND (valeur_texte IS NULL OR btrim(valeur_texte) = '') GROUP BY center_id UNION ALL SELECT 'analyte_non_loinc', center_id, count(*) FROM observations_biologiques WHERE analyte_code_system <> 'LOINC' GROUP BY center_id UNION ALL SELECT 'observations_dans_le_futur', center_id, count(*) FROM observations_biologiques WHERE date_prelevement > current_date GROUP BY center_id UNION ALL SELECT 'bilans_dans_le_futur', center_id, count(*) FROM resultats_analyses WHERE date_prelevement > current_date GROUP BY center_id" \
  "SELECT patient_id, max(date_prelevement) AS dernier_bilan, count(*) FROM resultats_analyses GROUP BY patient_id ORDER BY 1 LIMIT 50"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des ports ObservationsBiologiques et Analyses ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.ObservationsBiologiquesPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.AnalysesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
