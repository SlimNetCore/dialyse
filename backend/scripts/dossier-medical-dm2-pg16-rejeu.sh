#!/bin/bash
# Rejeu de la slice DM2 (ordonnances, prescriptions) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit
# en sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par les adapters JDBC de DM2 (ordonnances, lignes_ordonnance,
# prescriptions_medicales, app_settings, patients) contre information_schema ; aucune table parallele ; requetes de parite de
# verifications-production.md (SELECT seule) executables sur le schema reel ; puis les contrats PostgreSQL des ports Ordonnances et
# Prescriptions (meme jeu de tests que le double en memoire et que H2).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm2-pg16-rejeu.sh 55472 > rejeu-dm2.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55472}"
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

echo "=== Colonnes lues et ecrites par les adapters JDBC de DM2 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes ordonnances id center_id patient_id medecin_id date_prescription statut numero created_at updated_at signed_at
verifier_colonnes lignes_ordonnance id ordonnance_id medicament_code_system medicament_code medicament_code_display libelle posologie voie duree_jours quantite instructions
verifier_colonnes prescriptions_medicales id patient_id center_id date_prescription medecin_id qb_cible qd_cible uf_max_ml duree_cible_min poids_sec_cible_kg type_dialyseur_prescrit anticoag_type_prescrit epo_article_id epo_dose_ui epo_voie epo_frequence_valeur epo_frequence_unite fer_article_id fer_dose_mg fer_voie fer_frequence_valeur fer_frequence_unite created_at updated_at
verifier_colonnes app_settings center_id cle prefixe dernier_compteur
verifier_colonnes patients id center_id
verifier_colonnes administrations_anemie id patient_id center_id prescription_medicale_id type_traitement molecule dose unite_dose voie date_administration seance_id administre_par administree motif_non_administration article_id quantite_article created_at
verifier_colonnes alertes_observance id patient_id center_id type_traitement type_alerte periode_debut periode_fin doses_attendues doses_administrees message created_at resolved_at
# CONSTAT NON BLOQUANT (KB-MED-33) : l'entite JPA legacy AlerteObservanceJpaEntity ecrit 4 colonnes de detail que la chaine Flyway V1..Vn ne cree pas
# (elles n'existent qu'avec ddl-auto: update). Le legacy a le meme ecart ; aucune migration n'est decidee ici.
for c in unite_dose dose_prescrite frequence_valeur frequence_unite; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='alertes_observance' AND column_name='$c'")" = "1" ] \
    && echo "ok : alertes_observance.$c" || echo "CONSTAT KB-MED-33 : alertes_observance.$c absente de la chaine Flyway (non bloquant)"
done
verifier_colonnes resultats_analyses id patient_id center_id date_prelevement hb_g_dl ferritine_ng_ml cstf_pct albumine_g_dl kt_v_mensuel created_at
verifier_colonnes centers id

echo "=== Aucune table parallele ==="
for t in ordonnance prescriptions prescription_dm2 numerotation_ordonnances; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM2 : requetes de parite de verifications-production.md (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT center_id, statut, count(*) AS ordonnances FROM ordonnances GROUP BY center_id, statut" \
  "SELECT center_id, numero, count(*) FROM ordonnances WHERE numero IS NOT NULL GROUP BY center_id, numero HAVING count(*) > 1" \
  "SELECT o.center_id, count(*) AS sans_ligne FROM ordonnances o WHERE NOT EXISTS (SELECT 1 FROM lignes_ordonnance l WHERE l.ordonnance_id = o.id) GROUP BY o.center_id" \
  "SELECT p.center_id, count(*) AS prescriptions, count(*) FILTER (WHERE pa.id IS NULL) AS patient_absent FROM prescriptions_medicales p LEFT JOIN patients pa ON pa.id = p.patient_id AND pa.center_id = p.center_id GROUP BY p.center_id" \
  "SELECT center_id, prefixe, dernier_compteur FROM app_settings WHERE cle = 'SEQ_ORD'"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des ports Ordonnances et Prescriptions ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.OrdonnancesPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.PrescriptionsPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
