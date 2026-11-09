#!/bin/bash
# Rejeu de la slice DM9 (reliquat du legacy medical : lecteurs SQL de la Planification sur serologies_patient et abords_vasculaires,
# statistiques medicales, indicateurs de la Direction) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie.
# Chaine Flyway V1..Vn (psql), jeu de donnees FICTIF minimal (les memes cas que les goldens H2 de
# backend/platform/src/test/resources/characterization/dossiermedical/dm9/), puis les requetes de production A L'IDENTIQUE (parametres
# remplaces par des litteraux ; la partie Java des lecteurs, masquage d'anonymat et age pediatrique, n'est pas rejouee : elle est notee).
#   M1  RisquesInfectieuxJdbc.java:21-28 : patients a risque du centre = derniere serologie POSITIF d'un marqueur AG_HBS, AC_VHC, ARN_VHC,
#       VIH_AC, departagee par date_prelevement puis created_at ; NOT EXISTS borne au centre (goldens derniere_serologie, cloisonnement)
#   M2  ContraintesDesPatientsJdbc.java:34-37 : porteurs d'un catheter actif non termine au jour (golden pediatrie, cas CATHETER) ;
#       :39 colonnes lues des patients du centre (la regle des 18 ans est en Java : non rejouee)
#   M3  DirectionIndicatorsQueryService.java:152-156 : derniere hemoglobine non nulle de chaque patient sur la periode, par centre
#       (comptes bruts 5 et 3 : le masquage < 5 et la somme des totaux sont en Java, goldens un_centre_masque, deux_centres_masques)
#   M4  DirectionIndicatorsQueryService.java:171-175 : COUNT(DISTINCT patient) dont la derniere serologie AG_HBS est positive (5 et 1)
#   M5  DirectionIndicatorsQueryService.java:203-207 : bilans par statut (attente 5 et 4)
#   M6  PatientStatsQueryService.java:58, 62-65, 88-92 : moyenne d'hemoglobine et tendances / CSV ORDER BY date_prelevement ASC seul :
#       a date egale, l'ordre des deux lignes n'est PAS defini (observe, non impose ; KB-MED-109)
# Numerotation M1-M6 = celle de l'audit .ddd/safety-net/dossier-medical-dm9.md section 6.
# Le script rejoue ensuite les 5 detections SELECT de la section 66 de verifications-production.md (KB-MED-104, 106 a 109).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm9-pg16-rejeu.sh 55551
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# DM9_PG16_PASSWORD (tiree au hasard ici si absente), jamais en argument. Code de sortie : 0 si tout est conforme.
set -uo pipefail
PORT="${1:-55551}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${DM9_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
printf '%s' "$PW" | dd of="$TMP/pw" status=none
trap '"$PGBIN/pg_ctl" -s -D "$TMP/data" stop -m fast || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U dm9_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C --no-instructions || exit 3
"$PGBIN/pg_ctl" -s -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U dm9_test)
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
D10=2026-01-11; D20=2026-01-21; D100=2026-04-11
sql() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$1" | tr -d '\r'; }
lignes() { paste -sd ';'; }
patient() { # id centre naissance(ou NULL)
  sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, numero_assurance, type_patient, etat_patient) VALUES ('$1', '$2', 'DM9-$1', 'Patient fictif', 'Test', 'M', $3, '2024-11-27', 'ASS-$1', 'NON_VACANCIER', 'PERMANENT')" || exit 3; }
sero() { # id centre patient marqueur resultat date heure
  sql "INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '2026-01-01 $7:00:00+00', '2026-01-01 $7:00:00+00')" || exit 3; }
hb() { # id centre patient date hb
  sql "INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, created_at, updated_at) VALUES ('$1', '$3', '$2', '$4', $5, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3; }
bilan() { # id centre patient statut
  sql "INSERT INTO bilans_pre_greffe (id, patient_id, center_id, statut, created_at, updated_at) VALUES ('$1', '$3', '$2', '$4', '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3; }
# --- M1 : les huit patients du golden derniere_serologie (centre A) et les fiches du golden cloisonnement
P() { printf 'd9000000-0000-0000-0000-0000000000%02d' "$1"; }
for i in 1 2 3 4 5 6 7 8; do patient "$(P $i)" "$A" NULL; done
sero d9100000-0000-0000-0000-000000000001 "$A" "$(P 1)" AG_HBS POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000002 "$A" "$(P 1)" AG_HBS NEGATIF $D20 10
sero d9100000-0000-0000-0000-000000000003 "$A" "$(P 2)" AG_HBS NEGATIF $D10 10; sero d9100000-0000-0000-0000-000000000004 "$A" "$(P 2)" AG_HBS POSITIF $D20 10
sero d9100000-0000-0000-0000-000000000005 "$A" "$(P 3)" VIH_AC POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000006 "$A" "$(P 3)" VIH_AC NEGATIF $D10 11
sero d9100000-0000-0000-0000-000000000007 "$A" "$(P 4)" VIH_AC NEGATIF $D10 10; sero d9100000-0000-0000-0000-000000000008 "$A" "$(P 4)" VIH_AC POSITIF $D10 11
sero d9100000-0000-0000-0000-000000000009 "$A" "$(P 5)" AC_VHC POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000010 "$A" "$(P 5)" AC_VHC NEGATIF $D10 10
sero d9100000-0000-0000-0000-000000000011 "$A" "$(P 6)" AG_HBS POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000012 "$A" "$(P 6)" AG_HBS DOUTEUX $D20 10
sero d9100000-0000-0000-0000-000000000013 "$A" "$(P 7)" AG_HBS POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000014 "$A" "$(P 7)" AG_HBS EN_COURS $D20 10
sero d9100000-0000-0000-0000-000000000015 "$A" "$(P 8)" AG_HBS POSITIF $D10 10; sero d9100000-0000-0000-0000-000000000016 "$A" "$(P 8)" AC_VHC NEGATIF $D20 10
FA1=d9000000-0000-0000-0000-0000000000a1; FA2=d9000000-0000-0000-0000-0000000000a2; FA3=d9000000-0000-0000-0000-0000000000a3
patient $FA1 "$A" NULL; patient $FA2 "$A" NULL; patient $FA3 "$A" NULL
sero d9100000-0000-0000-0000-0000000000a1 "$B" $FA1 AG_HBS POSITIF $D10 10
sero d9100000-0000-0000-0000-0000000000a2 "$A" $FA2 VIH_AC NEGATIF $D20 10; sero d9100000-0000-0000-0000-0000000000a3 "$B" $FA2 VIH_AC POSITIF $D10 10
sero d9100000-0000-0000-0000-0000000000a4 "$A" $FA3 AC_VHC POSITIF $D10 10; sero d9100000-0000-0000-0000-0000000000a5 "$B" $FA3 AC_VHC NEGATIF $D20 10
# --- M2 : catheters (golden pediatrie et DM5)
KT=d9000000-0000-0000-0000-0000000000c1; KTFIN=d9000000-0000-0000-0000-0000000000c2; FAV=d9000000-0000-0000-0000-0000000000c3
patient $KT "$A" "'2021-04-11'"; patient $KTFIN "$A" NULL; patient $FAV "$A" NULL
sql "INSERT INTO abords_vasculaires (id, patient_id, center_id, type_abord, date_creation, date_fin, actif, created_at) VALUES
 ('d9200000-0000-0000-0000-000000000001', '$KT', '$A', 'KT_TUNNELISE', '$D10', NULL, TRUE, '2026-01-01 10:00:00+00'),
 ('d9200000-0000-0000-0000-000000000002', '$KTFIN', '$A', 'KT_AIGU', '$D10', '2026-04-10', TRUE, '2026-01-01 10:00:00+00'),
 ('d9200000-0000-0000-0000-000000000003', '$FAV', '$A', 'FAV', '$D10', NULL, TRUE, '2026-01-01 10:00:00+00')" || exit 3
# --- M3/M4/M5 : Direction, centre A 5 patients, centre B 3 (hb), 1 (AgHBs), 4 (attente)
for i in 1 2 3 4 5; do p="d9000000-0000-0000-0000-0000000000e$i"; patient "$p" "$A" NULL; hb "d9300000-0000-0000-0000-0000000000e$i" "$A" "$p" $D10 12
  sero "d9100000-0000-0000-0000-0000000000e$i" "$A" "$p" AG_HBS POSITIF $D10 10; bilan "d9400000-0000-0000-0000-0000000000e$i" "$A" "$p" INSCRIT_LISTE_ATTENTE; done
for i in 1 2 3; do p="d9000000-0000-0000-0000-0000000000f$i"; patient "$p" "$B" NULL; hb "d9300000-0000-0000-0000-0000000000f$i" "$B" "$p" $D10 12; done
sero d9100000-0000-0000-0000-0000000000f1 "$B" d9000000-0000-0000-0000-0000000000f1 AG_HBS POSITIF $D10 10
for i in 1 2 3 4; do p="d9000000-0000-0000-0000-0000000000f$i"; [ "$i" = 4 ] && patient "$p" "$B" NULL; bilan "d9400000-0000-0000-0000-0000000000f$i" "$B" "$p" INSCRIT_LISTE_ATTENTE; done
# --- M6 : statistiques, trois bilans dont deux le meme jour
ST=d9000000-0000-0000-0000-0000000000d1; patient $ST "$A" NULL
sql "INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, kt_v_mensuel, ferritine_ng_ml, phosphore_mg_dl, calcium_mg_dl, created_at, updated_at) VALUES
 ('d9300000-0000-0000-0000-0000000000d1', '$ST', '$A', '$D10', 10, 1.0, 300, 5.5, NULL, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00'),
 ('d9300000-0000-0000-0000-0000000000d2', '$ST', '$A', '$D10', 12, NULL, 500, NULL, 9, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00'),
 ('d9300000-0000-0000-0000-0000000000d3', '$ST', '$A', '$D20', 14, NULL, NULL, NULL, NULL, '2026-01-01 10:00:00+00', '2026-01-01 10:00:00+00')" || exit 3
echo "Jeu de donnees pose"

RC=0
verifier() { # libelle obtenu attendu
  if [ "$2" = "$3" ]; then echo "OK    $1 : $2"; else echo "ECART $1"; echo "   obtenu : $2"; echo "   attendu : $3"; RC=1; fi
}
RISQUE="SELECT DISTINCT s.patient_id FROM serologies_patient s WHERE s.center_id = '%s' AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND s.resultat = 'POSITIF' AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = s.center_id AND n.patient_id = s.patient_id AND n.marqueur = s.marqueur AND (n.date_prelevement > s.date_prelevement OR (n.date_prelevement = s.date_prelevement AND n.created_at > s.created_at))) ORDER BY 1"

echo "=== M1 RisquesInfectieuxJdbc:21-28 : patients a risque par centre ==="
verifier "M1 centre A (P2, P4, P5, P8, FA3 et les 5 porteurs de la Direction ; ni P1, P3, P6, P7, FA1, FA2)" \
  "$(sql "$(printf "$RISQUE" "$A")" | lignes)" \
  "$(P 2);$(P 4);$(P 5);$(P 8);$FA3;d9000000-0000-0000-0000-0000000000e1;d9000000-0000-0000-0000-0000000000e2;d9000000-0000-0000-0000-0000000000e3;d9000000-0000-0000-0000-0000000000e4;d9000000-0000-0000-0000-0000000000e5"
verifier "M1 centre B (FA1 et FA2 dont le positif est pose en B, le porteur unique F1 ; le negatif de A pour FA2 n'est pas vu)" \
  "$(sql "$(printf "$RISQUE" "$B")" | lignes)" "$FA1;$FA2;d9000000-0000-0000-0000-0000000000f1"

echo "=== M2 ContraintesDesPatientsJdbc:34-37 : catheters actifs non termines au $D100 ; :39 colonnes des patients ==="
verifier "M2 porteurs d'un catheter (KT tunnelise sans fin ; KT aigu termine la veille exclu ; FAV exclue)" \
  "$(sql "SELECT DISTINCT patient_id FROM abords_vasculaires WHERE center_id = '$A' AND actif = TRUE AND type_abord IN ('KT_TUNNELISE', 'KT_AIGU') AND (date_fin IS NULL OR date_fin >= '$D100') ORDER BY 1" | lignes)" "$KT"
verifier "M2 catheter termine le jour meme inclus (fin >= jour, au 2026-04-10)" \
  "$(sql "SELECT DISTINCT patient_id FROM abords_vasculaires WHERE center_id = '$A' AND actif = TRUE AND type_abord IN ('KT_TUNNELISE', 'KT_AIGU') AND (date_fin IS NULL OR date_fin >= '2026-04-10') ORDER BY 1" | lignes)" "$KT;$KTFIN"
verifier "M2 colonnes lues des patients (date de naissance et transporteurs ; la regle des 18 ans est en Java)" \
  "$(sql "SELECT id, date_naissance, transporteur_aller_id, transporteur_retour_id FROM patients WHERE center_id = '$A' AND id = '$KT'")" "$KT|2021-04-11||"

echo "=== M3 DirectionIndicatorsQueryService:152-156 : derniere hemoglobine de chaque patient sur la periode, par centre (comptes bruts) ==="
LATEST="SELECT r.center_id, r.hb_g_dl AS v FROM resultats_analyses r WHERE r.center_id IN ('$A','$B') AND r.hb_g_dl IS NOT NULL AND r.date_prelevement BETWEEN '2026-01-01' AND '$D100' AND r.date_prelevement = (SELECT MAX(r2.date_prelevement) FROM resultats_analyses r2 WHERE r2.patient_id = r.patient_id AND r2.center_id = r.center_id AND r2.hb_g_dl IS NOT NULL AND r2.date_prelevement BETWEEN '2026-01-01' AND '$D100')"
verifier "M3 lignes par centre (A : les 5 patients de la Direction + le patient des statistiques, une ligne a sa derniere date D+20 ; B : 3)" \
  "$(sql "SELECT center_id, count(*) FROM ($LATEST) t GROUP BY center_id ORDER BY 1" | lignes)" "$A|6;$B|3"
verifier "M3 le patient des statistiques ne compte qu'une fois (derniere date D+20, hb 14)" \
  "$(sql "SELECT count(*) FROM ($LATEST) t WHERE v = 14")" "1"

echo "=== M4 DirectionIndicatorsQueryService:171-175 : COUNT(DISTINCT) des porteurs AgHBs positifs au dernier resultat ==="
POS="SELECT s.center_id, COUNT(DISTINCT s.patient_id) AS n FROM serologies_patient s WHERE s.center_id IN ('$A','$B') AND s.marqueur = 'AG_HBS' AND s.resultat = 'POSITIF' AND s.date_prelevement = (SELECT MAX(s2.date_prelevement) FROM serologies_patient s2 WHERE s2.patient_id = s.patient_id AND s2.center_id = s.center_id AND s2.marqueur = s.marqueur AND s2.date_prelevement <= '$D100') GROUP BY s.center_id ORDER BY 1"
verifier "M4 A = 5 + P2 (positif recent) + P8 = 7 ; B = F1 + FA1 = 2 (P1, P6, P7 : dernier resultat non positif)" "$(sql "$POS" | lignes)" "$A|7;$B|2"

echo "=== M5 DirectionIndicatorsQueryService:203-207 : bilans par statut ==="
verifier "M5 inscrits en liste d'attente : A 5, B 4" \
  "$(sql "SELECT center_id, SUM(CASE WHEN statut = 'INSCRIT_LISTE_ATTENTE' THEN 1 ELSE 0 END) AS attente, SUM(CASE WHEN statut = 'BILAN_EN_COURS' THEN 1 ELSE 0 END) AS bilan, SUM(CASE WHEN date_greffe BETWEEN '2026-01-01' AND '$D100' THEN 1 ELSE 0 END) AS greffes FROM bilans_pre_greffe WHERE center_id IN ('$A','$B') GROUP BY center_id ORDER BY 1" | lignes)" "$A|5|0|0;$B|4|0|0"

echo "=== M6 PatientStatsQueryService:58, 62-65, 88-92 : moyenne et tendances a date egale ==="
verifier "M6 moyenne d'hemoglobine (10, 12, 14) = 12" "$(sql "SELECT AVG(ra.hb_g_dl) = 12 FROM resultats_analyses ra WHERE ra.center_id = '$A' AND ra.patient_id = '$ST'")" "t"
echo "M6 moyenne telle que servie par PostgreSQL : $(sql "SELECT AVG(ra.hb_g_dl) FROM resultats_analyses ra WHERE ra.center_id = '$A' AND ra.patient_id = '$ST'") (H2 : 12.0 ; Java : Double 12.0)"
TENDANCE="$(sql "SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml FROM resultats_analyses ra WHERE ra.center_id = '$A' AND ra.patient_id = '$ST' ORDER BY ra.date_prelevement ASC" | lignes)"
echo "M6 tendance servie : $TENDANCE"
verifier "M6 trois lignes, la troisieme est celle du D+20" "$(echo "$TENDANCE" | tr ';' '\n' | wc -l | tr -d ' ') $(echo "$TENDANCE" | tr ';' '\n' | tail -1)" "3 $D20|14.00||"
verifier "M6 les deux lignes du D+10 sont servies (ordre entre elles NON defini, KB-MED-109 : observe, non impose)" \
  "$(echo "$TENDANCE" | tr ';' '\n' | head -2 | cut -d'|' -f2 | sort | lignes)" "10.00;12.00"
CSV="$(sql "SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml, ra.phosphore_mg_dl, ra.calcium_mg_dl FROM resultats_analyses ra WHERE ra.center_id = '$A' AND ra.patient_id = '$ST' ORDER BY ra.date_prelevement ASC" | lignes)"
echo "M6 lignes CSV servies : $CSV"

echo "=== Section 66 de verifications-production.md (SELECT seulement) : detections rejouees a l'identique ==="
verifier "66.1 KB-MED-106 : patients dont le dernier resultat d'un marqueur a risque est DOUTEUX ou EN_COURS apres un POSITIF (P6, P7)" \
  "$(sql "SELECT d.center_id, d.patient_id, d.marqueur, d.resultat FROM serologies_patient d WHERE d.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND d.resultat IN ('DOUTEUX', 'EN_COURS') AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = d.center_id AND n.patient_id = d.patient_id AND n.marqueur = d.marqueur AND (n.date_prelevement > d.date_prelevement OR (n.date_prelevement = d.date_prelevement AND n.created_at > d.created_at))) AND EXISTS (SELECT 1 FROM serologies_patient p WHERE p.center_id = d.center_id AND p.patient_id = d.patient_id AND p.marqueur = d.marqueur AND p.resultat = 'POSITIF' AND p.date_prelevement < d.date_prelevement) ORDER BY 1, 2" | lignes)" \
  "$A|$(P 6)|AG_HBS|DOUTEUX;$A|$(P 7)|AG_HBS|EN_COURS"
verifier "66.2 KB-MED-107 : deux serologies du meme marqueur, meme patient, meme centre, meme date et meme horodatage de saisie, resultats differents (P5)" \
  "$(sql "SELECT a.center_id, a.patient_id, a.marqueur, a.date_prelevement, count(*) FROM serologies_patient a JOIN serologies_patient b ON b.center_id = a.center_id AND b.patient_id = a.patient_id AND b.marqueur = a.marqueur AND b.date_prelevement = a.date_prelevement AND b.created_at = a.created_at AND b.id <> a.id AND b.resultat <> a.resultat GROUP BY a.center_id, a.patient_id, a.marqueur, a.date_prelevement" | lignes)" \
  "$A|$(P 5)|AC_VHC|$D10|2"
verifier "66.3 KB-MED-108 : serologies a risque positives posees dans un autre centre que la fiche du patient (FA1, FA2 en B)" \
  "$(sql "SELECT s.center_id AS centre_de_la_serologie, p.center_id AS centre_de_la_fiche, count(*) FROM serologies_patient s JOIN patients p ON p.id = s.patient_id WHERE s.center_id <> p.center_id AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND s.resultat = 'POSITIF' GROUP BY s.center_id, p.center_id" | lignes)" \
  "$B|$A|2"
verifier "66.4 KB-MED-109 : plusieurs bilans du meme patient au centre le meme jour (le patient des statistiques, D+10)" \
  "$(sql "SELECT center_id, patient_id, date_prelevement, count(*) FROM resultats_analyses GROUP BY center_id, patient_id, date_prelevement HAVING count(*) > 1 LIMIT 100" | lignes)" \
  "$A|$ST|$D10|2"
verifier "66.5 KB-MED-104 : centres dont l'effectif evalue en hemoglobine sur la periode est masque (1 a 4 patients) et donc deductible des totaux (centre B : 3)" \
  "$(sql "SELECT r.center_id, count(DISTINCT r.patient_id) AS evalues FROM resultats_analyses r WHERE r.hb_g_dl IS NOT NULL AND r.date_prelevement BETWEEN date_trunc('year', current_date)::date AND current_date GROUP BY r.center_id HAVING count(DISTINCT r.patient_id) BETWEEN 1 AND 4" | lignes)" \
  "$B|3"

echo "=== Fin du rejeu : code $RC (0 = tout conforme) ==="
exit $RC
