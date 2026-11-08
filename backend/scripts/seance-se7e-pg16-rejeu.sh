#!/bin/bash
# Rejeu de la sous-slice SE7e (derniers lecteurs SQL de la table des seances cote plateforme : pilotage de la Direction, statistiques du
# patient, cahier de dialyse et son etat Jasper, reprise de donnees, usages d'un forfait) sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), jeu de donnees FICTIF minimal (les memes cas que les goldens H2 de
# backend/platform/src/test/resources/characterization/seance/se7e/), puis les requetes de production A L'IDENTIQUE (parametres remplaces
# par des litteraux) :
#   E1-E3  application/direction/DirectionDashboardQueryService.java:121, :167, :183 (centre, periode precedente, mois via EXTRACT)
#   E4     application/direction/DirectionBreakdownQueryService.java:190 (seances par caisse, jointure patients sur l'id ET le centre)
#   E5     application/direction/DirectionAbsencesQueryService.java:115 (meme predicat que E1, periode de fevrier)
#   E6-E9  application/query/PatientStatsQueryService.java:294-327 (compte sans statut, AVG d'un NUMERIC, poids sec en vigueur, libelles)
#   E10-11 infrastructure/reporting/CahierDialyseReportService.java:84 et reports/cahier_dialyse.jrxml:42-135 (requete de l'etat, entiere)
#   E12-14 persistence/adapter/migration/HistoricalRecordJdbcAdapter.java:94 et MigrationRollbackJdbcAdapter.java:83, :106
#   E15    persistence/adapter/referential/ReferentialAdminJdbcAdapter.java:201 (usage seances.forfait_override_id)
# Points propres a PostgreSQL verifies : precision de AVG sur NUMERIC (H2 : 12 decimales, KB-SEA-87), libelles de colonnes en minuscules
# (H2 : majuscules, cles JSON de poidsEvolution, KB-SEA-86), EXTRACT numerique, ordre des UUID a date egale (cahier), FETCH FIRST et CAST
# du jrxml.
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7e-pg16-rejeu.sh 55549
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), jamais en argument. Code de sortie : 0 si tout est conforme.
set -uo pipefail
PORT="${1:-55549}"
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
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"

A="99997800-0000-0000-0000-00000000000a"
B="99997800-0000-0000-0000-00000000000b"
C="99997800-0000-0000-0000-00000000000c"
# D et E : centres propres aux statistiques, au cahier et a la reprise (les goldens H2 les posent au centre A, dans des tests separes ;
# ici un seul jeu : un centre a part evite qu'ils ne comptent dans le pilotage). E joue le role de « l'autre centre ».
D="99997800-0000-0000-0000-00000000000d"
E="99997800-0000-0000-0000-00000000000e"
PA1="5e7e0000-0000-0000-0000-000000000001"; PA2="5e7e0000-0000-0000-0000-000000000002"; PA3="5e7e0000-0000-0000-0000-000000000003"
PB1="5e7e0000-0000-0000-0000-0000000000b1"; PC1="5e7e0000-0000-0000-0000-0000000000c1"
PS="5e7e0000-0000-0000-0000-000000000005"; PCD="5e7e0000-0000-0000-0000-000000000006"; P1="5e7e0000-0000-0000-0000-000000000007"
CAISSE_A="ca150000-0000-0000-0000-00000000000a"; AGENCE_A="a6e00000-0000-0000-0000-00000000000a"; PAYEUR_A="9a7e0000-0000-0000-0000-00000000000a"
CAISSE_B="ca150000-0000-0000-0000-00000000000b"; AGENCE_B="a6e00000-0000-0000-0000-00000000000b"; PAYEUR_B="9a7e0000-0000-0000-0000-00000000000b"
FORFAIT_PEC="f0f00000-0000-0000-0000-000000000001"; FORFAIT_SURCH="f0f00000-0000-0000-0000-000000000002"; FORFAIT_B="f0f00000-0000-0000-0000-000000000003"
C2="0e940000-0000-0000-0000-000000000002"; C3="49800000-0000-0000-0000-000000000003"

sql() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$1" | tr -d '\r'; }
# Requete non ASCII (accents du jrxml) : passee par un fichier en UTF-8, jamais en argument (Git Bash convertit les arguments dans la page
# de code Windows, ce que le serveur UTF8 refuse).
sqlf() {
  printf '%s' "$1" | dd of="$TMP/requete.sql" status=none
  PGCLIENTENCODING=UTF8 "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtA -f "$TMP/requete.sql" | tr -d '\r'
}
entete() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qAc "$1" | head -1 | tr -d '\r'; }
N=0
seance() { # centre patient date statut [id]
  N=$((N + 1))
  local id="${5:-5e7e1000-0000-0000-0000-$(printf '%012d' $N)}"
  sql "INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES ('$id', '$1', '$2', '$3', '$4', '2026-01-01 10:00:00+00')"
}
patient() { # centre id suffixe
  sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient) VALUES ('$2', '$1', 'SE7E-$3', 'Nom$3', 'Pre', 'M', '2024-01-01', 'ASS-$3', 'NON_VACANCIER')" || exit 3
}
patient "$A" "$PA1" PA1; patient "$A" "$PA2" PA2; patient "$A" "$PA3" PA3; patient "$B" "$PB1" PB1; patient "$C" "$PC1" PC1
patient "$D" "$PS" PS; patient "$D" "$PCD" PCD; patient "$D" "$P1" P1

# --- pilotage (golden repartition_par_caisse : jeu du pilotage + PA3 au centre payeur du centre B)
seance "$A" "$PA1" 2026-01-31 VALIDEE; seance "$A" "$PA1" 2026-02-15 SIGNEE; seance "$A" "$PA1" 2026-03-01 FACTUREE; seance "$A" "$PA1" 2026-03-02 VALIDEE
seance "$A" "$PA2" 2026-02-10 validee; seance "$A" "$PA2" 2026-02-11 'VALIDEE '; seance "$A" "$PA2" 2026-02-12 ANNULEE; seance "$A" "$PA2" 2026-02-13 ABSENT
seance "$A" "$PA2" 2026-02-14 CREE; seance "$A" "$PA2" 2026-02-16 PLANIFIEE; seance "$A" "$PA2" 2026-02-17 VALIDEE; seance "$A" "$PA2" 2026-02-17 SIGNEE
seance "$A" "$PB1" 2026-02-18 VALIDEE; seance "$A" "$PA3" 2026-02-19 VALIDEE
seance "$A" "$PA1" 2026-01-30 VALIDEE; seance "$A" "$PA1" 2026-01-01 SIGNEE; seance "$A" "$PA1" 2025-12-31 VALIDEE
seance "$B" "$PB1" 2026-02-20 VALIDEE; seance "$B" "$PB1" 2026-03-01 SIGNEE; seance "$B" "$PB1" 2026-01-11 FACTUREE
seance "$C" "$PC1" 2026-02-15 VALIDEE; seance "$C" "$PC1" 2026-01-06 VALIDEE
sql "INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES ('$CAISSE_A', '$A', 'CNAS', 'CNAS du centre A'), ('$CAISSE_B', '$B', 'CNAS-B', 'CNAS du centre B')"
sql "INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES ('$AGENCE_A', '$A', '$CAISSE_A', 'AG-CNAS', 'Agence'), ('$AGENCE_B', '$B', '$CAISSE_B', 'AG-CNAS-B', 'Agence B')"
sql "INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES ('$PAYEUR_A', '$A', '$AGENCE_A', 'CP-CNAS', 'Payeur'), ('$PAYEUR_B', '$B', '$AGENCE_B', 'CP-CNAS-B', 'Payeur B')"
sql "UPDATE patients SET centre_payeur_id = '$PAYEUR_A' WHERE id = '$PA1'"
sql "UPDATE patients SET centre_payeur_id = '$PAYEUR_B' WHERE id IN ('$PA3', '$PB1')"

# --- statistiques du patient PS (golden tous_statuts_et_volet_d_un_autre_centre, bornes_de_periode)
seance "$D" "$PS" 2026-02-01 VALIDEE 5e7e2000-0000-0000-0000-000000000001; seance "$D" "$PS" 2026-02-02 ANNULEE 5e7e2000-0000-0000-0000-000000000002
seance "$D" "$PS" 2026-02-03 validee 5e7e2000-0000-0000-0000-000000000003; seance "$D" "$PS" 2026-02-04 CREE 5e7e2000-0000-0000-0000-000000000004
seance "$D" "$PS" 2026-02-05 VALIDEE 5e7e2000-0000-0000-0000-000000000005; seance "$D" "$PS" 2026-02-05 SIGNEE 5e7e2000-0000-0000-0000-000000000006
seance "$D" "$PS" 2026-03-02 VALIDEE 5e7e2000-0000-0000-0000-000000000008; seance "$E" "$PS" 2026-02-05 VALIDEE 5e7e2000-0000-0000-0000-000000000007
volet() { # centre seance avant apres ta uf
  sql "INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ultrafiltration_ml) VALUES (gen_random_uuid(), '$1', '$2', $3, $4, '$5', $6)"
}
volet "$D" 5e7e2000-0000-0000-0000-000000000001 70.50 68.00 '130/80' 2500
volet "$D" 5e7e2000-0000-0000-0000-000000000002 72.00 70.00 '13/8' NULL
volet "$E" 5e7e2000-0000-0000-0000-000000000004 80.00 77.50 ' 140 - 90 ' NULL
volet "$D" 5e7e2000-0000-0000-0000-000000000005 69.25 67.75 '120/70' 1500
volet "$E" 5e7e2000-0000-0000-0000-000000000007 99.00 90.00 '150/95' 9000
presc() { # centre date poids maj
  sql "INSERT INTO prescriptions_medicales (id, center_id, patient_id, date_prescription, poids_sec_cible_kg, created_at, updated_at) VALUES (gen_random_uuid(), '$1', '$PS', '$2', $3, '2026-01-01 10:00:00+00', '$4')"
}
presc "$D" 2026-01-01 66.00 '2026-01-01 10:00:00+00'; presc "$D" 2026-02-01 NULL '2026-01-01 10:00:00+00'
presc "$D" 2026-02-03 67.50 '2026-01-01 10:00:00+00'; presc "$D" 2026-02-03 68.00 '2026-01-01 12:00:00+00'; presc "$E" 2026-02-04 99.00 '2026-01-01 10:00:00+00'

# --- cahier du patient PCD (golden statuts_bornes_et_egalite, surcharge_de_forfait)
sql "INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES ('$FORFAIT_PEC', '$D', 'FORFAIT_SE1', 'Forfait fictif', 100.00), ('$FORFAIT_SURCH', '$D', 'SURCH', 'Forfait SURCH', 150.00)"
sql "INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) VALUES (gen_random_uuid(), '$D', '$PCD', '2020-01-01', '2099-12-31', '2020-01-01', '2099-12-31', '$FORFAIT_PEC', '$FORFAIT_PEC', 'VALIDEE', '2026-01-01 10:00:00+00')"
seance "$D" "$PCD" 2026-02-01 VALIDEE 5e7e3000-0000-0000-0000-000000000001; seance "$D" "$PCD" 2026-02-10 SIGNEE "$C2"
seance "$D" "$PCD" 2026-02-10 FACTUREE "$C3"; seance "$D" "$PCD" 2026-02-15 validee; seance "$D" "$PCD" 2026-02-16 ANNULEE
seance "$D" "$PCD" 2026-02-28 VALIDEE 5e7e3000-0000-0000-0000-000000000006; seance "$D" "$PCD" 2026-03-01 VALIDEE 5e7e3000-0000-0000-0000-000000000007
seance "$E" "$PCD" 2026-02-11 VALIDEE
volet "$D" 5e7e3000-0000-0000-0000-000000000001 72.50 70.00 '13/8' 2500
sql "UPDATE seances SET forfait_override_id = '$FORFAIT_SURCH', forfait_override_code = 'SURCH', forfait_override_nom = 'Surcharge fictive', forfait_override_prix = 150.00 WHERE id = '5e7e3000-0000-0000-0000-000000000006'"
sql "UPDATE seances SET forfait_override_id = '$FORFAIT_SURCH' WHERE id = '5e7e3000-0000-0000-0000-000000000007'"

# --- reprise (golden deja_presente_et_annulation_bloquee) et referentiel (golden usages_par_les_seances)
seance "$D" "$P1" 2026-01-05 ANNULEE 5e7e4000-0000-0000-0000-000000000001; seance "$E" "$P1" 2026-01-07 VALIDEE 5e7e4000-0000-0000-0000-000000000002
seance "$D" "$P1" 2026-01-08 FACTUREE 5e7e4000-0000-0000-0000-000000000003
sql "INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES ('$FORFAIT_B', '$D', 'F-B', 'Forfait F-B', 100.00)"
sql "UPDATE seances SET forfait_override_id = '$FORFAIT_B' WHERE id = '5e7e4000-0000-0000-0000-000000000002'"
echo "Jeu de donnees pose ($N seances)"

RC=0
verifier() { # libelle obtenu attendu
  if [ "$2" = "$3" ]; then echo "OK    $1 : $2"; else echo "ECART $1"; echo "   obtenu : $2"; echo "   attendu : $3"; RC=1; fi
}
lignes() { paste -sd ';'; }
sort() { LC_ALL=C command sort "$@"; }   # tri octet par octet, independant de la locale de la machine

echo "=== E1 DirectionDashboardQueryService:121 : seances realisees par centre (societe = A, B), du 2026-01-31 au 2026-03-01 ==="
verifier "E1 par centre" "$(sql "SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '2026-01-31' AND '2026-03-01' AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id" | sort | lignes)" \
  "$A|7;$B|2"
echo "=== E2 DirectionDashboardQueryService:167 : periode precedente de meme duree (2026-01-01 au 2026-01-30) ==="
verifier "E2 total" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '2026-01-01' AND '2026-01-30' AND statut IN ('VALIDEE','SIGNEE','FACTUREE')")" "3"
echo "=== E3 DirectionDashboardQueryService:183 : par mois (EXTRACT numerique en PostgreSQL, lu par getInt) ==="
verifier "E3 mois" "$(sql "SELECT center_id, EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '2026-01-31' AND '2026-03-01' AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id, y, m" | sort | lignes)" \
  "$A|2026|1|1;$A|2026|2|5;$A|2026|3|1;$B|2026|2|1;$B|2026|3|1"
echo "=== E4 DirectionBreakdownQueryService:190 : seances par caisse (la seance du centre A sur la fiche du centre B disparait, KB-SEA-83) ==="
CAISSE_JOIN="LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id LEFT JOIN agence ag ON ag.id = cp.agence_id LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id "
verifier "E4 caisses" "$(sql "SELECT s.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n FROM seances s INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id ${CAISSE_JOIN}WHERE s.center_id IN ('$A','$B') AND s.date_seance BETWEEN '2026-01-31' AND '2026-03-01' AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, COALESCE(ca.code, '')" | sort | lignes)" \
  "$A|CNAS-B|CNAS du centre B|1;$A|CNAS|CNAS du centre A|3;$A|||2;$B|CNAS-B|CNAS du centre B|2"
echo "=== E5 DirectionAbsencesQueryService:115 : seances realisees de fevrier 2026 (denominateur du taux) ==="
verifier "E5 par centre" "$(sql "SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '2026-02-01' AND '2026-02-28' AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id" | sort | lignes)" \
  "$A|5;$B|1"

BASE=" FROM seances s LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id WHERE s.center_id = '$D' AND s.patient_id = '$PS' "
UF="COALESCE(vp.ultrafiltration_ml, (vp.poids_avant_kg - vp.poids_apres_kg) * 1000)"
POIDS_SEC="(SELECT pm.poids_sec_cible_kg FROM prescriptions_medicales pm WHERE pm.patient_id = s.patient_id AND pm.center_id = s.center_id AND pm.poids_sec_cible_kg IS NOT NULL AND pm.date_prescription <= s.date_seance ORDER BY pm.date_prescription DESC, pm.updated_at DESC LIMIT 1)"
echo "=== E6 PatientStatsQueryService:297 : compte sans predicat de statut (ANNULEE, validee, CREE comptes ; centre B exclu) ==="
verifier "E6 compte du 2026-02-01 au 2026-02-05" "$(sql "SELECT COUNT(1)${BASE} AND s.date_seance BETWEEN '2026-02-01' AND '2026-02-05'")" "6"
echo "=== E7 PatientStatsQueryService:298-300 : AVG d'un NUMERIC (H2 : 74.166666666667 ; PostgreSQL : precision du NUMERIC, KB-SEA-87) ==="
R="$(sql "SELECT AVG(vp.poids_avant_kg), AVG(vp.poids_apres_kg), AVG($UF)${BASE} AND s.date_seance <= '2026-02-04'")"
echo "valeurs servies par PostgreSQL 16 (to = 2026-02-04, trois volets dont un du centre B) : $R"
verifier "E7 moyennes (texte PostgreSQL)" "$R" "74.1666666666666667|71.8333333333333333|2333.3333333333333333"
verifier "E7 moyennes exactes du golden H2 (sans periode)" "$(sql "SELECT AVG(vp.poids_avant_kg), AVG(vp.poids_apres_kg), AVG($UF)${BASE}")" \
  "72.9375000000000000|70.8125000000000000|2125.0000000000000000"
echo "=== E8 PatientStatsQueryService:302 : poids sec en vigueur a la seance (meme jour : mise a jour la plus recente) ==="
verifier "E8 evolution (date, poids sec, UF)" "$(sql "SELECT s.date_seance, vp.poids_avant_kg, vp.poids_apres_kg, ${POIDS_SEC} AS poids_sec_cible_kg, ${UF} AS uf_reelle_ml${BASE} AND s.date_seance BETWEEN '2026-02-01' AND '2026-02-05' ORDER BY s.date_seance ASC" | cut -d'|' -f1,4,5 | sort | lignes)" \
  "2026-02-01|66.00|2500.00;2026-02-02|66.00|2000.00;2026-02-03|68.00|;2026-02-04|68.00|2500.00;2026-02-05|68.00|;2026-02-05|68.00|1500.00"
echo "=== E9 libelles de colonnes servis par PostgreSQL (cles JSON de poidsEvolution ; H2 : majuscules, KB-SEA-86) ==="
verifier "E9 libelles" "$(entete "SELECT s.date_seance, vp.poids_avant_kg, vp.poids_apres_kg, ${POIDS_SEC} AS poids_sec_cible_kg, ${UF} AS uf_reelle_ml${BASE} ORDER BY s.date_seance ASC")" \
  "date_seance|poids_avant_kg|poids_apres_kg|poids_sec_cible_kg|uf_reelle_ml"

echo "=== E10 CahierDialyseReportService:84 : compte des seances imprimables (casse exacte) ==="
verifier "E10 compte sans periode" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id = '$D' AND patient_id = '$PCD' AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND date_seance BETWEEN '1900-01-01' AND '2999-12-31'")" "5"
echo "=== E11 cahier_dialyse.jrxml:42-135 : requete de l'etat ENTIERE, parametres en litteraux (centre D, patient PCD, sans periode) ==="
JRXML="SELECT CAST(s.id AS VARCHAR(36)) AS seance_id,
             s.date_seance AS date_seance,
             CASE s.statut WHEN 'VALIDEE' THEN 'Validée' WHEN 'SIGNEE' THEN 'Signée' WHEN 'FACTUREE' THEN 'Facturée' ELSE s.statut END AS statut,
             CASE WHEN s.forfait_override_id IS NOT NULL THEN s.forfait_override_code ELSE f.code END AS forfait_code,
             CASE WHEN s.forfait_override_id IS NOT NULL THEN s.forfait_override_nom ELSE f.libelle END AS forfait_nom,
             CASE WHEN s.forfait_override_id IS NOT NULL THEN s.forfait_override_prix ELSE f.prix END AS forfait_prix,
             COALESCE(CASE WHEN POSITION('.' IN CAST(v.poids_avant_kg AS VARCHAR(40))) > 0 THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(v.poids_avant_kg AS VARCHAR(40)))) ELSE CAST(v.poids_avant_kg AS VARCHAR(40)) END, '-') AS poids_avant,
             COALESCE(CASE WHEN POSITION('.' IN CAST(v.poids_apres_kg AS VARCHAR(40))) > 0 THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(v.poids_apres_kg AS VARCHAR(40)))) ELSE CAST(v.poids_apres_kg AS VARCHAR(40)) END, '-') AS poids_apres,
             COALESCE(NULLIF(TRIM(v.ta_avant), ''), '-') AS ta_avant,
             COALESCE(NULLIF(TRIM(v.ta_apres), ''), '-') AS ta_apres,
             COALESCE(NULLIF(TRIM(CAST(v.duree_minutes AS VARCHAR(40))), ''), '-') AS duree,
             COALESCE(NULLIF(TRIM(CAST(v.debit_sang_ml_min AS VARCHAR(40))), ''), '-') AS debit_sang,
             COALESCE(CASE WHEN POSITION('.' IN CAST(v.ultrafiltration_ml AS VARCHAR(40))) > 0 THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(v.ultrafiltration_ml AS VARCHAR(40)))) ELSE CAST(v.ultrafiltration_ml AS VARCHAR(40)) END, '-') AS uf,
             COALESCE(NULLIF(TRIM(v.type_dialysat), ''), '-') AS dialysat,
             COALESCE(NULLIF(TRIM(v.anticoagulant), ''), '-') AS anticoagulant,
             COALESCE(NULLIF(TRIM(v.incidents), ''), '-') AS incidents,
             ac.code AS article_code,
             ac.libelle AS article_libelle,
             ac.unite AS article_unite,
             c.quantite AS quantite,
             c.prix_unitaire AS valeur_unitaire,
             c.total_valorise AS total_valorise,
             COALESCE(NULLIF(TRIM(d.nephropathie_initiale), ''), '-') AS nephropathie,
             d.date_mise_en_dialyse AS date_mise_dialyse,
             CASE d.hepatite_b_statut WHEN 'NEGATIF' THEN 'Négatif' WHEN 'PORTEUR' THEN 'Porteur' WHEN 'VACCINE' THEN 'Vacciné' WHEN 'IMMUNE' THEN 'Immunisé' WHEN 'INCONNU' THEN 'Inconnu' ELSE COALESCE(NULLIF(TRIM(d.hepatite_b_statut), ''), '-') END AS hepatite_b,
             CASE d.hepatite_c_statut WHEN 'NEGATIF' THEN 'Négatif' WHEN 'PORTEUR' THEN 'Porteur' WHEN 'VACCINE' THEN 'Vacciné' WHEN 'IMMUNE' THEN 'Immunisé' WHEN 'INCONNU' THEN 'Inconnu' ELSE COALESCE(NULLIF(TRIM(d.hepatite_c_statut), ''), '-') END AS hepatite_c,
             COALESCE(NULLIF(TRIM(d.observation_globale), ''), '-') AS observation_globale,
             COALESCE(NULLIF(TRIM(d.conclusion_medicale), ''), '-') AS conclusion_dossier,
             CASE WHEN pm.poids_sec_cible_kg IS NOT NULL
                  THEN CASE WHEN POSITION('.' IN CAST(pm.poids_sec_cible_kg AS VARCHAR(20))) > 0 THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(pm.poids_sec_cible_kg AS VARCHAR(20)))) ELSE CAST(pm.poids_sec_cible_kg AS VARCHAR(20)) END || ' kg'
                  ELSE '-' END AS poids_sec,
             CASE WHEN pm.id IS NULL THEN '-'
                  ELSE COALESCE(ea.libelle, ea.code, '-')
                       || CASE WHEN COALESCE(pm.epo_dose_ui, 0) > 0
                               THEN ' — ' || CAST(pm.epo_dose_ui AS VARCHAR(20)) || ' UI ' || COALESCE(pm.epo_voie, '') || ' '
                                    || COALESCE(CAST(pm.epo_frequence_valeur AS VARCHAR(10)), '') || '/'
                                    || CASE pm.epo_frequence_unite WHEN 'HEURE' THEN 'heure' WHEN 'JOUR' THEN 'jour' WHEN 'SEMAINE' THEN 'semaine' WHEN 'MOIS' THEN 'mois' WHEN 'ANNEE' THEN 'année' ELSE COALESCE(pm.epo_frequence_unite, '') END
                               ELSE '' END
             END AS epo_ligne,
             CASE WHEN pm.id IS NULL THEN '-'
                  ELSE COALESCE(fa.libelle, fa.code, '-')
                       || CASE WHEN COALESCE(pm.fer_dose_mg, 0) > 0
                               THEN ' — ' || CAST(pm.fer_dose_mg AS VARCHAR(20)) || ' mg ' || COALESCE(pm.fer_voie, '') || ' '
                                    || COALESCE(CAST(pm.fer_frequence_valeur AS VARCHAR(10)), '') || '/'
                                    || CASE pm.fer_frequence_unite WHEN 'HEURE' THEN 'heure' WHEN 'JOUR' THEN 'jour' WHEN 'SEMAINE' THEN 'semaine' WHEN 'MOIS' THEN 'mois' WHEN 'ANNEE' THEN 'année' ELSE COALESCE(pm.fer_frequence_unite, '') END
                               ELSE '' END
             END AS fer_ligne,
             COALESCE(NULLIF(TRIM(vm.prescription), ''), '-') AS med_prescription,
             COALESCE(NULLIF(TRIM(vm.tolerance_seance), ''), '-') AS med_tolerance,
             COALESCE(NULLIF(TRIM(vm.examen_clinique), ''), '-') AS med_examen,
             COALESCE(NULLIF(TRIM(vm.resultats_biologiques), ''), '-') AS med_resultats,
             COALESCE(NULLIF(TRIM(vm.ajustements_therapeutiques), ''), '-') AS med_ajustements,
             COALESCE(NULLIF(TRIM(vm.conclusion_medicale), ''), '-') AS med_conclusion
      FROM seances s
      LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id
      LEFT JOIN volet_medical vm ON vm.seance_id = s.id AND vm.center_id = s.center_id
      LEFT JOIN dossier_medical_patient d ON d.patient_id = s.patient_id AND d.center_id = s.center_id
      LEFT JOIN forfait f ON f.center_id = s.center_id AND f.id = COALESCE(
             (SELECT COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                FROM prise_en_charge p
               WHERE p.center_id = s.center_id AND p.patient_id = s.patient_id
                 AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
                 AND ((p.date_debut_effectif IS NOT NULL AND s.date_seance >= p.date_debut_effectif
                       AND (s.date_seance <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                   OR (p.date_debut_demande IS NOT NULL AND s.date_seance >= p.date_debut_demande
                       AND (s.date_seance <= p.date_fin_demande OR p.date_fin_demande IS NULL)))
               ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, p.created_at DESC
               FETCH FIRST 1 ROWS ONLY),
             (SELECT COALESCE(p2.forfait_effectif_id, p2.forfait_demande_id)
                FROM prise_en_charge p2
               WHERE p2.center_id = s.center_id AND p2.patient_id = s.patient_id
                 AND COALESCE(p2.forfait_effectif_id, p2.forfait_demande_id) IS NOT NULL
               ORDER BY CASE WHEN p2.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, p2.created_at DESC
               FETCH FIRST 1 ROWS ONLY))
      LEFT JOIN prescriptions_medicales pm ON pm.id = (
             SELECT p3.id FROM prescriptions_medicales p3
              WHERE p3.patient_id = s.patient_id AND p3.center_id = s.center_id AND p3.date_prescription <= s.date_seance
              ORDER BY p3.date_prescription DESC, p3.created_at DESC
              FETCH FIRST 1 ROWS ONLY)
      LEFT JOIN articles ea ON ea.id = pm.epo_article_id AND ea.center_id = pm.center_id
      LEFT JOIN articles fa ON fa.id = pm.fer_article_id AND fa.center_id = pm.center_id
      LEFT JOIN (SELECT sm.center_id AS center_id, sm.seance_id AS seance_id, sm.article_id AS article_id,
                        sm.prix_unitaire AS prix_unitaire, SUM(sm.quantite) AS quantite,
                        SUM(COALESCE(sm.quantite, 0) * COALESCE(sm.prix_unitaire, 0)) AS total_valorise
                   FROM stock_movements sm
                  WHERE sm.center_id = CAST('$D' AS UUID) AND sm.mouvement_type = 'SORTIE'
                  GROUP BY sm.center_id, sm.seance_id, sm.article_id, sm.prix_unitaire) c
             ON c.seance_id = s.id AND c.center_id = s.center_id
      LEFT JOIN articles ac ON ac.id = c.article_id AND ac.center_id = c.center_id
      WHERE s.center_id = CAST('$D' AS UUID)
        AND s.patient_id = CAST('$PCD' AS UUID)
        AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')
        AND s.date_seance BETWEEN CAST('1900-01-01' AS DATE) AND CAST('2999-12-31' AS DATE)
      ORDER BY s.date_seance DESC, s.id, ac.code"
verifier "E11 pages (date desc, puis identifiant croissant : 0e94... avant 4980...), statut, forfait, poids avant" \
  "$(sqlf "$JRXML" | cut -d'|' -f1-5,7 | lignes)" \
  "5e7e3000-0000-0000-0000-000000000007|2026-03-01|Validée|||-;5e7e3000-0000-0000-0000-000000000006|2026-02-28|Validée|SURCH|Surcharge fictive|-;$C2|2026-02-10|Signée|FORFAIT_SE1|Forfait fictif|-;$C3|2026-02-10|Facturée|FORFAIT_SE1|Forfait fictif|-;5e7e3000-0000-0000-0000-000000000001|2026-02-01|Validée|FORFAIT_SE1|Forfait fictif|72.5"

echo "=== E12 HistoricalRecordJdbcAdapter:94 : seance deja presente le meme jour au centre, quel que soit son statut (KB-SEA-89) ==="
verifier "E12 findExisting 2026-01-05 (ANNULEE)" "$(sql "SELECT id FROM seances WHERE center_id = '$D' AND date_seance = '2026-01-05' AND patient_id = '$P1' ORDER BY created_at")" \
  "5e7e4000-0000-0000-0000-000000000001"
verifier "E12 findExisting 2026-01-07 (seance de l'autre centre : rien au centre du lot)" "$(sql "SELECT id FROM seances WHERE center_id = '$D' AND date_seance = '2026-01-07' AND patient_id = '$P1' ORDER BY created_at")" ""
echo "=== E13 MigrationRollbackJdbcAdapter:83 : seances du patient repris, SANS centre (KB-SEA-90) ==="
verifier "E13 seances du patient" "$(sql "SELECT CAST(id AS VARCHAR(36)) FROM seances WHERE patient_id = '$P1'" | sort | lignes)" \
  "5e7e4000-0000-0000-0000-000000000001;5e7e4000-0000-0000-0000-000000000002;5e7e4000-0000-0000-0000-000000000003"
echo "=== E14 MigrationRollbackJdbcAdapter:106 : seance reprise facturee (facture_id), sans centre ==="
verifier "E14 facture_id nul" "$(sql "SELECT CAST(facture_id AS VARCHAR(36)) FROM seances WHERE id = '5e7e4000-0000-0000-0000-000000000003' AND facture_id IS NOT NULL")" ""
echo "=== E15 ReferentialAdminJdbcAdapter:201 : usages d'un forfait par seances.forfait_override_id, SANS centre ni statut (KB-SEA-91) ==="
verifier "E15 forfait du centre A surcharge sur une seance du centre B" "$(sql "SELECT COUNT(*) FROM seances WHERE forfait_override_id = '$FORFAIT_B'")" "1"
verifier "E15 forfait surcharge sur deux seances (dont une sans code)" "$(sql "SELECT COUNT(*) FROM seances WHERE forfait_override_id = '$FORFAIT_SURCH'")" "2"

echo "=== Fin du rejeu : code $RC (0 = tout conforme) ==="
exit $RC
