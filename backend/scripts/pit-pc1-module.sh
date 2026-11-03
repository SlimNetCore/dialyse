#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et les cas d'usage du contexte patient-couverture
# (slice PC1 : fiche, etats, mouvements, recherche, syntheses, assures) : domain.* et application.*, testes par leurs tests unitaires, les cas
# d'usage (doubles en memoire) et le contrat du depot des patients contre le double. Les adapters JDBC sont verifies par le contrat (H2,
# PostgreSQL 16) et le filet HTTP de caracterisation (lent, non rejoue par PIT).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-pc1-module.sh > pit-pc1-module.log 2>&1 &
# Rapport : backend/contexts/patient-couverture/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.patientcouverture
cd "$RACINE"
./mvnw -o -pl contexts/patient-couverture org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.PatientsEnMemoireContratTest" \
  -DtimestampedReports=false
