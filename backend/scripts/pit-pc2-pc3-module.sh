#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et les cas d'usage du contexte patient-couverture
# apres PC2 (assure principal) et PC3 (prise en charge) : domain.* et application.*, testes par leurs tests unitaires, les cas d'usage
# (doubles en memoire) et le contrat des depots contre le double (PC1 a PC3). Les adapters JDBC sont verifies par le contrat (H2,
# PostgreSQL 16) et les filets HTTP de caracterisation (lents, non rejoues par PIT).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-pc2-pc3-module.sh > pit-pc2-pc3-module.log 2>&1 &
# Rapport : backend/contexts/patient-couverture/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.patientcouverture
cd "$RACINE"
./mvnw -o -pl contexts/patient-couverture org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.PatientsEnMemoireContratTest" \
  -DtimestampedReports=false
