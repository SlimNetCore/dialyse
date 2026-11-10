#!/bin/bash
# Rejeu de la sous-slice SE7d (lectures de la table des seances par Absences patients, Planification et Dossier medical) sur un
# PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), jeu de donnees FICTIF minimal, puis
# les huit requetes de production A L'IDENTIQUE (parametres remplaces par des litteraux). Depuis le lot B/C de SE7d (ADR 0098), elles vivent
# dans la Seance, mot pour mot :
#   A1-A3, A5, A4, P1  contexts/seance/.../persistence/LecturesDesSeancesRealiseesJdbc.java (joursRealisesDuCentre, joursRealisesDuPatient,
#                      estRealisee, patientsRealisesLe, absenceLiee, duCentre) ; avant SE7d : Absences legacyseance/SeancesRealiseesJdbc,
#                      legacyseance/ExclusionsDeSeancesJdbc, legacyplanning/PatientsAttendusJdbc, Planification legacyseance/SeancesRealiseesJdbc
#   D1-D2              contexts/seance/.../persistence/ConstantesDesSeancesJdbc.java (nombreDeSeances, page) ; avant : HistoriqueDesConstantesJdbc
# Puis les cinq requetes de detection (SELECT seule) de la section 63 de .ddd/migration/verifications-production.md (ADR 0098, annexe),
# et les contrats PostgreSQL : SeancesRealiseesEtConstantesPostgresContractIT (Seance, 15 tests) et HistoriqueDesConstantesPostgresContractIT
# (Dossier medical, 9 tests, ACL de production au-dessus de l'adapter de la Seance), dans la base jetable uniquement.
# Points propres a PostgreSQL verifies : ordre des NULL de created_at en tri decroissant (KB-SEA-79 corrige : NULLS FIRST explicite, ADR 0117),
# blancs de fin de statut (varchar : significatifs), comparaison des UUID (octets non signes), ordre entre lignes d'une meme date (KB-SEA-77, 78
# corriges : deterministe, ADR 0117), lien d'exclusion borne a une seance ABSENT (KB-SEA-81 corrige).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7d-pg16-rejeu.sh 55548
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), transmis a Maven par l'environnement (SEANCE_PG16_PASSWORD,
# DOSSIERMEDICAL_PG16_PASSWORD), jamais en -D. Code de sortie : 0 si tout est conforme.
set -uo pipefail
PORT="${1:-55548}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${SEANCE_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -s -D "$TMP/data" stop -m fast || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U seance_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C --no-instructions || exit 3
"$PGBIN/pg_ctl" -s -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U seance_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
# ADR 0114 (lot L3) : ce rejeu pose volontairement des statuts non canoniques (KB-SEA-82) et des identifiants de facture sans facture ; sur
# cette base JETABLE, on retire les contraintes de production V54 (cle etrangere) et V55 (CHECK du statut), toutes deux NOT VALID. Elles sont
# testees pour elles-memes par seance-se7c2-pg16-rejeu.sh.
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qc "ALTER TABLE seances DROP CONSTRAINT IF EXISTS fk_seances_facture, DROP CONSTRAINT IF EXISTS ck_seances_statut_canonique" || exit 3

A="99997800-0000-0000-0000-00000000000a"
B="99997800-0000-0000-0000-00000000000b"
P1="5e7d0000-0000-0000-0000-000000000001"; P2="5e7d0000-0000-0000-0000-000000000002"; P3="5e7d0000-0000-0000-0000-000000000003"
P4="5e7d0000-0000-0000-0000-000000000004"; P5="5e7d0000-0000-0000-0000-000000000005"; P6="5e7d0000-0000-0000-0000-000000000006"
P7="5e7d0000-0000-0000-0000-000000000007"; PB="5e7d0000-0000-0000-0000-00000000000b"
SALLE1="5a110000-0000-0000-0000-000000000001"; SALLE2="5a110000-0000-0000-0000-000000000002"
EGAL_A="ceada716-0000-0000-0000-000000000001"; EGAL_B="e34516e5-0000-0000-0000-000000000002"; EGAL_C="403174a9-0000-0000-0000-000000000003"
NULLE="5e7d2000-0000-0000-0000-000000000001"; FIXE="5e7d2000-0000-0000-0000-000000000002"; TARD="5e7d2000-0000-0000-0000-000000000003"
ABS_X="ab500000-0000-0000-0000-000000000001"; ABS_Y="ab500000-0000-0000-0000-000000000002"; ABS_Z="ab500000-0000-0000-0000-000000000003"

sql() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$1" | tr -d '\r'; }
N=0
seance() { # centre patient date statut [id] [created_at]
  N=$((N + 1))
  local id="${5:-5e7d1000-0000-0000-0000-$(printf '%012d' $N)}"
  local cree="${6:-2026-01-01 10:00:00+00}"
  if [ "$cree" = "NULL" ]; then cree="NULL"; else cree="'$cree'"; fi
  sql "INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES ('$id', '$1', '$2', '$3', '$4', $cree)"
}
for p in "$A:$P1" "$A:$P2" "$A:$P3" "$A:$P4" "$A:$P5" "$A:$P6" "$A:$P7" "$B:$PB"; do
  c="${p%%:*}"; i="${p##*:}"
  sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient) VALUES ('$i', '$c', 'SE7D-${i: -2}', 'Nom${i: -2}', 'Pre', 'M', '2024-01-01', 'ASS-${i: -2}', 'NON_VACANCIER')" || exit 3
done
# semaine du dimanche 2026-03-01 (A1, A5)
seance "$A" "$P3" 2026-03-03 FACTUREE; seance "$A" "$P2" 2026-03-03 VALIDEE; seance "$A" "$P1" 2026-03-03 VALIDEE
seance "$A" "$P1" 2026-03-03 SIGNEE; seance "$A" "$P2" 2026-03-02 SIGNEE; seance "$A" "$P1" 2026-03-07 VALIDEE
seance "$A" "$P1" 2026-03-08 VALIDEE; seance "$A" "$P1" 2026-02-28 VALIDEE; seance "$B" "$PB" 2026-03-03 VALIDEE
# casse et blancs (A3) ; jours du patient (A2)
seance "$A" "$P1" 2026-01-31 VALIDEE; seance "$A" "$P1" 2026-02-01 validee; seance "$A" "$P1" 2026-02-02 'VALIDEE '
seance "$A" "$P1" 2026-02-03 ' VALIDEE'; seance "$A" "$P1" 2026-02-04 Signee; seance "$A" "$P1" 2026-02-05 facturee
seance "$A" "$P1" 2026-01-12 VALIDEE; seance "$A" "$P1" 2026-01-12 FACTUREE; seance "$A" "$P1" 2026-01-13 'SIGNEE '
seance "$A" "$P1" 2026-01-14 SIGNEE
# exclusion (A4)
seance "$A" "$P1" 2026-01-20 VALIDEE 5e7d3000-0000-0000-0000-000000000001; seance "$B" "$PB" 2026-01-20 ABSENT 5e7d3000-0000-0000-0000-000000000002
sql "UPDATE seances SET exclusion_absence_id = '$ABS_X' WHERE id = '5e7d3000-0000-0000-0000-000000000001'"
sql "UPDATE seances SET exclusion_absence_id = '$ABS_Y' WHERE id = '5e7d3000-0000-0000-0000-000000000002'"
seance "$A" "$P2" 2026-01-20 ABSENT 5e7d3000-0000-0000-0000-000000000003
sql "UPDATE seances SET exclusion_absence_id = '$ABS_Z' WHERE id = '5e7d3000-0000-0000-0000-000000000003'"
# planification (P1) : semaine du 2026-04-05 ; P6 dont la fiche passe au centre B ; P7 deux seances realisees le meme jour
seance "$A" "$P6" 2026-04-07 VALIDEE 5e7d4000-0000-0000-0000-000000000001
seance "$A" "$P7" 2026-04-07 VALIDEE 5e7d4000-0000-0000-0000-000000000002; seance "$A" "$P7" 2026-04-07 SIGNEE 5e7d4000-0000-0000-0000-000000000003
sql "UPDATE seances SET salle_id = '$SALLE1' WHERE id = '5e7d4000-0000-0000-0000-000000000002'"
sql "UPDATE seances SET salle_id = '$SALLE2' WHERE id = '5e7d4000-0000-0000-0000-000000000003'"
sql "UPDATE patients SET center_id = '$B' WHERE id = '$P6'"
# dossier medical (D1, D2) : egalite complete (P4), creation nulle (P5)
seance "$A" "$P4" 2026-01-06 VALIDEE "$EGAL_A"; seance "$A" "$P4" 2026-01-06 VALIDEE "$EGAL_B"; seance "$A" "$P4" 2026-01-06 VALIDEE "$EGAL_C"
seance "$A" "$P4" 2026-01-07 ANNULEE 5e7d5000-0000-0000-0000-000000000001; seance "$A" "$P4" 2026-01-05 CREE 5e7d5000-0000-0000-0000-000000000002
seance "$A" "$P5" 2026-01-06 VALIDEE "$NULLE" NULL; seance "$A" "$P5" 2026-01-06 SIGNEE "$FIXE"
seance "$A" "$P5" 2026-01-06 VALIDEE "$TARD" "2026-01-01 11:00:00+00"
echo "Jeu de donnees pose ($N seances)"

RC=0
verifier() { # libelle obtenu attendu
  if [ "$2" = "$3" ]; then echo "OK    $1 : $2"; else echo "ECART $1"; echo "   obtenu : $2"; echo "   attendu : $3"; RC=1; fi
}
lignes() { paste -sd ';'; }
REALISEE="s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')"

echo "=== A1 SeancesRealiseesJdbc.duCentre (absences) : semaine du 2026-03-01 ==="
R="$(sql "SELECT DISTINCT s.patient_id, s.date_seance FROM seances s WHERE s.center_id = '$A' AND s.date_seance BETWEEN '2026-03-01' AND '2026-03-07' AND $REALISEE ORDER BY s.date_seance, s.patient_id")"
echo "ordre servi par PostgreSQL 16 (date puis patient, deterministe, KB-SEA-77 corrige, ADR 0117) : $(echo "$R" | lignes)"
verifier "A1 dates croissantes" "$(echo "$R" | cut -d'|' -f2 | lignes)" "$(echo "$R" | cut -d'|' -f2 | sort | lignes)"
verifier "A1 ordre exact" "$(echo "$R" | lignes)" \
  "$P2|2026-03-02;$P1|2026-03-03;$P2|2026-03-03;$P3|2026-03-03;$P1|2026-03-07"

echo "=== A2 SeancesRealiseesJdbc.joursDuPatient : jours distincts, blancs de fin ignores ==="
verifier "A2 jours" "$(sql "SELECT DISTINCT s.date_seance FROM seances s WHERE s.center_id = '$A' AND s.patient_id = '$P1' AND s.date_seance BETWEEN '2026-01-11' AND '2026-01-20' AND $REALISEE ORDER BY s.date_seance" | lignes)" \
  "2026-01-12;2026-01-14;2026-01-20"
verifier "A2 bornes inversees" "$(sql "SELECT DISTINCT s.date_seance FROM seances s WHERE s.center_id = '$A' AND s.patient_id = '$P1' AND s.date_seance BETWEEN '2026-01-20' AND '2026-01-11' AND $REALISEE ORDER BY s.date_seance" | lignes)" ""

echo "=== A3 SeancesRealiseesJdbc.realisee : casse et blancs du statut ==="
OBT=""
for jour in 2026-01-31 2026-02-01 2026-02-02 2026-02-03 2026-02-04 2026-02-05; do
  OBT="$OBT$(sql "SELECT COUNT(*) FROM seances s WHERE s.center_id = '$A' AND s.patient_id = '$P1' AND s.date_seance = '$jour' AND $REALISEE");"
done
verifier "A3 VALIDEE;validee;'VALIDEE ';' VALIDEE';Signee;facturee" "${OBT%;}" "1;0;0;0;0;0"

echo "=== A4 ExclusionsDeSeancesJdbc : lien d'exclusion borne au centre ET a une seance ABSENT (KB-SEA-81 corrige, ADR 0117) ==="
verifier "A4 lien residuel sur seance VALIDEE du centre A : ne bloque plus" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id = '$A' AND exclusion_absence_id = '$ABS_X' AND statut = 'ABSENT'")" "0"
verifier "A4 lien sur seance ABSENT du centre A : bloque" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id = '$A' AND exclusion_absence_id = '$ABS_Z' AND statut = 'ABSENT'")" "1"
verifier "A4 lien sur seance du centre B lu par A" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id = '$A' AND exclusion_absence_id = '$ABS_Y' AND statut = 'ABSENT'")" "0"

echo "=== A5 PatientsAttendusJdbc : patients ecartes le 2026-03-03 (doublons possibles, ramenes a un ensemble par le code) ==="
verifier "A5 patients" "$(sql "SELECT s.patient_id FROM seances s WHERE s.center_id = '$A' AND s.date_seance = '2026-03-03' AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')" | sort | lignes)" \
  "$P1;$P1;$P2;$P3"

echo "=== P1 SeancesRealiseesJdbc (planification) : jointure patients sur l'identifiant ET le centre ==="
R="$(sql "SELECT s.patient_id, s.date_seance, s.salle_id, s.creneau_id, s.generateur_id, p.nom, p.prenom FROM seances s JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.center_id = '$A' AND s.date_seance BETWEEN '2026-04-05' AND '2026-04-11' AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') ORDER BY s.date_seance, s.created_at NULLS LAST, s.id")"
echo "ordre servi par PostgreSQL 16 (deux seances de P7 le meme jour : creation puis identifiant, deterministe, KB-SEA-78 corrige, ADR 0117) : $(echo "$R" | cut -d'|' -f1,3 | lignes)"
verifier "P1 ordre exact des deux seances de P7 (meme creation : identifiant croissant)" "$(echo "$R" | cut -d'|' -f1,3 | lignes)" "$P7|$SALLE1;$P7|$SALLE2"
verifier "P1 ensemble (P6 dont la fiche est au centre B : absent)" "$(echo "$R" | cut -d'|' -f1,2,3,6,7 | sort | lignes)" \
  "$P7|2026-04-07|$SALLE1|Nom07|Pre;$P7|2026-04-07|$SALLE2|Nom07|Pre"
verifier "P1 prenom NULL interdit par le schema (branche du code inatteignable)" \
  "$(sql "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'patients' AND column_name = 'prenom'")" "NO"

echo "=== D1-D2 HistoriqueDesConstantesJdbc : total, egalite complete, creation nulle ==="
PAGE="SELECT s.id, s.date_seance, v.poids_avant_kg, v.poids_apres_kg, v.ta_avant, v.ta_apres, v.debit_sang_ml_min, v.ultrafiltration_ml, v.duree_minutes FROM seances s LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id WHERE s.center_id = '$A' AND s.patient_id = '%s' ORDER BY s.date_seance DESC, s.created_at DESC NULLS FIRST, s.id DESC LIMIT %s OFFSET %s"
verifier "D1 total P4" "$(sql "SELECT count(*) FROM seances WHERE center_id = '$A' AND patient_id = '$P4'")" "5"
verifier "D2 egalite complete : identifiant decroissant (octets non signes)" "$(sql "$(printf "$PAGE" "$P4" 20 0)" | cut -d'|' -f1 | lignes)" \
  "5e7d5000-0000-0000-0000-000000000001;$EGAL_B;$EGAL_A;$EGAL_C;5e7d5000-0000-0000-0000-000000000002"
verifier "D2 page 1 taille 2" "$(sql "$(printf "$PAGE" "$P4" 2 2)" | cut -d'|' -f1 | lignes)" "$EGAL_A;$EGAL_C"
verifier "D2 creation nulle : NULLS FIRST explicite, identique sur H2 et PostgreSQL (KB-SEA-79 corrige, ADR 0117)" \
  "$(sql "$(printf "$PAGE" "$P5" 20 0)" | cut -d'|' -f1 | lignes)" "$NULLE;$TARD;$FIXE"

echo "=== Section 63 : requetes de detection (SELECT seule), executees telles quelles sur le schema reel et le jeu fictif ==="
verifier "63.1 KB-SEA-79 creations nulles par centre" \
  "$(sql "SELECT center_id, count(*) FROM seances WHERE created_at IS NULL GROUP BY center_id" | lignes)" "$A|1"
verifier "63.2 KB-SEA-81 lien d'exclusion sur une seance non ABSENT" \
  "$(sql "SELECT center_id, statut, count(*) FROM seances WHERE exclusion_absence_id IS NOT NULL AND statut <> 'ABSENT' GROUP BY center_id, statut" | lignes)" \
  "$A|VALIDEE|1"
R="$(sql "SELECT center_id, statut, count(*) FROM seances WHERE upper(trim(statut)) IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND statut NOT IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, statut")"
verifier "63.3 KB-SEA-82 statuts realises hors casse canonique ou avec blancs (groupes)" "$(echo "$R" | grep -c .)" "6"
verifier "63.4 KB-SEA-80 seances realisees dont la fiche n'est pas dans le centre de la seance" \
  "$(sql "SELECT s.center_id, count(*) FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND p.id IS NULL GROUP BY s.center_id" | lignes)" \
  "$A|1"
R="$(sql "SELECT center_id, patient_id, date_seance, count(*) FROM seances WHERE statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1 LIMIT 100")"
verifier "63.5 KB-SEA-78 plusieurs seances realisees d'un patient le meme jour (groupes)" "$(echo "$R" | grep -c .)" "5"

echo "=== Contrats PostgreSQL : Seance (SeancesRealiseesEtConstantesPostgresContractIT) et Dossier medical (HistoriqueDesConstantesPostgresContractIT) ==="
export SEANCE_PG16_PASSWORD="$PW"
export DOSSIERMEDICAL_PG16_PASSWORD="$PW"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
cd "$RACINE/backend" || exit 3
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.seance.contrat.SeancesRealiseesEtConstantesPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.HistoriqueDesConstantesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="$URL" -Dseance.pg16.user=seance_test -Ddossiermedical.pg16.url="$URL" -Ddossiermedical.pg16.user=seance_test || RC=1

echo "=== Fin du rejeu : code $RC (0 = tout conforme) ==="
exit $RC
