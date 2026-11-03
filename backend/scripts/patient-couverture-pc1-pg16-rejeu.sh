#!/bin/bash
# Rejeu de la slice PC1 (patient-couverture : identite et mouvements) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en
# sortie. Chaine Flyway V1..Vn (psql), verification que TOUTES les colonnes que lisent ou ecrivent les adapters JDBC du module existent dans
# le schema reel (dont patients.date_evenement_etat, pas date_sortie), requetes SELECT-only de verifications-production.md (section Slice PC1)
# executees sur le schema reel, puis contrat PostgreSQL des patients et des mouvements (memes tests que H2 et que le double en memoire), dont
# la CONCURRENCE REELLE : deux admissions simultanees du meme numero (une seule passe, 422 ou 409 pour l'autre) et modification de fiche contre
# un traitement qui prend le centre puis la fiche (aucun interblocage) ; verrou du centre pris en tete de toute ecriture de fiche.
# A lancer en arriere-plan avec un log : backend/scripts/patient-couverture-pc1-pg16-rejeu.sh 55463 > rejeu-patient-couverture-pc1.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55463}"
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
JDBC="$RACINE/backend/contexts/patient-couverture/src/main/java/com/hemodialyse/backend/contexts/patientcouverture/infrastructure"
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
}
verifier_colonnes patients id center_id code_patient civilite nom prenom sexe groupe_sanguin nombre_enfants date_admission en_sommeil date_naissance lieu_naissance situation_familiale profession1 adresse tel_personnel tel_mobile tel_bureau email numero_assurance type_patient etat_patient date_evenement_etat qualite_assure observation sous_kt epo_enabled epo_date fer_enabled fer_date photo_base64 centre_payeur_id medecin_traitant_id salle_id position_id transporteur_aller_id transporteur_retour_id categorie_transport_id generateur_id jour_dimanche jour_lundi jour_mardi jour_mercredi jour_jeudi jour_vendredi jour_samedi assure_nom assure_prenom assure_numero_assurance assure_sexe assure_date_naissance assure_tel_personnel assure_tel_mobile assure_tel_bureau assure_adresse assure_groupe_sanguin assure_history_json pieces_jointes_json created_at
verifier_colonnes mouvement_patient id center_id patient_id type date_effet etat_precedent etat_nouveau salle_id position_id generateur_id jours_dialyse automatique cree_le
verifier_colonnes assure numero_assurance center_id nom prenom sexe date_naissance tel_personnel tel_mobile tel_bureau adresse groupe_sanguin created_at
verifier_colonnes assure_patient id patient_id numero_assurance center_id is_primary date_affectation date_debut_affectation date_fin_affectation
verifier_colonnes attestation_droit id patient_id center_id date_debut date_fin created_at
verifier_colonnes prise_en_charge id patient_id center_id date_debut_demande date_fin_demande forfait_demande_id statut created_at
verifier_colonnes centers id fuseau_horaire
verifier_colonnes gmao_equipements id code fabricant statut centre_id deleted_at
[ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_name='patients' AND column_name='date_sortie'")" = "0" ] \
  && echo "patients.date_sortie absente (le module gele la lisait a tort)" || { echo "ATTENDU : pas de patients.date_sortie"; RC=1; }

echo "=== PC1 : requetes de verifications-production.md (section Slice PC1), SELECT seulement, executables sur le schema reel ==="
req() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$2" >/dev/null && echo "requete $1 executable" || { echo "ATTENDU : requete $1 executable"; RC=1; }; }
req "1 (doublons de numero d'assurance par centre, casse et espaces ignores)" "SELECT center_id, UPPER(TRIM(numero_assurance)), count(*) FROM patients GROUP BY center_id, UPPER(TRIM(numero_assurance)) HAVING count(*) > 1 ORDER BY 3 DESC LIMIT 50"
req "2 (etats hors liste fermee)" "SELECT center_id, etat_patient, count(*) FROM patients WHERE etat_patient IS NOT NULL AND etat_patient NOT IN ('PERMANENT','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER','TRANSFERE','DECEDE','GREFFE','GUERRI') GROUP BY center_id, etat_patient ORDER BY 3 DESC"
req "3 (sorties et sejours limites sans date d'evenement)" "SELECT center_id, etat_patient, count(*) FROM patients WHERE etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') AND date_evenement_etat IS NULL GROUP BY center_id, etat_patient ORDER BY 3 DESC"
req "4 (naissance future ou posterieure a l'admission)" "SELECT center_id, count(*) FROM patients WHERE date_naissance > current_date OR date_naissance > date_admission GROUP BY center_id ORDER BY 2 DESC"
req "5 (lignes heritees aux colonnes nulles)" "SELECT center_id, count(*) FILTER (WHERE etat_patient IS NULL) AS etat_nul, count(*) FILTER (WHERE qualite_assure IS NULL) AS qualite_nulle, count(*) FILTER (WHERE en_sommeil IS NULL) AS sommeil_nul FROM patients GROUP BY center_id"
req "6 (numero du patient different du numero de l'assure pour un assure lui-meme)" "SELECT center_id, count(*) FROM patients WHERE qualite_assure = 'ASSURE_LUI_MEME' AND assure_numero_assurance IS DISTINCT FROM numero_assurance GROUP BY center_id ORDER BY 2 DESC"
req "7 (patients sans mouvement d'admission)" "SELECT p.center_id, count(*) FROM patients p WHERE NOT EXISTS (SELECT 1 FROM mouvement_patient m WHERE m.patient_id = p.id AND m.type = 'ADMISSION') GROUP BY p.center_id ORDER BY 2 DESC"
req "8 (types de mouvement hors liste)" "SELECT type, count(*) FROM mouvement_patient WHERE type NOT IN ('ADMISSION','SEJOUR_TEMPORAIRE','REPRISE','TRANSFERT','DECES','GREFFE','GUERISON','PLACE_LIBEREE') GROUP BY type"

cd "$RACINE/backend"
P=com.hemodialyse.backend.contexts.patientcouverture.contrat
echo "=== Contrat PostgreSQL des patients et des mouvements ==="
./mvnw -o -pl contexts/patient-couverture -am test \
  -Dtest="$P.PatientsJdbcPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpatientcouverture.pg16.url="$URL" -Dpatientcouverture.pg16.user=patient_couverture_test -Dpatientcouverture.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
