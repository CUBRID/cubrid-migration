@echo off
set DIR=%CD%
set JRE=%DIR%\jre
set JAVA=%JRE%\bin\java
set ARGS=%*

%JAVA% -cp %JRE%\lib\*;%DIR%\lib\* -Xms1024M -Xmx4096M -jar %DIR%\com.cubrid.cubridmigration.command-1.0.0-SNAPSHOT.jar %ARGS%
@echo on
