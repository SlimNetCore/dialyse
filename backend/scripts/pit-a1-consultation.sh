#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur les lectures legacy d'absences de patients (slice A1) :
# AbsencePatientJdbcAdapter (liste, semaine, synthese), AbsenceDonneesJdbcAdapter (seances realisees),
# DirectionAbsencesQueryService (lecture Direction), testes par le filet HTTP de caracterisation A1 (contexte Spring : lent).
# Les mutants des methodes d'ecriture (save, declarer, qualifier...) ne sont pas exerces par A1 (slice A2/A3) : "NO_COVERAGE".
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-a1-consultation.sh > pit-a1.log 2>&1 &
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/tmp/abs-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/' \
  | sed -E "s#C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-([a-z]+)/0.0.1-SNAPSHOT/[^;]*#$B/contexts/\1/target/classes#g" \
  | sed -E "s#C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/(bc-[a-z]+|shared-kernel)/0.0.1-SNAPSHOT/[^;]*#$B/\1/target/classes#g")
CP="$B/platform/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
P=com.hemodialyse.backend
cd "$B/platform"
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/platform/target/pit-reports-a1" \
  --targetClasses "$P.infrastructure.persistence.adapter.absence.AbsencePatientJdbcAdapter*,$P.infrastructure.persistence.adapter.absence.AbsenceDonneesJdbcAdapter*,$P.application.direction.DirectionAbsencesQueryService*" \
  --targetTests "$P.characterization.absencespatients.consultation.*" \
  --sourceDirs "$B/platform/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
