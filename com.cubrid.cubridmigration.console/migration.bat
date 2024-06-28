@echo off
SET fn=%1 %2 %3 %4 %5 %6 %7 %8 %9
java -Xms1024M -Xmx4096M -jar com.cubrid.cubridmigration.command-1.0.0-SNAPSHOT.jar %fn%
@echo on
