#!/usr/bin/env bash

MVN="`which mvn`"
PROFILE="all"
MVN_DEBUG=""
DIR=$PWD
TARGET=$DIR/target
PRODUCT_TARGET=$DIR/com.cubrid.cubridmigration.product/target
CONSOLE_TARGET=$DIR/com.cubrid.cubridmigration.console/target
VERSION_DIR=$DIR/com.cubrid.cubridmigration.ui
VERSION_FILE_PATH=${VERSION_DIR}/version.properties

function show_usage ()
{
  echo " OPTIONS"
  echo "  -p [all(a)/desktop(d)/console(c)]       select profile"
  echo "  -X                                      enable debug log"

  echo ""
  echo " EXAMPLES"
  echo "  build.sh -p desktop -X"
  echo "  build.sh -p c -X"
  echo "  build.sh"
  echo ""
}

function get_options ()
{
  while getopts ":p:X" opt; do
    case $opt in
	  p ) PROFILE="${OPTARG,,}" ;;
          X ) MVN_DEBUG="-Dtycho.debug.resolver=true -X" ;;
          * ) show_usage 
              exit ;;
    esac
  done
}

function check_configuration ()
{
  if [ -n "${JAVA_HOME}" ]; then
    echo JAVA_HOME: ${JAVA_HOME}
  fi

  if [ -n "${MAVEN_HOME}" ]; then
    echo MAVEN_HOME: ${MAVEN_HOME}
    MVN="${MAVEN}/bin/mvn"
  fi

  if [ -z "$MVN" ]; then
    echo maven not found.
    exit 1
  fi
}

function update_build_version ()
{
  echo "Version File Update....  (com.cubrid.cubridmigration.ui/version.properties)"

  if [ -d ${DIR}/.git ]; then
    COMMIT_NUMBER=$(git rev-list --count HEAD | awk '{ printf "%04d", $1 }')
  else
    COMMIT_NUMBER=0000
  fi

  RELEASE_VERSION=$(cat ${VERSION_FILE_PATH} | grep releaseVersion | cut -d '=' -f2)

  echo "RELEASE_VERSION=" $RELEASE_VERSION
  echo "COMMIT_NUMBER=" $COMMIT_NUMBER
  FULL_VERSION=buildVersionId=${RELEASE_VERSION}.${COMMIT_NUMBER}

  sed -i '/buildVersionId/d' ${VERSION_FILE_PATH}
  echo $FULL_VERSION >> ${VERSION_FILE_PATH}
}

function copy_desktopcmt_to_directory ()
{
  CMT_LINUX=$PRODUCT_TARGET/cubridmigration-linux.tar.gz
  if [ -e $CMT_LINUX ]; then
    cp -vfp $CMT_LINUX $TARGET
  fi

  CMT_MAC=$PRODUCT_TARGET/cubridmigration-mac.tar.gz
  if [ -e $CMT_MAC ]; then
    cp -vfp $CMT_MAC $TARGET
  fi

  CMT_WINDOWS=$PRODUCT_TARGET/cubridmigration-windows.zip
  if [ -e $CMT_WINDOWS ]; then
    cp -vfp $CMT_WINDOWS $TARGET
  fi
}

function copy_consolecmt_to_directory ()
{
  CONSOLE_LINUX=$CONSOLE_TARGET/cubridmigration-console-linux.tar.gz
  if [ -e $CONSOLE_LINUX ]; then
    cp -vfp $CONSOLE_LINUX $TARGET
  fi

  CONSOLE_WINDOWS=$CONSOLE_TARGET/cubridmigration-console-windows.zip
  if [ -e $CONSOLE_WINDOWS ]; then
    cp -vfp $CONSOLE_WINDOWS $TARGET
  fi

}

function copy_cmt_to_directory ()
{
  if [ ! -d $TARGET ]; then
    mkdir $TARGET
  fi

  if [ $PROFILE = "all" ] || [ $PROFILE = "a" ]; then
    copy_desktopcmt_to_directory
    copy_consolecmt_to_directory
  elif [ $PROFILE = "desktop" ] || [ $PROFILE = "d" ]; then
    copy_desktopcmt_to_directory
  elif [ $PROFILE = "console" ] || [ $PROFILE = "c" ]; then
    copy_consolecmt_to_directory
  fi
}

function cmt_banner ()
{
 echo '
 
 ____                   ______   
/\  _`\     /`\_/`\    /\__  _\  
\ \ \/\_\  /\      \   \/_/\ \/  
 \ \ \/_/_ \ \ \__\ \     \ \ \  
  \ \ \L\ \ \ \ \_/\ \     \ \ \ 
   \ \____/  \ \_\\ \_\     \ \_\
    \/___/    \/_/ \/_/      \/_/
                                 
                                 
' 
}

# MAIN
cmt_banner

get_options "$@"
check_configuration
update_build_version

if [ $PROFILE = "all" ] || [ $PROFILE = "a" ]; then
  $MVN clean package $MVN_DEBUG
  $MVN clean package -Pconsole $MVN_DEBUG
elif [ $PROFILE = "desktop" ] || [ $PROFILE = "d" ]; then
  $MVN clean package $MVN_DEBUG
elif [ $PROFILE = "console" ] || [ $PROFILE = "c" ]; then
  $MVN clean package -Pconsole $MVN_DEBUG
else
  show_usage
fi

copy_cmt_to_directory
