#!/bin/bash

DIR=$PWD
java -jar -Xms1024M -Xmx4096M $DIR/com.cubrid.cubridmigration.command-1.0.0-SNAPSHOT.jar "$@"
