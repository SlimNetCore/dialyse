#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur le domaine des pieces jointes (G5, refonte sur place :
# contexts/gmao ... domain.piecejointe.*), teste par ses tests unitaires de domaine et de cas d'usage (rapides, sans Spring).
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash, depuis n'importe ou) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-g5-documents.sh
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/c/Users/TS-CON~1/AppData/Local/Temp/g5-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/' | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-gmao/0.0.1-SNAPSHOT/contexts-gmao-0.0.1-SNAPSHOT.jar|$B/contexts/gmao/target/classes|")
CP="$B/contexts/gmao/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
cd /c/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend/contexts/gmao
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/contexts/gmao/target/pit-reports-g5" \
  --targetClasses 'com.hemodialyse.backend.contexts.gmao.domain.piecejointe.*' \
  --targetTests 'com.hemodialyse.backend.contexts.gmao.domain.piecejointe.*,com.hemodialyse.backend.contexts.gmao.application.piecejointe.*' \
  --sourceDirs "$B/contexts/gmao/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 60000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
