# Koji Build Finder

Koji Build Finder iterates over any files or directories in the input, recursively scanning any supported possibly compressed archive types, locating the associated Koji build for each file matching a supported Koji archive type. It attempts to find at least one Koji build containing the file checksum (duplicate builds result in a warning) and records files that don't have any corresponding Koji build to a file. For files with a corresponding Koji build, if the Koji build does not have a corresponding Koji task, it reports the build as an import. For builds with a corresponding Koji task, it writes information about the build to a file.

Additionally, it can write various files required by other tools used as part of the productization process, including NVR (`name-version-release`) and GAV (`groupId:artifactId:version`) lists.

### Development

An example `codestyle-intellij.xml` code formatting style is supplied for IntelliJ. `mvn clean install` will compile and run all of the unit tests.

## Operation

The support for various compressed archive types relies on [Apache Commons VFS](https://commons.apache.org/proper/commons-vfs/) and the compressor and archive formats that Commons VFS can open *automatically*. If an exception occurs while trying to open a file, then the file is considered to be a normal file and recursive processing of the file is aborted.

The default supported Koji archive types are `jar`, `xml`, `pom`, `so`, `dll`, and `dylib`. The software uses [Koji Java Interface](https://github.com/release-engineering/kojiji) for Koji support and asks for all known extensions for the given type name. If you were to specify no Koji archive types, the software will ask the Koji server for all known Koji archive types, but the default set of types is meant to give a reasonable default.

Checksum and build information are both stored in JSON format. The configuration file is also written in JSON format via a helper.

The format of the reports is currently either text or HTML.

## Usage

The executable jar takes the following options.

    $ java -jar target/koji-build-finder-1.0.0-SNAPSHOT-jar-with-dependencies.jar -h
    Usage: koji-build-finder <files>
     -a,--archive-type <type>         Add a koji archive type to check. Default:
                                      [null].
     -c,--config <file>               Specify configuration file to use. Default:
                                      ${user.home}/.koji-build-finder/config.json.
     -d,--debug                       Enable debug logging.
     -h,--help                        Show this help message.
     -k,--checksum-only               Only checksum files and do not find sources.
                                      Default: false.
        --koji-hub-url <url>          Set the Koji hub URL.
        --koji-web-url <url>          Set the Koji web URL.
        --krb-ccache <ccache>         Set the location of Kerberos credential cache.
        --krb-keytab <keytab>         Set the location of Kerberos keytab.
        --krb-password <password>     Set Kerberos password.
        --krb-principal <principal>   Set Kerberos client principal.
        --krb-service <service>       Set Kerberos client service.
     -o,--output-directory            Use specified output directory for report files.
     -t,--checksum-type <type>        Checksum type (md5, sha1, sha256). Default:
                                      md5.
     -x,--exclude <pattern>           Add a pattern to exclude files from source
                                      check. Default: [null].
## Getting Started

On the first run, it will write a starter configuration file. You may optionally edit this file by hand. You do not need to create it ahead of time as the first run should create a default configuration file if none exists.

### Configuration file format

The default configuration file, `config.json`, is as follows.

    {
      "archive-types" : [ "jar", "xml", "pom", "so", "dll", "dylib" ],
      "checksum-only" : false,
      "checksum-type" : "md5",
      "excludes" : [ "^(?!.*/pom\\.xml$).*/.*\\.xml$" ],
      "koji-hub-url" : "http://kojihub.my.host/kojihub",
      "koji-web-url" : "https://kojiweb.my.host/koji"
    }

The `archive-types` option specifies the Koji archive types to include in the search.

The `excludes` option is a single regular expression pattern which specifies any additional file patterns to exclude from the search.

The `checksum-only` option specifies whether or not to skip the Koji build look-up stage and only checksum the files in the input.

The `checksum-type` option specifies the checksum type to use for lookups. Note that at this time Koji can only support a single checksum type in its database, `md5`, even though the API currently provides additional support for `sha256` and `sha512` checksum types.

The `koji-hub-url` and `koji-web-url` configuration options must be set to valid URLs for your particular network.

All of the options found in the configuration file can also be specified and overridden via command-line options.

### Command-line Options

The `koji-url` option is the only required command-line option (if not specified in the configuration file) and this option specifies the URL for the Koji server. Currently, this option is required even in checksum-only mode where it remains unused.

The `krb-` options are used for logging in via Kerberos as opposed to via SSL as it does not require the additional setup of SSL certificates. Note that the [Apache Kerby](https://directory.apache.org/kerby/) library is used to supply Kerberos functionality. As such, interaction with the other Kerberos implementations, such as the canonical MIT Kerberos implementation, may not work with the `krb-ccache` or `krb-keytab` options. The `krb-principal` and `krb-password` options are expected to always work, but care should be taken to protect your password. Note that when using the `krb-` options, the `krb-service` option is necessary in order for Kerberos login to work.

### Execution

After optionally completing setup of the configuration file, `config.json`, you can run the software with a command as follows.

    $ java -jar koji-build-finder-<version>-jar-with-dependencies.jar /path/to/distribution.zip

In this execution, the Koji source finder will read through the file `distribution.zip`, trying to match each file entry against a build in the Koji database provided that the file name matches one of the specified Koji archive types and does not match the exclusion pattern.

On the first completed run, the software will create a `checksum-<checksum-type>.json` file to cache the file checksums and a `builds.json` file to cache the Koji build information. Currently, if you wish to perform a clean run, you must *manually* remove the JSON files. Otherwise, the cache will be loaded and the information will not be recalculated.

## Output File Formats

This section describes the JSON files used for caching the distribution information between runs in more detail.

### Checksums File

The `checksum-md5.json` file contains a map where the key is the MD5 checksum of the file and the value is a list of all files with the checksum. Note that it is possible to have more than one file with the given checksum.

For completeness, the `checksum-md5.json` file contains every single file found in the input, including any files found by recursively scanning compressed files or inside archive files.

### Builds File

The `builds.json` file contains a map where the key is the Koji build ID. The special ID of 0 is used for files with no associated build, as Koji builds start at ID 1. The value contains additional maps, a partial list of what is contained is Koji Build Info, Koji Task Info, Koji Task Request, a Koji Archive, a list of all remote archives associated with the build and a list of local files from the distribution associated with this build.

## Reports

After a completed run, several output files are produced in the current directory. These files are overwritten on additional runs, so if the output files need to be saved between multiple runs then unique directories for each run.

### HTML Report

This is a report in the file `output.html` in HTML format containing all Koji builds found as well as any problems associated with the builds.

#### Problems Flagged

The HTML report currently reports total builds, including number of builds that are imports. Additionally, it reports

* Matching files with no Koji build associated. These are potentially files that need to be rebuilt from source, for example, a dynamic library downloaded from upstream during the build process.
* Builds that are *imports* and not built from source. These represent files which as they are builds with a known community import in the Koji database almost certainly need to be built from source and/or removed from the distribution if not required at runtime. These often appear inside shaded jars and the like.

### NVR Report

This is a report in the file `nvr.txt` text format of one `name-version-release` per line, as is typical with Koji native builds and RPMS.

### GAV Report

This is a report in the file `gav.txt` text format of one `groupId:artifactId:version` per line, as is typical with Maven builds.
