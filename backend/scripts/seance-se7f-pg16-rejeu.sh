#!/bin/bash
# Rejeu de la sous-slice SE7f (retrait de bc-seance : derniers chemins VIVANTS des adaptateurs legacy de la plateforme) sur un PostgreSQL 16
# REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), jeu de donnees FICTIF minimal (les memes cas que les
# goldens H2 de backend/platform/src/test/resources/characterization/seance/se7f/), puis les requetes de production A L'IDENTIQUE
# (parametres remplaces par des litteraux) ; pour les requetes DERIVEES par Spring Data JPA (sans SQL litteral dans le depot), le SQL
# equivalent est note comme tel.
#   F1  SeancePlanningJdbcAdapter.java:43-45 : centre ferme = COUNT(center_holiday) + COUNT(center_closure_day) au jour (ferie seul : 1 ;
#       ferie et fermeture : 2 ; ferie d'un autre centre ou d'une autre date : 0)
#   F2  SeancePlanningJdbcAdapter.java:46-48 : colonne du jour de la semaine, sommeil, etat, dates du patient AU CENTRE
#   F3  SeancePlaceJdbcAdapter.java:33-34 : deplacement temporaire du jour, au centre
#   F4  VoletParamedicalJpaRepository.findBySeanceIdAndCenterId (derivee) : volet pose sous un autre centre invisible au centre de la seance
#   F5  V1 : contrainte UNIQUE (seance_id) de volet_paramedical et volet_medical : un second volet pour la meme seance, meme sous un autre
#       centre, est refuse par la base (golden volet_d_un_autre_centre : 500 DATA_ACCESS_ERROR)
#   F6  ResultatAnalyseJpaRepository.findByPatientIdAndCenterIdOrderByDatePrelevementDesc (derivee) : analyses du patient AU CENTRE, date
#       decroissante ; a date egale, ordre NON defini (KB-SEA-96) : observe, non impose
# Numerotation = celle de l'audit .ddd/safety-net/seance-se7f.md section 2.
# Lots A-E (ADR 0100) : le script rejoue ensuite les 5 detections SELECT de la section 65 de verifications-production.md, puis lance les
# contrats PostgreSQL des adapters repris (VoletsParamedicauxPostgresContractIT, LecturesReprisesDeLaPlateformePostgresContractIT dans
# contexts/seance ; DerniereCreatininePostgresContractIT dans contexts/dossier-medical) contre la base jetable (schema Flyway reel).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7f-pg16-rejeu.sh 55550
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en argument. Code de sortie : 0 si tout est conforme.
set -uo pipefail
PORT="${1:-55550}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${SEANCE_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
printf '%s' "$PW" | dd of="$TMP/pw" status=none
trap '"$PGBIN/pg_ctl" -s -D "$TMP/data" stop -m fast || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U seance_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C --no-instructions || exit 3
"$PGBIN/pg_ctl" -s -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U seance_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
RANG=0
for f in $(cd "$MIG" && ls V*.sql | sort -V); do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" || { echo "ECHEC de $f"; exit 3; }
  RANG=$((RANG + 1))
done
echo "Chaine Flyway appliquee ($RANG fichiers)"

A="99997800-0000-0000-0000-00000000000a"
B="99997800-0000-0000-0000-00000000000b"
PA="5e7f0000-0000-0000-0000-00000000000a"   # fiche au centre A
PB="5e7f0000-0000-0000-0000-00000000000b"   # fiche au centre B
SA="5e7f1000-0000-0000-0000-000000000001"   # seance du centre A (patient PA)
SF="5e7f1000-0000-0000-0000-000000000002"   # seance du centre A dont la fiche est au centre B
JOUR=2026-01-11   # <D+10>, un dimanche
SALLE="5a110000-0000-0000-0000-000000000001"; CRENEAU="c0e00000-0000-0000-0000-000000000001"; GEN="9e000000-0000-0000-0000-000000000001"

sql() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$1" | tr -d '\r'; }
sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, numero_assurance, type_patient, etat_patient, en_sommeil, jour_dimanche, jour_lundi) VALUES ('$PA', '$A', 'SE1-PAT_A', 'Patient PAT_A', 'Test', 'M', '1950-01-01', '2024-11-27', 'ASS-SE1-PAT_A', 'NON_VACANCIER', 'PERMANENT', NULL, TRUE, TRUE)" || exit 3
sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, jour_dimanche) VALUES ('$PB', '$B', 'SE1-FICHE_B', 'Patient FICHE_B', 'Test', 'M', '2024-11-27', 'ASS-SE1-FICHE_B', 'NON_VACANCIER', 'PERMANENT', TRUE)" || exit 3
sql "INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at, hors_planning) VALUES ('$SA', '$A', '$PA', '$JOUR', 'VALIDEE', '2026-01-01 10:00:00+00', FALSE), ('$SF', '$A', '$PB', '$JOUR', 'VALIDEE', '2026-01-01 10:00:00+00', FALSE)" || exit 3
sql "INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (gen_random_uuid(), '$A', '$JOUR', 'Ferie fictif'), (gen_random_uuid(), '$B', '$JOUR', 'Ferie du centre B'), (gen_random_uuid(), '$A', '2026-01-10', 'Ferie d''hier'), (gen_random_uuid(), '$A', '2026-01-12', 'Ferie de demain')" || exit 3
# (center_id, day_date) est UNIQUE sur center_closure_day et center_holiday (KB-SEA-58) : au plus un ferie et une fermeture par jour
sql "INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (gen_random_uuid(), '$A', '2026-01-13', 'Fermeture fictive'), (gen_random_uuid(), '$A', '2026-01-14', 'Fermeture')" || exit 3
sql "INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (gen_random_uuid(), '$A', '2026-01-14', 'Ferie et fermeture')" || exit 3
sql "INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id, motif, cree_le) VALUES (gen_random_uuid(), '$A', '$PA', '$JOUR', '$SALLE', '$CRENEAU', '$GEN', 'MAINTENANCE', '2026-01-01 10:00:00+00'), (gen_random_uuid(), '$B', '$PA', '$JOUR', '$SALLE', '$CRENEAU', '$GEN', 'MAINTENANCE', '2026-01-01 10:00:00+00'), (gen_random_uuid(), '$A', '$PA', '2026-01-12', '$SALLE', '$CRENEAU', '$GEN', 'MAINTENANCE', '2026-01-01 10:00:00+00')" || exit 3
sql "INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, created_at, updated_at) VALUES ('5e7f2000-0000-0000-0000-00000000000b', '$B', '$SA', 80.00, 77.50, '140/90', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3
sql "INSERT INTO volet_medical (id, center_id, seance_id, prescription, created_at, updated_at) VALUES ('5e7f3000-0000-0000-0000-00000000000b', '$B', '$SA', 'Prescription posee au centre B', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3
analyse() { # id centre date creatinine(ou NULL) ; patient fixe PA
  sql "INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, creatinine_mg_dl, created_at, updated_at) VALUES ('$1', '$PA', '$2', '$3', $4, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3; }
analyse 5e7f4000-0000-0000-0000-0000000000a1 "$A" 2026-01-11 1.0
analyse 5e7f4000-0000-0000-0000-0000000000b1 "$B" 2026-02-10 9.0
analyse 5e7f4000-0000-0000-0000-0000000000a2 "$A" 2026-01-21 9.0
analyse 5e7f4000-0000-0000-0000-0000000000a3 "$A" 2026-01-31 NULL
analyse 5e7f4000-0000-0000-0000-0000000000e1 "$A" 2026-03-01 1.0
analyse 5e7f4000-0000-0000-0000-0000000000e2 "$A" 2026-03-01 9.0
echo "Jeu de donnees pose"

RC=0
verifier() { # libelle obtenu attendu
  if [ "$2" = "$3" ]; then echo "OK    $1 : $2"; else echo "ECART $1"; echo "   obtenu : $2"; echo "   attendu : $3"; RC=1; fi
}
lignes() { paste -sd ';'; }
FERME="SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = '%s' AND day_date = '%s') + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = '%s' AND day_date = '%s')"

echo "=== F1 SeancePlanningJdbcAdapter:43-45 : centre ferme par ferie et/ou fermeture (compte, lu > 0) ==="
verifier "F1 ferie seul (centre A, $JOUR)" "$(sql "$(printf "$FERME" "$A" "$JOUR" "$A" "$JOUR")")" "1"
verifier "F1 fermeture seule (2026-01-13)" "$(sql "$(printf "$FERME" "$A" 2026-01-13 "$A" 2026-01-13)")" "1"
verifier "F1 ferie et fermeture le meme jour (2026-01-14)" "$(sql "$(printf "$FERME" "$A" 2026-01-14 "$A" 2026-01-14)")" "2"
verifier "F1 ferie d'un autre centre ignore (centre B seul ferie le 2026-01-15 : rien)" "$(sql "$(printf "$FERME" "$A" 2026-01-15 "$A" 2026-01-15)")" "0"
verifier "F1 ferie du centre B lu depuis le centre B" "$(sql "$(printf "$FERME" "$B" "$JOUR" "$B" "$JOUR")")" "1"

echo "=== F2 SeancePlanningJdbcAdapter:46-48 : situation du patient au centre (dimanche = jour_dimanche ; sommeil nul lu FALSE) ==="
verifier "F2 fiche au centre A" "$(sql "SELECT p.jour_dimanche, COALESCE(p.en_sommeil, FALSE), p.etat_patient, p.date_evenement_etat, p.date_admission FROM patients p WHERE p.center_id = '$A' AND p.id = '$PA'")" "t|f|PERMANENT||2024-11-27"
verifier "F2 fiche au centre B lue depuis le centre A : aucune ligne (PATIENT_INTROUVABLE, PlanningFourniLegacy -> vide)" "$(sql "SELECT p.jour_dimanche FROM patients p WHERE p.center_id = '$A' AND p.id = '$PB'")" ""

echo "=== F3 SeancePlaceJdbcAdapter:33-34 : deplacement temporaire du jour, au centre (autre centre et autre date ignores) ==="
verifier "F3 deplacement du jour au centre A" "$(sql "SELECT salle_id, creneau_id, generateur_id FROM deplacement_temporaire WHERE center_id = '$A' AND patient_id = '$PA' AND date_seance = '$JOUR'")" "$SALLE|$CRENEAU|$GEN"
verifier "F3 rien au 2026-01-13" "$(sql "SELECT salle_id FROM deplacement_temporaire WHERE center_id = '$A' AND patient_id = '$PA' AND date_seance = '2026-01-13'")" ""

echo "=== F4 VoletParamedicalJpaRepository.findBySeanceIdAndCenterId (SQL equivalent de la requete derivee) ==="
verifier "F4 volet sous le centre B invisible au centre A" "$(sql "SELECT id FROM volet_paramedical WHERE seance_id = '$SA' AND center_id = '$A'")" ""
verifier "F4 le meme volet lu au centre B" "$(sql "SELECT poids_avant_kg FROM volet_paramedical WHERE seance_id = '$SA' AND center_id = '$B'")" "80.00"
verifier "F4 volet medical sous le centre B invisible au centre A" "$(sql "SELECT id FROM volet_medical WHERE seance_id = '$SA' AND center_id = '$A'")" ""

echo "=== F5 V1 : UNIQUE (seance_id) sur volet_paramedical et volet_medical ; second volet de la meme seance refuse ==="
verifier "F5 contraintes d'unicite presentes" "$(sql "SELECT conrelid::regclass::text FROM pg_constraint WHERE contype = 'u' AND conrelid IN ('volet_paramedical'::regclass, 'volet_medical'::regclass) ORDER BY 1" | lignes)" "volet_medical;volet_paramedical"
ERREUR="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, created_at, updated_at) VALUES ('5e7f2000-0000-0000-0000-00000000000a', '$A', '$SA', 70.50, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" 2>&1 | tr -d '\r')"
verifier "F5 second volet (centre A) refuse par la cle d'unicite" "$(echo "$ERREUR" | grep -c 'duplicate key value violates unique constraint')" "1"
verifier "F5 le volet du centre B est intact, aucun volet au centre A" "$(sql "SELECT center_id FROM volet_paramedical WHERE seance_id = '$SA' ORDER BY center_id" | lignes)" "$B"

echo "=== F6 ResultatAnalyseJpaRepository...OrderByDatePrelevementDesc (SQL equivalent) : analyses du patient AU CENTRE, date decroissante ==="
verifier "F6 ordre au centre A (creatinine : 1.0 et 9.0 du 03-01, nulle du 01-31, 9.0 du 01-21, 1.0 du 01-11 ; le centre B absent)" \
  "$(sql "SELECT date_prelevement, creatinine_mg_dl FROM resultats_analyses WHERE patient_id = '$PA' AND center_id = '$A' ORDER BY date_prelevement DESC, creatinine_mg_dl" | lignes)" \
  "2026-03-01|1.00;2026-03-01|9.00;2026-01-31|;2026-01-21|9.00;2026-01-11|1.00"
verifier "F6 derniere creatinine non nulle du centre A sans les lignes du 03-01 (golden cloisonnement 03 : 9.0 du 01-21)" \
  "$(sql "SELECT creatinine_mg_dl FROM resultats_analyses WHERE patient_id = '$PA' AND center_id = '$A' AND creatinine_mg_dl IS NOT NULL AND date_prelevement < '2026-03-01' ORDER BY date_prelevement DESC LIMIT 1")" "9.00"
PREMIERE="$(sql "SELECT id FROM resultats_analyses WHERE patient_id = '$PA' AND center_id = '$A' ORDER BY date_prelevement DESC LIMIT 1")"
echo "F6 a date egale (2026-03-01, deux lignes), PostgreSQL 16 sert d'abord : $PREMIERE (ordre NON defini, KB-SEA-96 : observe, non impose)"
verifier "F6 les deux analyses du 03-01 sont servies" "$(sql "SELECT COUNT(*) FROM resultats_analyses WHERE patient_id = '$PA' AND center_id = '$A' AND date_prelevement = '2026-03-01'")" "2"

echo "=== Section 65 de verifications-production.md (ADR 0100, SELECT seulement) : detections rejouees a l'identique ==="
verifier "65.1 KB-SEA-94 : volets poses dans un autre centre que leur seance (un paramedical et un medical du centre B sur la seance SA du centre A)" \
  "$(sql "SELECT 'volet_paramedical' AS volet, s.center_id, v.center_id AS centre_du_volet, count(*) FROM volet_paramedical v JOIN seances s ON s.id = v.seance_id WHERE v.center_id <> s.center_id GROUP BY s.center_id, v.center_id
UNION ALL
SELECT 'volet_medical', s.center_id, v.center_id, count(*) FROM volet_medical v JOIN seances s ON s.id = v.seance_id WHERE v.center_id <> s.center_id GROUP BY s.center_id, v.center_id" | lignes)" \
  "volet_paramedical|$A|$B|1;volet_medical|$A|$B|1"
verifier "65.2 KB-SEA-95 : seances hors planning un jour ferie (aucune : le scan administrateur n'est pas rejoue en SQL)" \
  "$(sql "SELECT s.center_id, s.motif_hors_planning, count(*) FROM seances s JOIN center_holiday h ON h.center_id = s.center_id AND h.day_date = s.date_seance WHERE s.hors_planning = TRUE GROUP BY s.center_id, s.motif_hors_planning" | lignes)" ""
verifier "65.3 KB-SEA-96 : patients dont la derniere creatinine vaut 0 malgre une mesure positive (aucun dans ce jeu)" \
  "$(sql "SELECT r.center_id, r.patient_id, max(r.date_prelevement) AS derniere FROM resultats_analyses r WHERE r.creatinine_mg_dl IS NOT NULL GROUP BY r.center_id, r.patient_id HAVING max(CASE WHEN r.creatinine_mg_dl = 0 THEN r.date_prelevement END) = max(r.date_prelevement) AND max(CASE WHEN r.creatinine_mg_dl > 0 THEN r.date_prelevement END) IS NOT NULL" | lignes)" ""
verifier "65.4 KB-SEA-96 : deux creatinines du meme patient au centre le meme jour (PA au centre A le 2026-03-01)" \
  "$(sql "SELECT center_id, patient_id, date_prelevement, count(*) FROM resultats_analyses WHERE creatinine_mg_dl IS NOT NULL GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1 LIMIT 100" | lignes)" \
  "$A|$PA|2026-03-01|2"
verifier "65.5 volets orphelins (aucun)" \
  "$(sql "SELECT 'volet_paramedical' AS volet, count(*) FROM volet_paramedical v WHERE NOT EXISTS (SELECT 1 FROM seances s WHERE s.id = v.seance_id)
UNION ALL
SELECT 'volet_medical', count(*) FROM volet_medical v WHERE NOT EXISTS (SELECT 1 FROM seances s WHERE s.id = v.seance_id)" | lignes)" \
  "volet_paramedical|0;volet_medical|0"

echo "=== Contrats PostgreSQL des adapters repris par SE7f (ADR 0100) : volets paramedicaux, lectures reprises de la plateforme (Seance), derniere creatinine (Dossier medical) ==="
export SEANCE_PG16_PASSWORD="$PW"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
cd "$RACINE/backend" || exit 3
./mvnw -o -pl contexts/seance,contexts/dossier-medical test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.VoletsParamedicauxPostgresContractIT,com.hemodialyse.backend.contexts.seance.contrat.LecturesReprisesDeLaPlateformePostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.DerniereCreatininePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="$URL" -Dseance.pg16.user=seance_test -Dseance.pg16.password="$PW" \
  -Ddossiermedical.pg16.url="$URL" -Ddossiermedical.pg16.user=seance_test -Ddossiermedical.pg16.password="$PW" || RC=1

echo "=== Fin du rejeu : code $RC (0 = tout conforme) ==="
exit $RC
