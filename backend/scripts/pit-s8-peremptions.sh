#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module) sur le code nouveau de la slice S8 du contexte stock : la regle des peremptions
# (domain.peremptions) et le cas d'usage ControlerLesPeremptions (application.peremptions), testes par leurs tests unitaires et
# le cas d'usage avec doubles en memoire. Rapide (aucun contexte Spring).
# Usage (Git Bash) : bash backend/scripts/pit-s8-peremptions.sh > pit-s8.log 2>&1
# Rapport : backend/contexts/stock/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.stock
cd "$RACINE"
./mvnw -o -pl contexts/stock org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.peremptions.*,$R.application.peremptions.*" \
  -DtargetTests="$R.domain.peremptions.*,$R.application.peremptions.*" \
  -DtimestampedReports=false
