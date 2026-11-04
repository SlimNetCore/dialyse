#!/bin/bash
# Rejeu des slices PC2 (assure principal) et PC3 (prise en charge) de patient-couverture sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes lues ou ecrites par les adapters JDBC
# (assure, assure_patient, prise_en_charge, attestation_droit en lecture, patients) existent dans le schema reel, requetes SELECT-only de
# verifications-production.md (sections PC2 et PC3) executees sur le schema reel, puis le contrat PostgreSQL du module (memes tests que
# H2 et que les doubles en memoire), dont les COURSES REELLES : deux affectations simultanees d'assures au meme patient (un seul principal
# actif, R1), deux validations simultanees de la meme PEC (une seule passe), DEUX CENTRES declarant le meme numero d'assure en meme temps (un seul le detient, l'autre 422 ASSURE_D_UN_AUTRE_CENTRE, identite non ecrasee : KB-PAT-31), plus les courses de la fiche (PC1) ; verrou du centre pris en
# tete de toute ecriture.
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-pc2-pc3-pg16-rejeu.sh 55464 > rejeu-pc2-pc3.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55464}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U patient_couverture_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U patient_couverture_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
RC=0
scalaire() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '\r'; }

echo "=== Colonnes lues ou ecrites par les adapters JDBC du module (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes assure numero_assurance center_id nom prenom sexe date_naissance tel_personnel tel_mobile tel_bureau adresse groupe_sanguin created_at
verifier_colonnes assure_patient id patient_id numero_assurance center_id is_primary date_affectation date_debut_affectation date_fin_affectation
verifier_colonnes prise_en_charge id patient_id center_id date_debut_demande date_fin_demande forfait_demande_id date_debut_effectif date_fin_effectif forfait_effectif_id statut created_at
verifier_colonnes attestation_droit id patient_id center_id date_debut date_fin created_at
verifier_colonnes planning_parametres center_id jours_ouverts updated_at

echo "=== PC2 et PC3 : requetes de verifications-production.md, SELECT seulement, executables sur le schema reel ==="
req() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }; }
req "PC2-1 (plusieurs assures principaux actifs pour un patient, R1)" "SELECT center_id, patient_id, count(*) FROM assure_patient WHERE is_primary = true AND date_fin_affectation IS NULL GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "PC2-2 (assures dont le centre differe de celui de leur patient)" "SELECT a.numero_assurance, a.center_id AS centre_assure, ap.center_id AS centre_affectation FROM assure a JOIN assure_patient ap ON ap.numero_assurance = a.numero_assurance WHERE a.center_id <> ap.center_id LIMIT 50"
req "PC2-3 (lignes d'affectation sans dates par patient et assure : doublons historiques de la fiche)" "SELECT patient_id, numero_assurance, count(*) FROM assure_patient WHERE date_debut_affectation IS NULL GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "PC2-4 (affectations dont l'assure a disparu)" "SELECT ap.id, ap.numero_assurance FROM assure_patient ap WHERE NOT EXISTS (SELECT 1 FROM assure a WHERE a.numero_assurance = ap.numero_assurance) LIMIT 50"
req "PC3-1 (statuts de PEC hors liste)" "SELECT statut, count(*) FROM prise_en_charge WHERE statut NOT IN ('CREE','VALIDEE','CLOTUREE') GROUP BY statut"
req "PC3-2 (periodes inversees ou absentes)" "SELECT statut, count(*) FILTER (WHERE date_fin_demande < date_debut_demande) AS inversees, count(*) FILTER (WHERE date_debut_demande IS NULL OR date_fin_demande IS NULL) AS sans_date FROM prise_en_charge GROUP BY statut"
req "PC3-3 (PEC validees simultanees du meme patient)" "SELECT center_id, patient_id, count(*) FROM prise_en_charge WHERE statut = 'VALIDEE' GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "PC3-4 (PEC dont le patient a disparu ou est d'un autre centre)" "SELECT c.id, c.center_id FROM prise_en_charge c LEFT JOIN patients p ON p.id = c.patient_id AND p.center_id = c.center_id WHERE p.id IS NULL LIMIT 50"
req "PC3-5 (PEC validee sans forfait effectif)" "SELECT center_id, count(*) FROM prise_en_charge WHERE statut = 'VALIDEE' AND forfait_effectif_id IS NULL GROUP BY 1"
req "42-2 (assures partages entre centres)" "SELECT ap.numero_assurance, count(DISTINCT ap.center_id) AS centres FROM assure_patient ap GROUP BY 1 HAVING count(DISTINCT ap.center_id) > 1 ORDER BY 2 DESC LIMIT 50"
req "42-3 (lignes assure_patient dupliquees)" "SELECT patient_id, numero_assurance, count(*) FROM assure_patient GROUP BY 1, 2 HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "42-4 (affectations sans debut, inversees, de duree nulle)" "SELECT center_id, count(*) FILTER (WHERE date_debut_affectation IS NULL) AS sans_debut, count(*) FILTER (WHERE date_fin_affectation < date_debut_affectation) AS inversees, count(*) FILTER (WHERE date_fin_affectation = date_debut_affectation) AS duree_nulle FROM assure_patient GROUP BY 1"
req "42-5 (assures sans nom)" "SELECT center_id, count(*) FROM assure WHERE nom IS NULL OR TRIM(nom) = '' GROUP BY 1 ORDER BY 2 DESC"
req "42-6 (PEC d'un autre centre que leur patient)" "SELECT c.center_id AS centre_pec, p.center_id AS centre_patient, count(*) FROM prise_en_charge c JOIN patients p ON p.id = c.patient_id WHERE c.center_id <> p.center_id GROUP BY 1, 2"
req "42-8 (PEC chevauchantes du meme patient)" "SELECT a.patient_id, a.id, b.id FROM prise_en_charge a JOIN prise_en_charge b ON a.patient_id = b.patient_id AND a.id < b.id AND a.date_debut_demande <= b.date_fin_demande AND b.date_debut_demande <= a.date_fin_demande LIMIT 50"
req "42-9 (forfaits effectifs inconnus ou d'un autre centre)" "SELECT c.center_id, count(*) FROM prise_en_charge c LEFT JOIN forfait f ON f.id = c.forfait_effectif_id WHERE c.forfait_effectif_id IS NOT NULL AND (f.id IS NULL OR f.center_id <> c.center_id) GROUP BY 1"
req "42-11 (centres sans ligne planning_parametres)" "SELECT c.id, c.name FROM centers c WHERE NOT EXISTS (SELECT 1 FROM planning_parametres p WHERE p.center_id = c.id)"
req "42-12 (comptes ADMIN actifs sans centre de rattachement)" "SELECT u.username, u.full_name FROM app_user u JOIN app_user_role ur ON ur.user_id = u.id JOIN app_role r ON r.id = ur.role_id WHERE u.active AND r.code = 'ADMIN' AND NOT EXISTS (SELECT 1 FROM app_user_center uc WHERE uc.user_id = u.id)"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL du module (patients, mouvements, assures, PEC) et courses reelles ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.PatientsJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
