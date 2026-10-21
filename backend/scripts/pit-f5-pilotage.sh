#!/bin/bash
# Mutation PIT 1.30.0 (plugin Maven du module, junit5-plugin 1.2.3) sur le calcul et les cas d'usage de consultation et de
# pilotage du contexte facturation (slice F5, refonte sur place) : application.pilotage.* (barres de repartition, colonnes de la
# synthese, format, garde du mois, validation de la periode), testes par leurs tests unitaires (doubles en memoire) et par le
# contrat des lectures contre le double. Les anciennes classes du legacy (FacturationDomainService, services de platform) sont
# retirees ; le filet HTTP de caracterisation F5 couvre le rendu et la Direction (lent, non rejoue par PIT).
# Rapide (aucun contexte Spring) : a lancer en arriere-plan avec un journal, puis tail.
# Usage (Git Bash) : bash backend/scripts/pit-f5-pilotage.sh > pit-f5.log 2>&1 &
# Rapport : backend/contexts/facturation/target/pit-reports/
set -uo pipefail
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
R=com.hemodialyse.backend.contexts.facturation
cd "$RACINE"
./mvnw -o -pl contexts/facturation org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$R.application.pilotage.*" \
  -DtargetTests="$R.application.pilotage.*,$R.contrat.PilotageEnMemoireContratTest" \
  -DtimestampedReports=false
