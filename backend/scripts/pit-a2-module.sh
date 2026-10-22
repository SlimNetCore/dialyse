#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le domaine et les cas d'usage de consultation du contexte
# absences-patients (slices A1 et A2, refonte sur place) : domain.* et application.*, testes par leurs tests unitaires, les cas d'usage
# (double en memoire) et le contrat des lectures et des absences contre le double. Les adaptateurs JDBC sont verifies par le contrat (H2, PostgreSQL 16)
# et le filet HTTP de caracterisation A1 (lent, non rejoue par PIT, cf. backend/scripts/pit-a1-consultation.sh).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-a2-module.sh > pit-a1-module.log 2>&1 &
# Rapport : backend/contexts/absences-patients/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.absencespatients
cd "$RACINE"
./mvnw -o -pl contexts/absences-patients org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.domain.*,$R.application.*" \
  -DtargetTests="$R.domain.*,$R.application.*,$R.contrat.LecturesDuSuiviEnMemoireContratTest,$R.contrat.AbsencesEnMemoireContratTest" \
  -DtimestampedReports=false
