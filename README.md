CUBRID Migration Toolkit (CMT) is a software tool that allows migrating data from MySQL/Oracle/CUBRID to CUBRID Database Server.

The data and schema in the source database can be as sophisticated as possible. CMT provides the default settings to cast native MySQL and other DBMS data types to native CUBRID data types. However, most of them will overlap as CUBRID provides over 90% MySQL compatibility. If it is necessary to have the destination column data type different from the default settings, it can be easily customized before the migration process starts.

---
__Note__: When you want to use this tool, you need to download JDBC files first from their official site.

 - MYSQL: https://dev.mysql.com/downloads/connector/j/
 - ORACLE: http://www.oracle.com/technetwork/database/features/jdbc/index-091264.html
 - SQL SERVER: https://msdn.microsoft.com/en-us/sqlserver/aa937724.aspx

## Prepare 3rd party libraries for CMT

### prepare dependency libs

```
cd cubridmigration/com.cubrid.cubridmigration.build/
wget http://ftp.cubrid.org/CUBRID_Docs/CMT/cmt-build-3rdparty-libs.tgz
tar -xvf cmt-build-3rdparty-libs.tgz
```

### prepare dependency files for test

```
cd cubridmigration/com.cubrid.cubridmigration.build/
wget http://ftp.cubrid.org/CUBRID_Docs/CMT/cmt-build-3rdparty-test.zip
unzip cmt-build-3rdparty-test.zip
```

### prepare dependency drivers for testfragments

```
cd cubridmigration/com.cubrid.cubridmigration.core.testfragment/
wget http://ftp.cubrid.org/CUBRID_Docs/CMT/cmt-test-3rdparty-drivers.zip
unzip cmt-test-3rdparty-drivers.zip -d jdbc

cd cubridmigration/com.cubrid.cubridmigration.ui.testfragment/
wget http://ftp.cubrid.org/CUBRID_Docs/CMT/cmt-test-3rdparty-drivers.zip
unzip cmt-test-3rdparty-drivers.zip -d jdbc
```

## Build from sources

### Prerequisites
1. [OpenJDK 8](https://adoptium.net/temurin/releases/?version=8)
2. [Apache Maven 3.9.6](https://maven.apache.org/download.cgi)
3. git
4. Internet access

### Build

```
git clone -b release/eclipse_upgrade --single-branch https://github.com/CUBRID/cubrid-migration.git eclipse_upgrade
cd eclipse_upgrade

// all build
sh build -X

// desktop build
sh build -profile desktop -X

// console build
sh build -profile console -X
```
