#!/bin/bash
# Rejeu de la slice DM7 (bilan pre-greffe, etapes, donneurs vivants) du Dossier medical sur un PostgreSQL 16 REEL et JETABLE : cluster
# temporaire detruit en sortie. Chaine Flyway V1..Vn (psql) ; colonnes lues et ecrites par les adapters JDBC BilansPreGreffeJdbc,
# EtapesDeBilanJdbc et DonneursVivantsJdbc contre information_schema (et ABSENCE de contrainte d'unicite sur les quatre tables : un
# doublon de bilan reste possible en base et est lu sans erreur) ; aucune table parallele ; requetes de parite de
# verifications-production.md section 51 (SELECT seule) executables sur le schema reel ; puis les contrats PostgreSQL des trois ports
# (meme jeu de tests que les doubles en memoire et que H2, plus les tests a deux connexions : verrou de ligne et verrous consultatifs
# d'ouverture du bilan et de generation des etapes -> 409).
# A lancer en arriere-plan avec un log : backend/scripts/dossier-medical-dm7-pg16-rejeu.sh 55486 > rejeu-dm7.log 2>&1 &
# JAMAIS le service PostgreSQL de la machine : le port 5432 est refuse. Code de sortie : 0 si tout est vert, 1 sinon.
set -uo pipefail
PORT="${1:-55486}"
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

echo "=== Colonnes lues et ecrites par les adapters JDBC de DM7 (schema reel) ==="
verifier_colonnes() { # $1 = table, reste = colonnes
  t="$1"; shift
  for c in "$@"; do
    [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t' AND column_name='$c'")" = "1" ] \
      || { echo "COLONNE ABSENTE : $t.$c"; RC=1; }
  done
  echo "ok : $t ($# colonnes)"
  [ "$t" != "patients" ] || return 0   # lue seulement (existence), hors tables de la slice
  # Aucune colonne ajoutee : exactement les colonnes lues par l'adapter.
  [ "$(scalaire "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='$t'")" = "$#" ] \
    && echo "ok : $t a exactement $# colonnes" || { echo "INATTENDU : colonnes de $t"; RC=1; }
  # Aucune contrainte d'unicite autre que la cle primaire.
  [ "$(scalaire "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='$t' AND indexdef ILIKE '%UNIQUE%' AND indexname <> '${t}_pkey'")" = "0" ] \
    && echo "ok : $t sans contrainte d'unicite supplementaire" || { echo "INATTENDU : index unique supplementaire sur $t"; RC=1; }
}
verifier_colonnes bilans_pre_greffe id center_id patient_id statut date_debut_bilan date_inscription_liste_attente date_greffe groupe_sanguin_confirme typage_hla pra_classe_i pra_classe_ii contre_indications conclusion_nephrologue created_at updated_at
verifier_colonnes decisions_rcp id bilan_id date_reunion avis compte_rendu prochaine_date_revue
verifier_colonnes etapes_bilan_pre_greffe id patient_id center_id categorie libelle statut date_realisation resultat date_expiration demande_examen_id serologie_id created_at updated_at
verifier_colonnes donneurs_vivants id patient_id center_id nom prenom date_naissance lien_parente telephone groupe_sanguin typage_hla statut_bilan crossmatch_resultat date_crossmatch bilan_realise contre_indications decision_finale date_decision created_at updated_at
verifier_colonnes patients id center_id

echo "=== Aucune table parallele (les schemas geles du module n'existent pas dans la chaine Flyway) ==="
for t in bilan_pre_greffe bilans etapes_bilan donneurs donneur_vivant decision_rcp; do
  [ "$(scalaire "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")" = "0" ] \
    && echo "absente : $t" || { echo "INATTENDU : table parallele $t"; RC=1; }
done

echo "=== DM7 : requetes de parite de verifications-production.md section 51 (SELECT seule), executables sur le schema reel ==="
for q in \
  "SELECT 'bilans' AS table_, center_id, count(*) FROM bilans_pre_greffe GROUP BY center_id UNION ALL SELECT 'decisions_rcp', b.center_id, count(*) FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id GROUP BY b.center_id UNION ALL SELECT 'etapes', center_id, count(*) FROM etapes_bilan_pre_greffe GROUP BY center_id UNION ALL SELECT 'donneurs', center_id, count(*) FROM donneurs_vivants GROUP BY center_id" \
  "SELECT center_id, patient_id, count(*) AS bilans, min(created_at) AS le_plus_ancien FROM bilans_pre_greffe GROUP BY center_id, patient_id HAVING count(*) > 1" \
  "SELECT 'bilans' AS table_, t.center_id, count(*) AS patient_absent FROM bilans_pre_greffe t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id UNION ALL SELECT 'etapes', t.center_id, count(*) FROM etapes_bilan_pre_greffe t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id UNION ALL SELECT 'donneurs', t.center_id, count(*) FROM donneurs_vivants t LEFT JOIN patients p ON p.id = t.patient_id AND p.center_id = t.center_id WHERE p.id IS NULL GROUP BY t.center_id" \
  "SELECT center_id, patient_id, categorie, libelle, count(*) AS exemplaires FROM etapes_bilan_pre_greffe GROUP BY center_id, patient_id, categorie, libelle HAVING count(*) > 1" \
  "SELECT 'bilans' AS table_, center_id, id FROM bilans_pre_greffe WHERE statut NOT IN ('NON_DEBUTE','BILAN_EN_COURS','ELIGIBLE','INSCRIT_LISTE_ATTENTE','CONTRE_INDICATION_TEMPORAIRE','CONTRE_INDICATION_DEFINITIVE','GREFFE_REALISEE') UNION ALL SELECT 'decisions_rcp', b.center_id, d.id FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id WHERE d.avis NOT IN ('FAVORABLE','DEFAVORABLE','AJOURNE') UNION ALL SELECT 'etapes', center_id, id FROM etapes_bilan_pre_greffe WHERE categorie NOT IN ('CARDIOLOGIQUE','PNEUMOLOGIQUE','DENTAIRE','ORL','GYNECOLOGIQUE','UROLOGIQUE','DIGESTIF','ONCOLOGIQUE','PSYCHIATRIQUE','VIROLOGIQUE','IMMUNOLOGIQUE','VACCINATION','NUTRITIONNEL','AUTRE','DERMATOLOGIQUE') OR statut NOT IN ('A_FAIRE','PLANIFIE','FAIT','NON_APPLICABLE') UNION ALL SELECT 'donneurs', center_id, id FROM donneurs_vivants WHERE lien_parente NOT IN ('CONJOINT','PARENT','ENFANT','FRERE_SOEUR','AUTRE_FAMILLE','NON_APPARENTE') OR statut_bilan NOT IN ('CANDIDAT','BILAN_EN_COURS','COMPATIBLE','INCOMPATIBLE','EXCLU','RETENU') OR crossmatch_resultat NOT IN ('NON_FAIT','NEGATIF','POSITIF')" \
  "SELECT 'etape_faite_sans_date' AS controle, center_id, count(*) FROM etapes_bilan_pre_greffe WHERE statut = 'FAIT' AND date_realisation IS NULL GROUP BY center_id UNION ALL SELECT 'rcp_dans_le_futur', b.center_id, count(*) FROM decisions_rcp d JOIN bilans_pre_greffe b ON b.id = d.bilan_id WHERE d.date_reunion > current_date GROUP BY b.center_id" \
  "SELECT statut, count(*) FROM bilans_pre_greffe GROUP BY statut ORDER BY 1"; do
  "${PSQL[@]}" -d hemo_test -v ON_ERROR_STOP=1 -qtAc "$q" >/dev/null && echo "requete executable" || { echo "ECHEC de la requete : $q"; RC=1; }
done

cd "$RACINE/backend"
echo "=== Contrats PostgreSQL des ports BilansPreGreffe, EtapesDeBilan et DonneursVivants ==="
./mvnw -o -pl contexts/dossier-medical -am test \
  -Dtest="com.hemodialyse.backend.contexts.dossiermedical.contrat.BilansPreGreffePostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.EtapesDeBilanPostgresContractIT,com.hemodialyse.backend.contexts.dossiermedical.contrat.DonneursVivantsPostgresContractIT" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddossiermedical.pg16.url="jdbc:postgresql://127.0.0.1:$PORT/hemo_test" -Ddossiermedical.pg16.user=dossier_medical_test -Ddossiermedical.pg16.password="$PW" || RC=1
echo "=== Code de sortie : $RC ==="
exit $RC
