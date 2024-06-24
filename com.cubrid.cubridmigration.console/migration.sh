#!/bin/bash

DIR=$PWD
java -jar -Xms 1024M -Xmx 4096M $DIR/com.cubrid.cubridmigration.command-1.0.0-SNAPSHOT.jar "$@"
