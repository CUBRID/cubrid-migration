#!/bin/bash

DIR=$PWD
JRE=$DIR/jre
JAVA=$JRE/bin/java

$JAVA -cp $JRE/lib/*:$DIR/lib/* -Xms1024M -Xmx4096M -jar $DIR/com.cubrid.cubridmigration.command-1.0.0-SNAPSHOT.jar "$@"
