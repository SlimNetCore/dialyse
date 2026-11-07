#!/bin/bash
# Rejeu de la slice SE1 (creation, scan, consultation et liste des seances) du contexte Seance sur un PostgreSQL 16 REEL et JETABLE :
# cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; les 13 colonnes lues et les 9 colonnes ecrites par SeancesJdbc sont
# controlees contre information_schema, la table garde EXACTEMENT ses 28 colonnes (aucune colonne ajoutee par le module), aucune table
# parallele, et l'ABSENCE d'unicite sur (center_id, patient_id, date_seance) est constatee (KB-SEA-02 : le module n'en invente pas) ; les
# requetes de verifications-production.md (SELECT seule) sont executables sur le schema reel ; puis le contrat PostgreSQL du port Seances
# (meme jeu de tests que le double en memoire et que H2, plus le verrou consultatif a deux connexions : verrou -> 409).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se1-pg16-rejeu.sh 55501 > rejeu-seance-se1.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en -D sur la ligne de commande Maven. Code de sortie : 0 si tout est vert.
set -uo pipefail
PORT="${1:-55501}"
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

echo "=== Colonnes lues et ecrites par SeancesJdbc (schema reel) ==="
T=seances
for c in id center_id patient_id date_seance statut created_at hors_planning motif_hors_planning precision_hors_planning validated_at signed_infirmier_at signed_medecin_at regularisation_deverrouillee_at; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T' AND column_name='$c'")" = "1" ] \
    || { echo "COLONNE ABSENTE : $T.$c"; RC=1; }
done
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T'")" = "28" ] \
  && echo "ok : $T a exactement 28 colonnes (aucune ajoutee par le module)" || { echo "INATTENDU : colonnes de $T"; RC=1; }
# Types et nullite des colonnes ecrites a la creation.
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T' AND column_name='precision_hors_planning' AND character_maximum_length=255")" = "1" ] \
  && echo "ok : precision_hors_planning varchar(255) (borne du domaine)" || { echo "INATTENDU : borne de precision_hors_planning"; RC=1; }
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$T' AND column_name='motif_hors_planning' AND character_maximum_length=20")" = "1" ] \
  && echo "ok : motif_hors_planning varchar(20)" || { echo "INATTENDU : borne de motif_hors_planning"; RC=1; }

echo "=== Aucune unicite sur (center_id, patient_id, date_seance) : constat de KB-SEA-02 (le module n'en invente pas) ==="
[ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$T' AND indexdef ILIKE '%UNIQUE%'")" = "1" ] \
  && echo "ok : seul l'index unique est la cle primaire" || { echo "INATTENDU : un index unique existe sur $T"; RC=1; }

echo "=== Aucune table parallele ==="
for t in seances_creation seance_creation seances_se1 seance_scan scans_seance; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== SE1 : requetes de controle (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, patient_id, date_seance, count(*) AS seances FROM seances GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1" \
  "SELECT id, center_id, statut FROM seances WHERE statut NOT IN ('CREE','VALIDEE','SIGNEE','FACTUREE','ABSENT')" \
  "SELECT id, center_id FROM seances WHERE hors_planning AND (motif_hors_planning IS NULL OR motif_hors_planning NOT IN ('RATTRAPAGE','URGENCE','AUTRE') OR (motif_hors_planning = 'AUTRE' AND (precision_hors_planning IS NULL OR TRIM(precision_hors_planning) = '')))" \
  "SELECT s.center_id, count(*) AS patient_absent_ou_autre_centre FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE p.id IS NULL GROUP BY s.center_id"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

echo "=== Colonnes lues par les lectures SQL de la plateforme (historique et forfait courant, schema reel) ==="
colonnes() {
  local t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
}
colonnes seances forfait_override_id forfait_override_code forfait_override_nom forfait_override_prix forfait_override_updated_at forfait_override_updated_by
colonnes patients id center_id nom prenom code_patient
colonnes forfait id center_id code libelle prix
colonnes prise_en_charge center_id patient_id forfait_effectif_id forfait_demande_id date_debut_effectif date_fin_effectif date_debut_demande date_fin_demande created_at
echo "colonnes des lectures de la plateforme controlees"

cd "$RACINE/backend"
echo "=== Contrat PostgreSQL des lectures de la plateforme (historique et forfait courant : meme jeu que H2, schema dedie se1_lectures) ==="
./mvnw -o -pl platform -am test \
  -Dtest="com.hemodialyse.backend.infrastructure.acl.seance.LecturesDeLaSeancePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Contrat PostgreSQL du port Seances (meme jeu que le double en memoire et H2, plus le verrou a deux connexions) ==="
./mvnw -o -pl contexts/seance -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.SeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Dseance.pg16.user=seance_test || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
