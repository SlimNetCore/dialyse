#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et les cas d'usage du contexte planification
# (slices P1 a P4 : parametrage, referentiel des infirmiers, roulement, absences, remplacements, presence, charge, impression, controle de 07:15) : domain.* et
# application.*, testes par leurs tests unitaires, les cas d'usage (doubles en memoire) et le contrat des depots contre les doubles.
# Les adaptateurs JDBC sont verifies par le contrat (H2, PostgreSQL 16) et le filet HTTP de caracterisation (lent, non rejoue par PIT).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal.
# Usage (Git Bash) : bash backend/scripts/pit-p4-module.sh > pit-p4-module.log 2>&1 &
# Rapport : backend/contexts/planification/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.planification
cd "$RACINE"
./mvnw -o -pl contexts/planification org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.ParametrageEnMemoireContratTest,$R.contrat.InfirmiersEnMemoireContratTest,$R.contrat.PlanningDesInfirmiersEnMemoireContratTest,$R.contrat.PersonnelDeLaPresenceEnMemoireContratTest" \
  -DtimestampedReports=false
