#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur les calculs de consultation et de pilotage de la GMAO
# (G6, refonte sur place : contexts/gmao ... AnalyseDeDecision, CalculDIndisponibilite, IndicateursDIntervention, chronologie),
# testes par leurs tests unitaires de domaine et par les cas d'usage de pilotage (rapides, sans Spring).
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash, depuis n'importe ou) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-g6-calculs.sh
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/c/Users/TS-CON~1/AppData/Local/Temp/g6-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/' | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-gmao/0.0.1-SNAPSHOT/contexts-gmao-0.0.1-SNAPSHOT.jar|$B/contexts/gmao/target/classes|")
CP="$B/contexts/gmao/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
cd /c/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend/contexts/gmao
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/contexts/gmao/target/pit-reports-g6" \
  --targetClasses 'com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision*,com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibilite*,com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDIntervention*,com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie*' \
  --targetTests 'com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecisionTest,com.hemodialyse.backend.contexts.gmao.domain.equipement.CalculDIndisponibiliteTest,com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDInterventionTest,com.hemodialyse.backend.contexts.gmao.domain.intervention.ChronologieDInterventionTest,com.hemodialyse.backend.contexts.gmao.application.pilotage.*' \
  --sourceDirs "$B/contexts/gmao/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 60000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
