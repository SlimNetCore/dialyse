#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le CODE NOUVEAU de PC5 (langage publie patientcouverture.v1) :
# les trois adapters FichesPublieesJdbc, CouvertureDesPatientsJdbc et CompteursDeCouvertureJdbc, testes par le contrat du langage publie
# sur H2 (LecturesPublieesJdbcContratTest, memes 14 tests que le double en memoire et que PostgreSQL 16). Le domaine et l'application
# du module n'ont pas change en PC5 (voir pit-pc4-module.sh).
# Plus lent que PIT du domaine (contexte @JdbcTest H2) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-pc5-module.sh > pit-pc5-module.log 2>&1 &
# Rapport : backend/contexts/patient-couverture/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.patientcouverture
cd "$RACINE"
./mvnw -o -pl contexts/patient-couverture org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.infrastructure.persistence.FichesPublieesJdbc,$R.infrastructure.persistence.CouvertureDesPatientsJdbc,$R.infrastructure.persistence.CompteursDeCouvertureJdbc" \
  -DtargetTests="$R.contrat.LecturesPublieesJdbcContratTest" \
  -DtimestampedReports=false
