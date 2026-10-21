#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3, configuration du pom du module) sur la cloture mensuelle de facturation (F7) :
# domaine (domain.cloture) et application (application.cloture), testes par leurs tests unitaires purs (domaine, doubles en memoire)
# et par les tests de la garde dans les cas d'usage de validation et de simulation. Cible : >= 70 % de mutants tues sur le domaine.
# Lent : a lancer en arriere-plan avec un journal, puis tail/grep. Rapport : backend/contexts/facturation/target/pit-reports.
# Prerequis : le module comptabilite installe dans .m2 avec le port publie PeriodesComptables (premiere commande ci-dessous).
# Usage (Git Bash) : bash backend/scripts/pit-f7-cloture.sh > pit-f7.log 2>&1 &
cd "$(dirname "$0")/.." || exit 2
P=com.hemodialyse.backend.contexts.facturation
./mvnw -o -q -pl contexts/comptabilite install -DskipTests || exit 3
./mvnw -o -pl contexts/facturation test-compile org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses="$P.domain.cloture.*,$P.application.cloture.*" \
  -DtargetTests="$P.domain.cloture.*,$P.application.cloture.*,$P.application.simulation.ModificationsDUnMoisClotureTest,$P.application.validation.ValiderLaFacturationTest"
