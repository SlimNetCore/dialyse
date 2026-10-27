#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et les cas d'usage du contexte planification
# (slices P1 a P7 : ... planning de la semaine, presence unique des patients, liberation des places) :
# domain.* et application.*, testes par leurs tests unitaires, les cas d'usage (doubles en memoire, dont LiberationEnMemoire) et le
# contrat des depots contre les doubles (parametrage, infirmiers, planning, personnel, places).
# Les adaptateurs JDBC sont verifies par le contrat (H2, PostgreSQL 16) et le filet HTTP de caracterisation (lent, non rejoue par PIT).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal. JAMAIS en parallele d'un autre build Maven.
# Usage (Git Bash) : bash backend/scripts/pit-p7-module.sh > pit-p7-module.log 2>&1 &
# Rapport : backend/contexts/planification/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.planification
cd "$RACINE"
./mvnw -o -pl contexts/planification org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.ParametrageEnMemoireContratTest,$R.contrat.InfirmiersEnMemoireContratTest,$R.contrat.PlanningDesInfirmiersEnMemoireContratTest,$R.contrat.PersonnelDeLaPresenceEnMemoireContratTest,$R.contrat.PlacesEnMemoireContratTest" \
  -DtimestampedReports=false
