#!/bin/bash
# Rejeu de la sous-slice SE7c2 (ecritures et lectures de la table des seances par la Facturation) sur un PostgreSQL 16 REEL et
# JETABLE : cluster temporaire detruit en sortie. Chaine Flyway V1..Vn (psql), puis les requetes de controle (SELECT seule, dont
# les requetes 1, 3 et 4 du paragraphe 62 de .ddd/migration/verifications-production.md, a l'identique), puis :
#  - module Seance : la course de deux emissions simultanees sur les memes seances (LiaisonConcurrentePostgresContractIT, ecrit dans
#    la base jetable uniquement) et les ports publies de la Seance pour la Facturation (FacturationDesSeancesPostgresContractIT) ;
#  - module Facturation : le forfait applique (ForfaitAppliquePostgresContractIT : ForfaitsDuCentreJdbc et prises en charge reelles,
#    sans SQL sur seances).
# A lancer en arriere-plan avec un log : backend/scripts/seance-se7c2-pg16-rejeu.sh 55545
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Mot de passe du cluster jetable : variable d'environnement
# SEANCE_PG16_PASSWORD (tiree au hasard ici si absente), recopiee dans SEANCE_PG16_PASSWORD et FACTURATION_PG16_PASSWORD pour les
# deux modules ; jamais en -D sur la ligne de commande Maven (les tests la lisent dans l'environnement). Code de sortie : 0 si tout
# est vert.
set -uo pipefail
PORT="${1:-55545}"
[ "$PORT" != "5432" ] || { echo "Port 5432 interdit"; exit 2; }
PGBIN="${PGBIN:-/c/Program Files/PostgreSQL/16/bin}"
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
PW="${SEANCE_PG16_PASSWORD:-pw$RANDOM$RANDOM$RANDOM$RANDOM}"
echo -n "$PW" > "$TMP/pw"
trap '"$PGBIN/pg_ctl" -D "$TMP/data" stop -m fast >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT
"$PGBIN/initdb" -D "$TMP/data" -U facturation_test --pwfile="$TMP/pw" -A scram-sha-256 -E UTF8 --locale=C >/dev/null 2>&1 || exit 3
"$PGBIN/pg_ctl" -D "$TMP/data" -o "-p $PORT -c listen_addresses=127.0.0.1" -l "$TMP/pg.log" -w -t 60 start >/dev/null || exit 3
export PGPASSWORD="$PW"
export FACTURATION_PG16_PASSWORD="$PW"
PSQL=("$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U facturation_test)
"${PSQL[@]}" -d postgres -qc "create database hemo_test" || exit 3
MIG="$RACINE/backend/platform/src/main/resources/db/migration"
echo "=== Versions Flyway : aucun doublon ==="
DOUBLONS="$(cd "$MIG" && ls V*.sql | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | uniq -d)"
[ -z "$DOUBLONS" ] && echo "aucun doublon" || { echo "DOUBLONS : $DOUBLONS"; exit 1; }
for f in $(cd "$MIG" && ls V*.sql | sort -V); do "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -q -f "$MIG/$f" >/dev/null || { echo "ECHEC de $f"; exit 3; }; done
echo "Chaine Flyway appliquee"
RC=0
echo "=== Contraintes reelles de seances.facture_id (aucune cle etrangere vers factures attendue) ==="
"${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name WHERE tc.table_name = 'seances' AND tc.constraint_type = 'FOREIGN KEY' AND k.column_name = 'facture_id'" | tr -d '\r'
echo "=== SE7c2 : requetes de controle (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT s.center_id, count(*) AS seances_facturees_sans_facture FROM seances s WHERE s.statut = 'FACTUREE' AND s.facture_id IS NULL GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_avec_facture_sans_statut_facturee FROM seances s WHERE s.facture_id IS NOT NULL AND s.statut <> 'FACTUREE' GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_liees_a_une_facture_inexistante FROM seances s WHERE s.facture_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM factures f WHERE f.id = s.facture_id) GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS seances_liees_a_la_facture_d_un_autre_centre FROM seances s INNER JOIN factures f ON f.id = s.facture_id WHERE f.center_id <> s.center_id GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS surcharges_avec_id_sans_code FROM seances s WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS NULL GROUP BY s.center_id" \
  "SELECT s.center_id, count(*) AS memoires_d_exclusion_blanches FROM seances s WHERE s.statut_avant_exclusion IS NOT NULL AND trim(s.statut_avant_exclusion) = '' GROUP BY s.center_id" \
  "SELECT s.center_id, s.statut, count(*) AS statuts_hors_casse_canonique FROM seances s WHERE s.statut <> upper(s.statut) GROUP BY s.center_id, s.statut"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done
echo "=== Paragraphe 62 (verifications-production.md) : requetes 1, 3 et 4 a l'identique, SELECT seule, 0 ligne attendue sur le schema neuf ==="
for q in \
  "SELECT s.center_id, s.id, s.forfait_override_id, s.forfait_override_code FROM seances s LEFT JOIN forfait f ON f.id = s.forfait_override_id WHERE s.forfait_override_id IS NOT NULL AND s.forfait_override_code IS DISTINCT FROM f.code LIMIT 100" \
  "SELECT s.center_id, s.id, s.facture_id, s.exclusion_absence_id FROM seances s WHERE s.statut = 'ABSENT' AND s.facture_id IS NOT NULL LIMIT 100" \
  "SELECT a.center_id, a.patient_id, a.id AS facture_a, b.id AS facture_b FROM factures a INNER JOIN factures b ON b.center_id = a.center_id AND b.patient_id = a.patient_id AND b.id > a.id AND b.period_start <= a.period_end AND a.period_start <= b.period_end LIMIT 100"; do
  LIGNES="$("${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "SELECT count(*) FROM ($q) r")" || { echo "ECHEC de la requete : $q"; RC=1; continue; }
  LIGNES="$(echo "$LIGNES" | tr -d '\r')"
  [ "$LIGNES" = "0" ] && echo "requete executable, 0 ligne" || { echo "LIGNES INATTENDUES ($LIGNES) : $q"; RC=1; }
done

cd "$RACINE/backend"
URL="jdbc:postgresql://127.0.0.1:$PORT/hemo_test"
echo "=== Contrat PostgreSQL (module Seance, ADR 0097) : course de deux emissions et ports publies de la Seance pour la Facturation ==="
# Depuis SE7c2 lot B : les ecritures vivent dans contexts/seance (ports publies) ; la variable d'environnement est SEANCE_PG16_PASSWORD.
export SEANCE_PG16_PASSWORD="$PW"
./mvnw -o -pl contexts/seance -am test \
  -Dtest="LiaisonConcurrentePostgresContractIT,FacturationDesSeancesPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dseance.pg16.url="$URL" -Dseance.pg16.user=facturation_test || RC=1
echo "=== Contrat PostgreSQL (module Facturation, revue SE7c2 m1) : forfait applique, sans SQL sur seances ==="
export FACTURATION_PG16_PASSWORD="$PW"
./mvnw -o -pl contexts/facturation -am test \
  -Dtest="ForfaitAppliquePostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfacturation.pg16.url="$URL" -Dfacturation.pg16.user=facturation_test || RC=1
echo "=== Fin du rejeu : code $RC (0 = tout vert) ==="
exit $RC
