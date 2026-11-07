#!/bin/bash
# Rejeu de la slice DM8 (lectures publiees : dossier medical de base, export FHIR, lecteurs Direction/Planification) du Dossier medical
# sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes de
# dossier_medical_patient lues et ecrites par DossiersMedicauxDeBaseJdbc contre information_schema (et PRESENCE de l'unicite globale sur
# patient_id : un second dossier d'un patient dans un autre centre est refuse par la base -> 409) ; aucune table parallele ; requetes de
# parite de verifications-production.md section 52 (SELECT seule) executables sur le schema reel ; PREUVE de l'ordre FHIR des abords
# vasculaires sans date (date_creation DESC NULLS LAST, created_at DESC, id DESC : les abords sans date en dernier, departages par
# created_at puis id, ordre deterministe, divergence H2/PostgreSQL de DM5 resolue) ; puis les contrats PostgreSQL des ports (meme jeu de
# tests que les doubles en memoire et que H2, plus les tests a deux connexions : verrou de ligne et verrou consultatif -> 409).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm8-pg16-rejeu.sh 55497 > rejeu-dm8.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55497}"
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
# Mot de passe du cluster jetable transmis aux tests par l'environnement (placeholder Spring dossiermedical.pg16.password), jamais en -D
# sur la ligne de commande Maven (visible dans la liste des processus).
export DOSSIERMEDICAL_PG16_PASSWORD="$PW"
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

echo "=== Colonnes lues et ecrites par DossiersMedicauxDeBaseJdbc (schema reel) ==="
T=dossier_medical_patient
for c in id center_id patient_id nephropathie_initiale date_mise_en_dialyse hepatite_b_statut hepatite_c_statut observation_globale conclusion_medicale created_at updated_at; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T' AND column_name='$c'")" = "1" ] \
    || { echo "COLONNE ABSENTE : $T.$c"; RC=1; }
done
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T'")" = "11" ] \
  && echo "ok : $T a exactement 11 colonnes" || { echo "INATTENDU : colonnes de $T"; RC=1; }
# L'unicite globale sur patient_id existe en base (constat de DM8, KB-MED-98) : un doublon est refuse, 409 cote module.
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$T' AND indexdef ILIKE '%UNIQUE%' AND indexdef ILIKE '%(patient_id)%'")" = "1" ] \
  && echo "ok : unicite globale sur patient_id" || { echo "INATTENDU : unicite sur patient_id"; RC=1; }

echo "=== Aucune table parallele ==="
for t in dossier_medical dossiers_medicaux dossier_medical_de_base volet_medical_base; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM8 : requetes de parite de verifications-production.md section 52 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, count(*) FROM dossier_medical_patient GROUP BY center_id" \
  "SELECT patient_id, count(*) AS dossiers FROM dossier_medical_patient GROUP BY patient_id HAVING count(*) > 1" \
  "SELECT t.center_id, count(*) AS patient_absent_ou_autre_centre FROM dossier_medical_patient t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id" \
  "SELECT center_id, id FROM ordonnances WHERE statut NOT IN ('BROUILLON','SIGNEE','IMPRIMEE','ANNULEE')" \
  "SELECT center_id, patient_id, count(*) AS nombre FROM observations_biologiques GROUP BY center_id, patient_id HAVING count(*) > 500 UNION ALL SELECT center_id, patient_id, count(*) FROM ordonnances GROUP BY center_id, patient_id HAVING count(*) > 500"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

echo "=== Preuve : ordre FHIR des abords vasculaires sans date (PostgreSQL 16) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qc "INSERT INTO abords_vasculaires (id, actif, center_id, created_at, date_creation, patient_id, type_abord) VALUES ('00000000-0000-0000-0000-0000000000a1', true, '00000000-0000-0000-0000-00000000c001', '2026-01-01T10:00:00Z', '2025-05-01', '00000000-0000-0000-0000-0000000000f1', 'FAV'), ('00000000-0000-0000-0000-0000000000a2', true, '00000000-0000-0000-0000-00000000c001', '2026-01-02T10:00:00Z', NULL, '00000000-0000-0000-0000-0000000000f1', 'FAV'), ('00000000-0000-0000-0000-0000000000a3', true, '00000000-0000-0000-0000-00000000c001', '2026-01-02T10:00:00Z', NULL, '00000000-0000-0000-0000-0000000000f1', 'FAV'), ('00000000-0000-0000-0000-0000000000a4', true, '00000000-0000-0000-0000-00000000c001', '2026-01-03T10:00:00Z', '2026-03-01', '00000000-0000-0000-0000-0000000000f1', 'FAV')" || { echo "ECHEC de l'insertion de preuve"; RC=1; }
ORDRE="$(scalaire "SELECT string_agg(right(id::text, 2), ',') FROM (SELECT id FROM abords_vasculaires WHERE center_id = '00000000-0000-0000-0000-00000000c001' AND patient_id = '00000000-0000-0000-0000-0000000000f1' ORDER BY date_creation DESC NULLS LAST, created_at DESC, id DESC) s")"
[ "$ORDRE" = "a4,a1,a3,a2" ] && echo "ok : ordre $ORDRE (dates desc, puis sans date par created_at desc, id desc)" || { echo "ORDRE INATTENDU : $ORDRE"; RC=1; }
# Valeur par defaut de PostgreSQL SANS NULLS LAST : les abords sans date viendraient en tete (c'est la divergence H2/PostgreSQL de DM5).
DEFAUT="$(scalaire "SELECT string_agg(right(id::text, 2), ',') FROM (SELECT id FROM abords_vasculaires WHERE patient_id = '00000000-0000-0000-0000-0000000000f1' ORDER BY date_creation DESC, created_at DESC, id DESC) s")"
echo "pour memoire, sans NULLS LAST : $DEFAUT"
"${PSQL[@]}" -d hemo_test -qc "DELETE FROM abords_vasculaires WHERE patient_id = '00000000-0000-0000-0000-0000000000f1'" >/dev/null

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des ports DossiersMedicauxDeBase, Ordonnances et AbordsVasculaires ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.DossiersMedicauxDeBasePostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.*Ordonnances*PostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.*Abords*PostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
