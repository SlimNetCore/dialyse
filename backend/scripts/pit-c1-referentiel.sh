#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur le domaine et les cas d'usage du referentiel comptable
# (C1, refonte sur place : contexts/comptabilite ... domain.plan, domain.journal, domain.payeur, application.*), testes par leurs
# tests unitaires de domaine et de cas d'usage avec doubles en memoire (rapides, sans Spring).
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash, depuis n'importe ou) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-c1-referentiel.sh
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/c/Users/TS-CON~1/AppData/Local/Temp/c1-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/' | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-comptabilite/0.0.1-SNAPSHOT/contexts-comptabilite-0.0.1-SNAPSHOT.jar|$B/contexts/comptabilite/target/classes|")
CP="$B/contexts/comptabilite/target/test-classes;$B/contexts/comptabilite/target/classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
cd /c/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend/contexts/comptabilite
P=com.hemodialyse.backend.contexts.comptabilite
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/contexts/comptabilite/target/pit-reports-c1" \
  --targetClasses "$P.domain.plan.*,$P.domain.journal.*,$P.domain.payeur.*,$P.application.plan.*,$P.application.journal.*,$P.application.payeur.*" \
  --excludedClasses "*Test,$P.doubles.*" \
  --targetTests "$P.domain.*,$P.application.*" \
  --sourceDirs "$B/contexts/comptabilite/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 60000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
