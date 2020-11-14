File Access Service
-------------------

This service provides access to files contained in virtual disks
(rooted hierarchies). You can run the service within a container
(such as Tomcat), or as a standalone executable. You don't need
to do both.

To build and run, you must have at least Java 8 installed on your system.
To build, type the following command:

```bash
    ./gradlew build
```

This will create the following three files in the `build/libs` directory:

```bash
    FileAccessService-1.0-all.jar
    FileAccessService-1.0.jar
    FileAccessService-1.0.war
```

The `FileAccessService-1.0.war` war file can be deployed and run in
your favorite servlet container. For example, to run under Tomcat
(assuming Tomcat is installed and running on your system):

```bash
    cp build/libs/FileAccessService-1.0.war $TOMCAT_HOME/webapps/FileAccessService.war
```

Notice that the version specification (`-1.0`) was removed
in the destination filename.

If you would prefer to run the service as a standalone application,
`FileAccessService-1.0-all.jar` is an executable jar file.
This can be invoked from the command line like this:

```bash
    java -jar build/libs/FileAccessService-1.0-all.jar  [ args ]
```

Possible args are:

```bash
    --port portNumber
```

and

```bash
    --disk diskName path/to/top/directory/of/the/disk
```

If not specified, the port number will be randomly selected by the server.
The URL (including the port number) will be displayed on the standard output.

Usually the path argument will be specified as an absolute path.
Multiple `--disk` triples can be specified.

If there is a directory named `DefaultDisk` in the current directory or your home directory,
it can be accessed as a disk named `DefaultDisk`. '`DefaultDisk`' (literal) is both
the disk name and the top directory. So if you don't care what port number
is used, and you have a `DefaultDisk` directory in your current or home directory,
then it is not necessary to supply any arguments.

However you launch the service, it can be accessed from your client
in the following ways (use your own host and port name, as displayed
on the standard output):

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/diskList
```

will return JSON specifying the names of the disks known to this server.

To obtain a list of URLs that can be used to read the files
from the disk named _`diskName`_:

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/fileList/diskName
```

If the trailing disk name path component is omitted, it is as if `DefaultDisk`
had been specified for the disk name. There is a `DefaultDisk` directory here,
so if you run the command line application from here, this default disk
will be used.

Actually reading a file is easy -- just use one of the URLs returned
by the fileList operation. The returned file URLs are already suitably
%-encoded. For example, this URL would retrieve the bits
from a particular file on the disk named `PhotoDisk01`:

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/file/PhotoDisk01/directory%2001/696547_0445.jpg
```

The contents of the file will be streamed back to the client.

Finally, if you want to create a new disk for subsequent file reading
(especially useful if the server has been launched in a container),
a POST operation using a URL of the following form can be performed:

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/newDisk/diskName
```

with a payload that specifies the path (generally absolute)
to the top directory of the new disk _`diskName`_, like this:

```bash
    /Users/ron/Documents/WeddingPhotos/JulianaWedding
```

and a ContentType header of `text/plain`. A curl command to accomplish
the creation of a disk named `PhotoDisk01` would be:

```bash
    curl -X POST -H "Content-Type: text/plain" \
        --data "/Users/ron/Documents/WeddingPhotos/JulianaWedding" \
        http://localhost:8080/FileAccessService/api/fileAccessor/newDisk/PhotoDisk01
```

Subsequent `listDisk`, `listFiles` and `file` operations can be performed
with the new disk.

File service can be paused by doing a GET to

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/pause
```

While paused, file access operation will return 503 (service unavailable).
File service can be resumed with a GET operation to:

```bash
    http://localhost:8080/FileAccessService/api/fileAccessor/resume
```

There is a `SampleDisks` directory here containing a few images
in subdirectories that you can use to exercise the service.
