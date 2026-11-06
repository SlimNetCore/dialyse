#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et le cas d'usage du contexte dossier-medical
# (slice DM1 : constantes, lecture seule) : domain.* et application.*, testes par leurs tests unitaires, le cas d'usage (double en
# memoire) et le contrat du port contre le double. L'adapter JDBC est verifie par le contrat (H2, PostgreSQL 16) et le filet HTTP.
# Usage (Git Bash) : bash backend/scripts/pit-dm1-module.sh > pit-dm1-module.log 2>&1 &
# Rapport : backend/contexts/dossier-medical/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.dossiermedical
cd "$RACINE"
./mvnw -o -pl contexts/dossier-medical org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.HistoriqueDesConstantesEnMemoireContratTest" \
  -DtimestampedReports=false
