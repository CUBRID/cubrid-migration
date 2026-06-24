# ![Migration Icon](docs/migration_icon.svg) CUBRID Migration Toolkit (CMT)

[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](plugins/com.cubrid.cubridmigration.app/src/com/cubrid/cubridmigration/app/copyright.txt)  [![Release](https://img.shields.io/github/v/release/CUBRID/cubrid-migration)](https://github.com/CUBRID/cubrid-migration/releases)

**CUBRID Migration Toolkit (CMT)** is a comprehensive software tool designed to migrate data and schemas from various source databases to the CUBRID Database Server.

The tool automatically maps complex schemas and data from source databases to CUBRID-compatible formats. It also allows for detailed customization before the migration process starts, enabling users to manually adjust data types and column attributes to fit their specific needs.

## Key Features

*   **Supported Source Databases**
    * CUBRID (Supports version-to-version migration)
    * Oracle
    * MySQL
    * MS SQL Server (MSSQL)
    * MariaDB
    * Informix
    * Tibero
*   **Schema & Data Migration**
	* Converts table structures, indexes, and views to CUBRID-compatible formats.
	* Automatically converts data types based on predefined CUBRID type mappings.
*   **Execution Modes**
    *   **GUI Mode(Desktop)**: Provides an intuitive user interface based on Eclipse RCP for easy configuration and monitoring.
    *   **CLI Mode(Console)**: Executes migrations using pre-configured scripts, enabling lightweight and fast operation without a GUI.

## Downloads
You can download the latest binaries from the following links:
- **Official Downloads**: [http://www.cubrid.org/downloads](http://www.cubrid.org/downloads)
- **FTP Server**: [http://ftp.cubrid.org](http://ftp.cubrid.org/CUBRID_Tools/CUBRID_Migration_Toolkit/)

## Build from Source

### 1. Prerequisites
Ensure you have the following installed before building:
-  **Java Development Kit (JDK)**: Version 21 or higher
*   **Apache Maven**: Version 3.9.0 or higher
### 2. How to Build
Use the `build.sh` script in the root directory to build the project.
> **Note**: For Windows users, you can use the `build.ps1` script.

*   **Build All**
    ```bash
    sh build.sh
    ```

*   **Build Desktop (GUI) Version**
    ```bash
    sh build.sh -profile desktop
    ```

*   **Build Console (CLI) Version**
    ```bash
    sh build.sh -profile console
    ```

 The build artifacts(`.zip`, `tar.gz`) are generated in the `target` directory at the project root.

### 3. Build Options

| Option | Values                         | Description           |
| ------ | ------------------------------ | --------------------- |
| `-p`   | all(default), desktop, console | Set the build profile |
| `-X`   | N/A                            | Enable debug logging  |

## License
This project is open source. The CUBRID Migration Toolkit is distributed under the BSD License.  
For more information about CUBRID Tools license policy, please visit https://www.cubrid.org/license

## Getting Help
If you encounter any difficulties, have questions, find bugs, or want to share suggestions, please visit our community:
- Reddit: [https://www.reddit.com/r/CUBRID/](https://www.reddit.com/r/CUBRID/)
- Jira: http://jira.cubrid.org/projects/TOOLS/issues

