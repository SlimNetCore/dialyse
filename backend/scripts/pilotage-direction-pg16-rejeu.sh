#!/bin/bash
# Rejeu du legacy DIRECTION (pilotage, slices PD1 a PD4) sur un PostgreSQL 16 REEL et JETABLE : cluster temporaire detruit en sortie.
# Chaine Flyway V1..Vn (psql), puis :
#   (1) les verifications du gel (2026-11-02, ADR 0075) : colonnes lues/ecrites en JDBC par le legacy (DirectionSnapshotService,
#       DirectionAlertHistoryService : pas d'entite JPA) contre information_schema, contrainte uq_direction_snapshot, index, aucun doublon Flyway,
#       aucune des tables paralleles du module gele contexts/pilotage-direction ;
#   (2) le jeu FICTIF minimal des goldens H2 de backend/platform/src/test/resources/characterization/pilotagedirection/ (memes cas) et les requetes
#       de production A L'IDENTIQUE (parametres remplaces par des litteraux), la ou la semantique H2/PostgreSQL peut differer :
#       D1  EffectifDesPatientsJdbc.parCentre (patient-couverture depuis LEC-PAT iii, ADR 0178 ; ex PatientsDesCentresJdbc + PredicatDEffectif, PD1, ADR 0102 ; repris mot pour mot de
#           DirectionDashboardQueryService.java:112-113 + EffectifSql.presentSur, retires) : effectif de la periode par centre
#       D2  EffectifDesPatientsJdbc.parCentrePayeur puis EffectifsParLaFiche.parCaisse (ex PatientsDesCentresJdbc.parCaisse ; repris de DirectionBreakdownQueryService.java:186-188, retire) : patients par caisse,
#           COALESCE(ca.code,'') + MAX(ca.nom) dans un GROUP BY
#       D3  SeancesRealiseesDesCentresJdbc.java:49-50, 68-70, 80-83 (lues par la Direction, SE7e) : par centre, par mois (EXTRACT), par caisse
#       D4  SyntheseFinanciereDesCentresJdbc.java:36-38, 42-43, 75-79 : factures, encaisse de toutes dates, CA par caisse (agence figee)
#       D5  TraitementDeLAnemieJdbc.java:34-40 : COUNT(DISTINCT CASE ...) et SUM(CASE ...)
#       D6  IndicateursDuStockJdbc.java:32-35, 45-49 : articles sous le seuil, valeur stock x PMP (PMP nul), lots perimes / proches aux bornes
#       D7  HistoriqueDesAlertesJdbc.recentes (module pilotage-direction, PD3, ADR 0104 ; repris mot pour mot de DirectionAlertHistoryService.java:70-72, retire) : ORDER BY COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC LIMIT n
#       D8  DirectionSnapshotService.java:62, 126-128 : liste ORDER BY mois DESC, INSERT avec CURRENT_TIMESTAMP, doublon refuse par uq_direction_snapshot
#       puis les detections SELECT de la section 68 de .ddd/migration/verifications-production.md.
#   La partie Java (masquage < 5, somme des totaux sur les bruts, tranches d'age, initiale du sexe, seuils d'alerte, diff temps reel, PDF) n'est pas
#   rejouee en SQL : elle est figee par les goldens H2.
# A lancer en arriere-plan avec un log : backend/scripts/pilotage-direction-pg16-rejeu.sh 55552
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# PD_PG16_PASSWORD (tiree au hasard ici si absente), jamais en argument. Code de sortie : 0 si tout est conforme.
set -uo pipefail
PORT="${1:-55552}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${PD_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
printf '%s' "$PW" | dd of="$TMP/pw" status=none
trap '"$PGBIN/pg_ctl" -s -D "$TMP/data" stop -m fast || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U pd_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C --no-instructions || exit 3
"$PGBIN/pg_ctl" -s -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start || exit 3
export PGPASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U pd_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | grep -oE '^V[0-9]+' | sort | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
RANG=0
for f in $(cd "$MIG" && ls V*.sql | sort -V); do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" || { echo "ECHEC de $f"; exit 3; }
  RANG=$((RANG + 1))
done
echo "Chaine Flyway appliquee ($RANG fichiers)"
RC=0
sql() { "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$1" | tr -d '\r'; }
lignes() { paste -sd ';'; }
verifier() { # libelle obtenu attendu
  if [ "$2" = "$3" ]; then echo "OK    $1 : $2"; else echo "ECART $1"; echo "   obtenu : $2"; echo "   attendu : $3"; RC=1; fi
}

echo "=== (1) Colonnes du legacy Direction (JDBC) contre information_schema ==="
for tc in \
  "direction_snapshot:id" "direction_snapshot:societe_id" "direction_snapshot:mois" "direction_snapshot:payload" "direction_snapshot:generated_at" \
  "direction_alert_history:id" "direction_alert_history:societe_id" "direction_alert_history:center_id" "direction_alert_history:centre_nom" \
  "direction_alert_history:code" "direction_alert_history:severity" "direction_alert_history:valeur" "direction_alert_history:first_seen_at" \
  "direction_alert_history:resolved_at" "societes:actif" "societes:pied_page" "centers:fuseau_horaire" "patients:date_evenement_etat" "patients:fer_enabled" \
  "factures:agence_id_snapshot" "articles:pmp_courant" "lots:quantite_restante"; do
  t="${tc%%:*}"; c="${tc##*:}"
  [ "$(sql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
    && echo "presente : $t.$c" || { echo "ABSENTE : $t.$c"; RC=1; }
done
verifier "contrainte uq_direction_snapshot (societe_id, mois)" "$(sql "SELECT count(*) FROM pg_constraint WHERE conname='uq_direction_snapshot'")" "1"
verifier "index idx_direction_alert_history_societe" "$(sql "SELECT count(*) FROM pg_indexes WHERE indexname='idx_direction_alert_history_societe'")" "1"
SRC="$RACINE/backend/platform/src/main/java/com/hemodialyse/backend/application/direction"
for col in $(grep -hoE "(INSERT INTO|FROM|UPDATE) direction_(snapshot|alert_history)" "$SRC"/*.java | grep -oE "direction_[a-z_]+" | sort -u); do
  verifier "table utilisee par le legacy presente : $col" "$(sql "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$col'")" "1"
done
for t in tableaux_de_bord instantanes_mensuels alertes_direction exports_synthese; do
  verifier "table parallele du module gele absente du schema Flyway : $t" "$(sql "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" "0"
done

echo "=== (2) Jeu de donnees fictif (memes cas que les goldens H2) ==="
SOC=99997800-0000-0000-0000-0000000000f1; AUTRE=99997800-0000-0000-0000-0000000000f2
A="99997800-0000-0000-0000-00000000000a"; B="99997800-0000-0000-0000-00000000000b"; X="99997800-0000-0000-0000-00000000000c"
D0=2026-01-01; D10=2026-01-11; D15=2026-01-16; D20=2026-01-21; D30=2026-01-31; D31=2026-02-01; D40=2026-02-10; D50=2026-02-20; D70=2026-03-12; D75=2026-03-17
D99=2026-04-10; D100=2026-04-11; D190=2026-07-10; D191=2026-07-11; D200=2026-07-20; DM5=2025-12-27; DM10=2025-12-22; DM20=2025-12-12; DM1=2025-12-31; DM101=2025-09-22
sql "INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES ('$SOC','ZT-PD','Clinique Pilotage & Fils',TRUE,CURRENT_TIMESTAMP), ('$AUTRE','ZT-PD-AUTRE','Autre',TRUE,CURRENT_TIMESTAMP)" || exit 3
sql "INSERT INTO centers (id, code, name, societe_id, actif) VALUES ('$A','ZT-PD-A','Centre A','$SOC',TRUE), ('$B','ZT-PD-B','Centre B','$SOC',TRUE), ('$X','ZT-PD-X','Centre Confidentiel','$AUTRE',TRUE)" || exit 3
CAISSE=d0000000-0000-0000-0000-0000000000c1; AGENCE=d0000000-0000-0000-0000-0000000000a1; PAYEUR=d0000000-0000-0000-0000-0000000000b1
CASNOS=d0000000-0000-0000-0000-0000000000c2; AGCASNOS=d0000000-0000-0000-0000-0000000000a2
sql "INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES ('$CAISSE','$A','CNAS','CNAS Nationale'), ('$CASNOS','$A','CASNOS','CASNOS Independants')" || exit 3
sql "INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES ('$AGENCE','$A','$CAISSE','AG-CNAS','Agence CNAS'), ('$AGCASNOS','$A','$CASNOS','AG-CASNOS','Agence CASNOS')" || exit 3
sql "INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES ('$PAYEUR','$A','$AGENCE','CP-CNAS','Payeur CNAS')" || exit 3
P() { printf 'd0000000-0000-0000-0000-0000000000%02d' "$1"; }
patient() { # id centre sexe naissance(ou NULL) sous_kt epo fer payeur(ou NULL) admission etat date_evenement(ou NULL)
  sql "INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, numero_assurance, type_patient, etat_patient, sous_kt, epo_enabled, fer_enabled, centre_payeur_id, date_evenement_etat) VALUES ('$1', '$2', 'PD-$1', 'Patient fictif', 'Test', '$3', $4, '$9', 'ASS-$1', 'NON_VACANCIER', '${10}', $5, $6, $7, $8, ${11})" || exit 3; }
ADM=2024-11-27
patient "$(P 1)" "$A" M "'1960-06-15'" TRUE TRUE FALSE "'$PAYEUR'" $ADM PERMANENT NULL
patient "$(P 2)" "$A" M "'1990-06-15'" TRUE TRUE FALSE "'$PAYEUR'" $ADM PERMANENT NULL
patient "$(P 3)" "$A" F "'2015-06-15'" FALSE TRUE TRUE "'$PAYEUR'" $ADM PERMANENT NULL
patient "$(P 4)" "$A" "f " NULL FALSE TRUE FALSE "'$PAYEUR'" $ADM PERMANENT NULL
patient "$(P 5)" "$A" "" "'2100-01-01'" FALSE TRUE FALSE "'$PAYEUR'" $ADM PERMANENT NULL
patient "$(P 6)" "$A" X "'1975-06-15'" FALSE TRUE FALSE NULL $ADM PERMANENT NULL
patient "$(P 7)" "$A" F "'1975-06-15'" FALSE FALSE FALSE NULL $ADM TRANSFERE "'$D50'"
patient "$(P 8)" "$A" M "'1975-06-15'" FALSE FALSE FALSE NULL $ADM DECEDE "'$DM10'"
patient "$(P 9)" "$A" M "'1975-06-15'" FALSE FALSE FALSE NULL $D200 PERMANENT NULL
for i in 11 12 13; do patient "$(P $i)" "$B" M "'1960-05-05'" FALSE FALSE FALSE NULL $ADM PERMANENT NULL; done
seance() { sql "INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) VALUES ('$1', '$2', '$3', '$4', '$5', '2026-01-01 10:00:00+00', FALSE)" || exit 3; }
seance d1000000-0000-0000-0000-000000000001 "$(P 1)" "$A" $D10 VALIDEE
seance d1000000-0000-0000-0000-000000000002 "$(P 2)" "$A" $D10 VALIDEE
seance d1000000-0000-0000-0000-000000000003 "$(P 3)" "$A" $D40 SIGNEE
seance d1000000-0000-0000-0000-000000000004 "$(P 6)" "$A" $D70 FACTUREE
seance d1000000-0000-0000-0000-000000000005 "$(P 6)" "$A" $D10 CREE
seance d1000000-0000-0000-0000-000000000006 "$(P 1)" "$A" $DM5 VALIDEE
seance d1000000-0000-0000-0000-000000000007 "$(P 11)" "$B" $D20 VALIDEE
seance d1000000-0000-0000-0000-000000000008 "$(P 1)" "$B" $D20 VALIDEE
facture() { # id centre patient date ht ttc agence(ou NULL)
  sql "INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, agence_id_snapshot) VALUES ('$1', '$2', '$3', 'PD-$1', '$4', '$4', '$4', 19.00, $5, $6 - $5, $6, $7)" || exit 3; }
F1=d2000000-0000-0000-0000-000000000001; F0=d2000000-0000-0000-0000-000000000005
facture $F1 "$A" "$(P 1)" $D10 1000.00 1190.00 "'$AGENCE'"
facture d2000000-0000-0000-0000-000000000002 "$A" "$(P 2)" $D70 500.00 595.00 "'$AGENCE'"
facture d2000000-0000-0000-0000-000000000003 "$A" "$(P 6)" $D70 200.00 238.00 NULL
facture d2000000-0000-0000-0000-000000000004 "$A" "$(P 6)" $D75 50.00 59.50 "'$AGCASNOS'"
facture $F0 "$A" "$(P 1)" $DM20 300.00 357.00 "'$AGENCE'"
sql "INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES
 ('d3000000-0000-0000-0000-000000000001', '$F1', '$A', 600.00, '$D15'), ('d3000000-0000-0000-0000-000000000002', '$F1', '$A', 200.00, '$D200'),
 ('d3000000-0000-0000-0000-000000000003', '$F0', '$A', 357.00, '$DM20')" || exit 3
adm() { sql "INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, administree, created_at) VALUES ('$1', '$2', '$A', '$3', '$4', $5, '2026-01-01 10:00:00+00')" || exit 3; }
for i in 1 2 3 4 5; do adm "d4000000-0000-0000-0000-00000000000$i" "$(P $i)" EPO $D30 TRUE; done
adm d4000000-0000-0000-0000-000000000006 "$(P 3)" FER_INJECTABLE $D30 TRUE
adm d4000000-0000-0000-0000-000000000007 "$(P 3)" FER_INJECTABLE $D31 TRUE
adm d4000000-0000-0000-0000-000000000008 "$(P 6)" EPO $D30 FALSE
ART=d5000000-0000-0000-0000-000000000001
sql "INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active) VALUES
 ('$ART', '$A', 'PD-SOUS', 'Article sous seuil', 'U', 10, 10, 2.50, TRUE, TRUE), ('d5000000-0000-0000-0000-000000000002', '$A', 'PD-DESSUS', 'Article au dessus', 'U', 11, 10, NULL, TRUE, TRUE),
 ('d5000000-0000-0000-0000-000000000003', '$A', 'PD-ZERO', 'Article seuil zero', 'U', 0, 0, NULL, TRUE, TRUE)" || exit 3
sql "INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante) VALUES
 ('d6000000-0000-0000-0000-000000000001', '$A', '$ART', 'L-PERIME-VEILLE', '$D99', 10, 5), ('d6000000-0000-0000-0000-000000000002', '$A', '$ART', 'L-PERIME-SANS-RESTE', '2026-04-01', 10, 0),
 ('d6000000-0000-0000-0000-000000000003', '$A', '$ART', 'L-PROCHE-JOUR', '$D100', 10, 5), ('d6000000-0000-0000-0000-000000000004', '$A', '$ART', 'L-PROCHE-90', '$D190', 10, 5),
 ('d6000000-0000-0000-0000-000000000005', '$A', '$ART', 'L-HORS-91', '$D191', 10, 5), ('d6000000-0000-0000-0000-000000000006', '$A', '$ART', 'L-SANS-DATE', NULL, 10, 5)" || exit 3
alerte() { sql "INSERT INTO direction_alert_history (id, societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at, resolved_at) VALUES ('$1', '$2', '$3', '$4', '$5', '$6', $7, '2026-01-01 $8:00:00+00', $9)" || exit 3; }
alerte d7000000-0000-0000-0000-000000000001 "$SOC" "$A" "Centre A" STOCK_SOUS_SEUIL CRITICAL 3 11 NULL
alerte d7000000-0000-0000-0000-000000000002 "$SOC" "$A" "Centre A" LOTS_PERIMES CRITICAL 2 12 "'2026-01-01 13:00:00+00'"
alerte d7000000-0000-0000-0000-000000000003 "$SOC" "$B" "Centre B" HB_HORS_CIBLE WARNING 40.0 15 NULL
alerte d7000000-0000-0000-0000-000000000004 "$SOC" "$A" "Centre A" KTV_CONFORMITE_BASSE WARNING 60.0 10 "'2026-01-01 20:00:00+00'"
alerte d7000000-0000-0000-0000-000000000005 "$SOC" "$B" "Centre B" OBSERVANCE_EN_RETARD WARNING NULL 14 NULL
alerte d7000000-0000-0000-0000-000000000006 "$AUTRE" "$X" "Centre Confidentiel" LOTS_PERIMES CRITICAL 9 15 NULL
echo "Jeu de donnees pose"

EFFECTIF="(p.date_admission IS NULL OR p.date_admission <= '$D100') AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ('DECEDE','GREFFE','GUERRI','TRANSFERE','OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') OR (p.etat_patient IN ('OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL') AND (p.date_evenement_etat IS NULL OR p.date_evenement_etat >= '$D0')) OR (p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= '$D0') OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat > '$D0'))"
echo "=== D1 DirectionDashboardQueryService:112-113 : effectif de la periode par centre (A : 7 presents, 2 sous KT ; B : 3) ==="
echo "    (fragment EffectifSql.presentSur reproduit mot pour mot : sorties DECEDE, GREFFE, GUERRI, TRANSFERE ; sejours OCCASIONNEL, VACANCIER_ETRANGER, VACANCIER_LOCAL, FinOccupation.java:28,33)"
verifier "D1 effectif par centre" "$(sql "SELECT p.center_id, COUNT(*) AS total, SUM(CASE WHEN p.sous_kt = TRUE THEN 1 ELSE 0 END) AS kt FROM patients p WHERE p.center_id IN ('$A','$B') AND $EFFECTIF GROUP BY p.center_id ORDER BY 1" | lignes)" "$A|7|2;$B|3|0"

echo "=== D2 DirectionBreakdownQueryService:186-188 : patients par caisse (COALESCE + MAX(nom)) ==="
verifier "D2 A CNAS 5, A sans caisse 2, B sans caisse 3" "$(sql "SELECT p.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n FROM patients p LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id LEFT JOIN agence ag ON ag.id = cp.agence_id LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE p.center_id IN ('$A','$B') AND $EFFECTIF GROUP BY p.center_id, COALESCE(ca.code, '') ORDER BY 1, 2" | lignes)" "$A|||2;$A|CNAS|CNAS Nationale|5;$B|||3"
echo "    (MAX(ca.nom) est NULL pour la ligne sans caisse : colonne vide, servie null au client comme en H2)"

echo "=== D3 SeancesRealiseesDesCentresJdbc : seances realisees par centre, par mois, par caisse ==="
verifier "D3 par centre (A 4 ; B 2, dont la seance d'une fiche du centre A)" "$(sql "SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '$D0' AND '$D100' AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id ORDER BY 1" | lignes)" "$A|4;$B|2"
verifier "D3 periode precedente ($DM101..$DM1) : 1" "$(sql "SELECT COUNT(*) FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '$DM101' AND '$DM1' AND statut IN ('VALIDEE','SIGNEE','FACTUREE')")" "1"
verifier "D3 par mois (EXTRACT : PostgreSQL sert des numeriques)" "$(sql "SELECT center_id, EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n FROM seances WHERE center_id IN ('$A','$B') AND date_seance BETWEEN '$D0' AND '$D100' AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id, y, m ORDER BY 1, 2, 3" | lignes)" "$A|2026|1|2;$A|2026|2|1;$A|2026|3|1;$B|2026|1|2"
verifier "D3 par caisse (lot L5b, KB-DIR-05 : la seance B d'une fiche A reste comptee sous la caisse vide ; centre payeur borne, KB-SEA-84)" "$(sql "SELECT s.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id LEFT JOIN agence ag ON ag.id = cp.agence_id LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE s.center_id IN ('$A','$B') AND s.date_seance BETWEEN '$D0' AND '$D100' AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, COALESCE(ca.code, '') ORDER BY 1, 2" | lignes)" "$A|||1;$A|CNAS|CNAS Nationale|3;$B|||2"

echo "=== D4 SyntheseFinanciereDesCentresJdbc : factures, encaisse de toutes dates, CA par caisse ==="
verifier "D4 factures de la periode : A 4, HT 1750, TTC 2082.50" "$(sql "SELECT center_id, COUNT(*) AS n, COALESCE(SUM(total_ht),0) AS ht, COALESCE(SUM(total_ttc),0) AS ttc FROM factures WHERE center_id IN ('$A','$B') AND date_facturation BETWEEN '$D0' AND '$D100' GROUP BY center_id ORDER BY 1" | lignes)" "$A|4|1750.00|2082.50"
verifier "D4 encaisse des factures de la periode = reglements de TOUTES dates (600 + 200 poste le $D200) = 800 (KB-FAC-62)" "$(sql "SELECT f.center_id, COALESCE(SUM(r.montant),0) AS encaisse FROM facture_reglements r INNER JOIN factures f ON f.id = r.facture_id AND f.center_id = r.center_id WHERE f.center_id IN ('$A','$B') AND f.date_facturation BETWEEN '$D0' AND '$D100' GROUP BY f.center_id" | lignes)" "$A|800.00"
verifier "D4 periode precedente : 1 facture 300 / 357, encaisse 357" "$(sql "SELECT (SELECT COUNT(*) || '|' || COALESCE(SUM(total_ht),0) || '|' || COALESCE(SUM(total_ttc),0) FROM factures WHERE center_id IN ('$A','$B') AND date_facturation BETWEEN '$DM101' AND '$DM1') || '|' || (SELECT COALESCE(SUM(r.montant),0) FROM facture_reglements r INNER JOIN factures f ON f.id = r.facture_id AND f.center_id = r.center_id WHERE f.center_id IN ('$A','$B') AND f.date_facturation BETWEEN '$DM101' AND '$DM1')")" "1|300.00|357.00|357.00"
verifier "D4 CA HT par caisse selon l'agence figee : CNAS 1500, sans caisse 200, CASNOS 50" "$(sql "SELECT f.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COALESCE(SUM(f.total_ht), 0) AS ht FROM factures f LEFT JOIN agence ag ON ag.id = f.agence_id_snapshot AND ag.center_id = f.center_id LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE f.center_id IN ('$A','$B') AND f.date_facturation BETWEEN '$D0' AND '$D100' GROUP BY f.center_id, COALESCE(ca.code, '') ORDER BY 1, 2" | lignes)" "$A|||200.00;$A|CASNOS|CASNOS Independants|50.00;$A|CNAS|CNAS Nationale|1500.00"

echo "=== D5 TraitementDeLAnemieJdbc:34-40 : patients EPO 5, fer 1, administrations 5 / 2, non administrees 1 ==="
verifier "D5 anemie du centre A" "$(sql "SELECT center_id, COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN patient_id END) AS pat_epo, COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN patient_id END) AS pat_fer, SUM(CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN 1 ELSE 0 END) AS adm_epo, SUM(CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN 1 ELSE 0 END) AS adm_fer, SUM(CASE WHEN administree = FALSE THEN 1 ELSE 0 END) AS non_adm FROM administrations_anemie WHERE center_id IN ('$A','$B') AND date_administration BETWEEN '$D0' AND '$D100' GROUP BY center_id" | lignes)" "$A|5|1|5|2|1"

echo "=== D6 IndicateursDuStockJdbc : articles (3 actifs, 1 sous le seuil, valeur 25.00 avec PMP nul = 0) ; lots a la date $D100 ==="
verifier "D6 articles" "$(sql "SELECT center_id, COUNT(*) AS actifs, SUM(CASE WHEN seuil_alerte > 0 AND stock_quantity <= seuil_alerte THEN 1 ELSE 0 END) AS sous, COALESCE(SUM(stock_quantity * COALESCE(pmp_courant, 0)), 0) AS valeur FROM articles WHERE center_id IN ('$A','$B') AND active = TRUE GROUP BY center_id" | lignes)" "$A|3|1|25.0000"
verifier "D6 lots : perimes 1 (veille), proches 2 (le jour meme et a 90 jours ; 91 jours, sans reste et sans date exclus)" "$(sql "SELECT center_id, SUM(CASE WHEN date_peremption < '$D100' THEN 1 ELSE 0 END) AS perimes, SUM(CASE WHEN date_peremption >= '$D100' AND date_peremption <= '$D190' THEN 1 ELSE 0 END) AS proches FROM lots WHERE center_id IN ('$A','$B') AND quantite_restante > 0 AND date_peremption IS NOT NULL GROUP BY center_id" | lignes)" "$A|1|2"

echo "=== D7 HistoriqueDesAlertesJdbc.recentes (ex DirectionAlertHistoryService:70-72) : ordre de l'historique et LIMIT ==="
verifier "D7 ordre : KTV (resolue T+10h), HB (T+5h), OBSERVANCE (T+4h), LOTS (resolue T+3h), STOCK (T+1h) ; autre societe absente" \
  "$(sql "SELECT code FROM direction_alert_history WHERE societe_id = '$SOC' ORDER BY COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC LIMIT 50" | lignes)" \
  "KTV_CONFORMITE_BASSE;HB_HORS_CIBLE;OBSERVANCE_EN_RETARD;LOTS_PERIMES;STOCK_SOUS_SEUIL"
verifier "D7 LIMIT 2" "$(sql "SELECT code FROM direction_alert_history WHERE societe_id = '$SOC' ORDER BY COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC LIMIT 2" | lignes)" "KTV_CONFORMITE_BASSE;HB_HORS_CIBLE"
verifier "D7 valeur numeric(14,2) : 3 relu 3.00, NULL conserve" "$(sql "SELECT code || '=' || COALESCE(valeur::text, 'NULL') FROM direction_alert_history WHERE societe_id = '$SOC' AND code IN ('STOCK_SOUS_SEUIL','OBSERVANCE_EN_RETARD') ORDER BY code" | lignes)" "OBSERVANCE_EN_RETARD=NULL;STOCK_SOUS_SEUIL=3.00"
verifier "D7 ouvertes par (centre, code) : la requete de HistoriqueDesAlertesJdbc.ouvertes ne lit que les lignes non resolues" "$(sql "SELECT code FROM direction_alert_history WHERE societe_id = '$SOC' AND resolved_at IS NULL ORDER BY code" | lignes)" "HB_HORS_CIBLE;OBSERVANCE_EN_RETARD;STOCK_SOUS_SEUIL"

echo "=== D8 DirectionSnapshotService : INSERT (CURRENT_TIMESTAMP), liste ORDER BY mois DESC, doublon refuse ==="
sql "INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES ('d8000000-0000-0000-0000-000000000001', '$SOC', '2024-09', '{\"overview\":{}}', CURRENT_TIMESTAMP)" || exit 3
sql "INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES ('d8000000-0000-0000-0000-000000000002', '$SOC', '2024-10', '{\"overview\":{},\"indicators\":{},\"breakdown\":{}}', CURRENT_TIMESTAMP)" || exit 3
verifier "D8 liste du plus recent au plus ancien" "$(sql "SELECT mois FROM direction_snapshot WHERE societe_id = '$SOC' ORDER BY mois DESC" | lignes)" "2024-10;2024-09"
verifier "D8 generated_at renseigne par CURRENT_TIMESTAMP (timestamptz)" "$(sql "SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = '$SOC' AND generated_at IS NOT NULL")" "2"
"${PSQL[@]}" -d hemo_test -qtAc "INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES ('d8000000-0000-0000-0000-000000000003', '$SOC', '2024-10', '{}', CURRENT_TIMESTAMP)" || echo "    (insertion du doublon refusee par PostgreSQL, message ci-dessus)"
verifier "D8 doublon (societe, mois) refuse par uq_direction_snapshot : toujours 2 lignes (le legacy traduit DuplicateKeyException en « deja cree »)" "$(sql "SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = '$SOC'")" "2"
verifier "D8 lecture du payload TEXT tel quel" "$(sql "SELECT payload FROM direction_snapshot WHERE societe_id = '$SOC' AND mois = '2024-09'")" '{"overview":{}}'

echo "=== Section 68 de verifications-production.md (SELECT seulement) : detections rejouees a l'identique ==="
verifier "68.1 KB-DIR-01 : centres dont l'effectif present est masque (1 a 4) et donc deductible des totaux (B : 3)" \
  "$(sql "SELECT p.center_id, COUNT(*) AS effectif FROM patients p WHERE $EFFECTIF GROUP BY p.center_id HAVING COUNT(*) BETWEEN 1 AND 4 ORDER BY 1" | lignes)" "$B|3"
verifier "68.2 KB-DIR-04 : alertes ouvertes en double pour un meme (societe, centre, code) : aucune attendue" \
  "$(sql "SELECT COUNT(*) FROM (SELECT societe_id, center_id, code FROM direction_alert_history WHERE resolved_at IS NULL GROUP BY societe_id, center_id, code HAVING COUNT(*) > 1) d")" "0"
verifier "68.3 KB-DIR-06 : instantanes sans repartition ('breakdown' absent du payload) ou au payload non JSON-objet" \
  "$(sql "SELECT societe_id, mois, CASE WHEN payload NOT LIKE '{%' THEN 'ILLISIBLE' ELSE 'SANS_BREAKDOWN' END FROM direction_snapshot WHERE payload NOT LIKE '{%' OR POSITION('\"breakdown\"' IN payload) = 0 ORDER BY 1, 2" | lignes)" "$SOC|2024-09|SANS_BREAKDOWN"
verifier "68.4 KB-DIR-05 : seances realisees dont la fiche du patient est dans un autre centre (comptees dans la vue d'ensemble, exclues des caisses)" \
  "$(sql "SELECT s.center_id AS centre_de_la_seance, p.center_id AS centre_de_la_fiche, COUNT(*) FROM seances s JOIN patients p ON p.id = s.patient_id WHERE p.center_id <> s.center_id AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, p.center_id" | lignes)" "$B|$A|1"
verifier "68.5 KB-DIR-06 : societes actives sans instantane du mois ecoule (la tache de 02:30 n'a pas tourne ou a echoue) : ici les deux (jeu fictif)" \
  "$(sql "SELECT COUNT(*) FROM societes s WHERE s.actif = TRUE AND NOT EXISTS (SELECT 1 FROM direction_snapshot d WHERE d.societe_id = s.id AND d.mois = to_char(date_trunc('month', current_date) - interval '1 month', 'YYYY-MM'))")" "2"
echo "=== Section 69 de verifications-production.md (PD1 et PD4, ADR 0102) : condition de mise en production de KB-DIR-01 (SELECT seulement) ==="
#   69.1 (m1 de la revue du lot A) : la cle "totaux" figure dans TOUT instantane ; la detection est donc discriminante : un centre MASQUE
#   (patients nul) ET des totaux de patients superieurs a la somme des centres publies (le total deduit un effectif masque).
DET691="WITH s AS (SELECT societe_id, mois, CASE WHEN payload LIKE '{%' THEN payload::jsonb END AS j FROM direction_snapshot), c AS (SELECT s.societe_id, s.mois, e->'patients' AS p FROM s, jsonb_array_elements(CASE WHEN jsonb_typeof(s.j#>'{overview,centres}') = 'array' THEN s.j#>'{overview,centres}' ELSE '[]'::jsonb END) e) SELECT s.societe_id, s.mois FROM s WHERE jsonb_typeof(s.j#>'{overview,totaux,patients}') = 'number' AND EXISTS (SELECT 1 FROM c WHERE c.societe_id = s.societe_id AND c.mois = s.mois AND c.p = 'null'::jsonb) AND (s.j#>>'{overview,totaux,patients}')::bigint > COALESCE((SELECT SUM((c.p#>>'{}')::bigint) FROM c WHERE c.societe_id = s.societe_id AND c.mois = s.mois AND jsonb_typeof(c.p) = 'number'), 0) ORDER BY 1, 2"
verifier "69.1 KB-DIR-01 : instantanes figes avec un centre masque ET des totaux superieurs a la somme des centres publies : aucun dans le jeu fictif" \
  "$(sql "$DET691" | lignes)" ""
verifier "69.2 nombre d'instantanes figes (les deux du jeu fictif)" "$(sql "SELECT COUNT(*) FROM direction_snapshot")" "2"
echo "    --- cas positif et cas temoin de 69.1 (instantanes synthetiques inseres puis supprimes, cluster jetable) ---"
sql "INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES ('d8000000-0000-0000-0000-000000000011', '$AUTRE', '2024-08', '{\"overview\":{\"centres\":[{\"patients\":null},{\"patients\":9}],\"totaux\":{\"patients\":12}}}', CURRENT_TIMESTAMP), ('d8000000-0000-0000-0000-000000000012', '$AUTRE', '2024-07', '{\"overview\":{\"centres\":[{\"patients\":9}],\"totaux\":{\"patients\":9}}}', CURRENT_TIMESTAMP), ('d8000000-0000-0000-0000-000000000013', '$AUTRE', '2024-06', '{\"overview\":{\"centres\":[{\"patients\":null}],\"totaux\":{\"patients\":null}}}', CURRENT_TIMESTAMP)" || exit 3
verifier "69.1 cas positif : seul l'instantane au centre masque et au total superieur est detecte (2024-08) ; le temoin sans masque (2024-07) et le total nul (2024-06) ne le sont pas" \
  "$(sql "$DET691" | lignes)" "$AUTRE|2024-08"
sql "DELETE FROM direction_snapshot WHERE id IN ('d8000000-0000-0000-0000-000000000011','d8000000-0000-0000-0000-000000000012','d8000000-0000-0000-0000-000000000013')" || exit 3
verifier "69.1/69.2 nettoyage : les deux instantanes du jeu fictif seulement" "$(sql "SELECT COUNT(*) FROM direction_snapshot")" "2"

echo "=== Contrat PostgreSQL des lectures du module Pilotage direction (PD1, ADR 0102) : societe et centres, effectif, profils, caisses, anemie ==="
export PD_PG16_PASSWORD="$PW"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
cd "$RACINE/backend" || exit 3
# -am : les modules amont (Organisation : AnnuaireDesSocietes) viennent du reacteur, pas d'un jar perime du depot Maven local
# (sans lui : ClassNotFoundException au rejeu du lot L5b, constatee deja au lot L3).
./mvnw -o -pl contexts/pilotage-direction -am test \
  -Dtest="com.hemodialyse.backend.contexts.pilotagedirection.contrat.LecturesDuPilotagePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpilotagedirection.pg16.url="$URL" -Dpilotagedirection.pg16.user=pd_test -Dpilotagedirection.pg16.password="$PW" || RC=1

echo "=== Fin du rejeu : code $RC (0 = tout conforme) ==="
exit $RC
