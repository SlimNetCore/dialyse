#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et l'application des reglements du
# contexte facturation (slice F4, refonte sur place) : domain.reglement.* et application.reglement.*, testes par leurs tests
# unitaires (domaine, cas d'usage avec doubles en memoire). Les anciennes classes du legacy (bc-reglement, platform) sont retirees.
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-f4-reglements.sh > pit-f4.log 2>&1 &
# Rapport : backend/contexts/facturation/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.facturation
cd "$RACINE"
./mvnw -o -pl contexts/facturation org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.reglement.*,$R.application.reglement.*" \
  -DtargetTests="$R.domain.reglement.*,$R.application.reglement.*,$R.contrat.Reglements*" \
  -DtimestampedReports=false
