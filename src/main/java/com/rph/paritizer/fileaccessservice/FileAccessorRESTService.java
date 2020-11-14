package com.rph.paritizer.fileaccessservice;

import com.rph.paritizer.fileaccessservice.exceptions.DiskNotFoundException;
import com.rph.paritizer.fileaccessservice.exceptions.NotDirectoryException;
import com.rph.paritizer.fileaccessservice.exceptions.NotFileException;
import com.rph.paritizer.fileaccessservice.exceptions.NotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;


@Path("fileAccessor")
public class FileAccessorRESTService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAccessorRESTService.class);

    @Context
    private UriInfo uriInfo;   // automagically set with each request

    @Context
    private HttpServletRequest request;

    private static boolean paused = false;

    /**
     * A GET operation can be used to obtain a list of files.
     * This list can be retrieved with a URL like this:
     *     http://localhost:8080/FileAccessService/api/fileAccessor/fileList
     *
     * Returns JSON of the form:
     *     { "urls" : [ url1, url2 url3, ... ] }
     * Each URL in the list corresponds to a file that can be retrieved with a GET operation.
     *
     * @return the Response. And hopefully, a JSON string.
     */
    @GET
    @Path("fileList")
    public Response getFileList() {
        return getFileList(FileAccessor.DEFAULT_DISK_NAME);
    }

    /**
     * A GET operation can be used to obtain a list of files contained on a virtual disk.
     * This list can be retrieved with a URL like this:
     *     http://localhost:8080/FileAccessService/api/fileAccessor/fileList/NameOfDisk
     * NameOfDisk is the name of a disk that the server knows about (set with a command line arg, for instance).
     * Any ' ' and '+' characters in the disk name do not need to be encoded.
     *
     * Returns JSON of the form:
     *     { "urls" : [ url1, url2 url3, ... ] }
     * Each URL in the list corresponds to a file on the disk that can be retrieved with a GET operation.
     *
     * @param diskName the name of the disk
     *
     * @return the Response. And hopefully, a JSON string.
     */
    @GET
    @Path("fileList/{diskName}")
    public Response getFileList(@PathParam("diskName") String diskName) {
        LOGGER.info("getFileList: diskName=\"{}\" sender=\"{}\"", diskName, request.getRemoteAddr() + ':' + request.getRemotePort());
        try {
            String uri = uriInfo.getRequestUri().toString();
            LOGGER.info("getFileList: uri=\"{}\"", uri);
            uri = convertDiskUriToFileUriPrefix(uri, diskName);
            LOGGER.info("getFileList: now uri=\"{}\"", uri);
            String json = FileAccessor.getJsonUrlList(uri, diskName);
            return Response.ok(json, "application/json")
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (FileNotFoundException ex) {
            String message = "Directory not found: " + ex.getMessage();
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotDirectoryException ex) {
            String message = "Not a directory: " + ex.getMessage();
            return Response.status(Response.Status.NOT_ACCEPTABLE.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotReadableException ex) {
            String message = "Read access denied: " + ex.getMessage();
            return Response.status(Response.Status.FORBIDDEN.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IOException ex) {
            String message = "I/O exception: " + ex.getMessage();
            System.err.println(message);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    /**
     * A GET operation can be used to obtain the bytes of a file, as a data stream.
     * The URL for the file should be of the following form:
     *     http://localhost:8080/FileAccessService/api/fileAccessor/file/NameOfDisk/directories/filename.jpg
     * NameOfDisk is the name of a disk that the server knows about. The remainder of the URL
     * specifies a particular file in the hierarchy of the specified disk.
     * Each URL in the JSON list returned by the fileList operation above is of this form,
     * so it should be unnecessary to construct the URL. Just do a GET operation on a URL from the list,
     * and you should be fine.
     *
     * Returns the bytes of the specified file, as a data stream.
     *
     * @param diskName the name of the disk
     * @param relativePath the path to the file on the disk
     *
     * @return the Response. And hopefully, a JSON string.
     */
    @GET
    @Path("file/{diskName}/{relativePath: .*}")
    public Response readFile(@PathParam("diskName") String diskName, @PathParam("relativePath") String relativePath) {
        LOGGER.info("readFile: diskName=\"{}\" relativePath=\"{}\"", diskName, relativePath);
        try {
            if (isPaused()) {
                LOGGER.info("readFile: paused");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), "paused")
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }
            String filePath = FileAccessor.getFilePath(diskName, relativePath);   // may throw exception
            StreamingOutput stream = out -> {
                try {
                    FileAccessor.copyFileToOutputStream(filePath, out);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            };
            return Response.ok(stream, getMediaType(filePath))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (DiskNotFoundException ex) {
            String message = "Disk not found: " + ex.getMessage();
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (FileNotFoundException ex) {
            String message = "File not found: " + ex.getMessage();
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotFileException ex) {
            String message = "Not a regular file: " + ex.getMessage();
            return Response.status(Response.Status.NOT_ACCEPTABLE.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotReadableException ex) {
            String message = "Read access denied: " + ex.getMessage();
            return Response.status(Response.Status.FORBIDDEN.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IOException ex) {
            String message = "I/O exception: " + ex.getMessage();
            System.err.println(message);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    /**
     * A POST operation can be used to create a new disk. The name of the disk is specified
     * in the URL, while the associated directory is specified as a plain text payload. So, for example:
     * curl -X POST -H "Content-Type: text/plain" --data "/Users/ron/Documents/Wedding Photos/JulianaWedding" \
     *     http://localhost:8090/FileAccessService/api/fileAccessor/newDisk/NewDiskName
     *
     * @param diskName the name of the virtual disk to be created
     * @param top the top directory of this virtual disk.
     *
     * @return the Response
     */
    @POST
    @Path("newDisk/{diskName}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response setDisk(@PathParam("diskName") String diskName, String top) {
        LOGGER.info("newDisk: diskname=\"" + diskName + "\" top=\"" + top + "\"");
        try {
            FileAccessor.addNewDisk(diskName, top);
            return Response.ok()
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (FileNotFoundException ex) {
            String message = "Directory not found: " + ex.getMessage();
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotDirectoryException ex) {
            String message = "Not a directory: " + ex.getMessage();
            return Response.status(Response.Status.NOT_ACCEPTABLE.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (NotReadableException ex) {
            String message = "Read access denied: " + ex.getMessage();
            return Response.status(Response.Status.FORBIDDEN.getStatusCode(), message)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    /**
     * A GET operation can be used to obtain a list of disks on the this server.
     * This list can be retrieved with a URL like this:
     *     http://localhost:8080/FileAccessService/api/fileAccessor/diskList
     *
     * Returns JSON of the form:
     *     { "disks" : [ diskName1, diskName2 diskName3, ... ] }
     *
     * @return the Response. And hopefully, a JSON string.
     */
    @GET
    @Path("diskList")
    public Response getDiskList() {
        LOGGER.info("getDiskList()");
        try {
            String json = FileAccessor.getDiskList();
            return Response.ok(json, "application/json")
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage())
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("pause")
    public Response pause() {
        LOGGER.info("pause()");
        setPaused(true);
        return Response.status(Response.Status.OK.getStatusCode(), "pause")
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }

    @GET
    @Path("resume")
    public Response resume() {
        LOGGER.info("resume()");
        setPaused(false);
        return Response.status(Response.Status.OK.getStatusCode(), "resume")
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }

    /**
     * Converts a disk URI of the form
     *     http://localhost:8080/FileAccessService/api/fileAccessor/fileList/NameOfDisk?foo=bar
     * into a file URI prefix of the form
     *     http://localhost:8080/FileAccessService/api/fileAccessor/file/NameOfDisk
     */
    private static String convertDiskUriToFileUriPrefix(String uri, String diskName) {
        // uri has not yet been decoded
        // diskName has been decoded.
        String originalUri = uri;
        uri = stripTrailingQueryString(uri);
        uri = uri.replaceAll("\\+", "%2B");   // because URLDecoder.decode converts '+' to ' '
        try {
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.toString());   // converts "%2B" back to '+'
        } catch (UnsupportedEncodingException ex) {   // this should never happen
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        if (!uri.endsWith(diskName)) {
            uri = uri + "/" + diskName;   // if no NameOfDisk was specified (DEFAULT_DISK_NAME)
        }
        if (!uri.contains("/fileList/")) {
            throw new IllegalArgumentException("missing \"fileList\" path component: " + originalUri);
        }
        uri = uri.replace("/fileList/", "/file/");
        uri = uri.replace("/file/.*", "/file/" + encode(diskName));
        return uri;
    }

    private static String stripTrailingQueryString(String uri) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }
        return uri;
    }

    private static String getMediaType(String filePath) {
        int index = filePath.lastIndexOf('.');
        if (index < 0) {
            return "*.*";
        }
        String suffix = filePath.substring(index).toLowerCase();
        if (".jpg".equals(suffix) || ".jpeg".equals(suffix)) {
            return "image/jpg";
        }
        if (".png".equals(suffix)) {
            return "image/png";
        }
        if (".txt".equals(suffix) || ".text".equals(suffix)) {
            return "text/plain";
        }
        if (".html".equals(suffix)) {
            return "text/html";
        }
        if (".xml".equals(suffix)) {
            return "text/xml";
        }
        return "*/*";
    }

    private static synchronized boolean isPaused() {
        return paused;
    }

    private static synchronized void setPaused(boolean yup) {
        paused = yup;
    }

    /**
     * This does two levels of encoding. First, the characters are UTF-8 encoded, resulting in an array of bytes.
     * Then, each byte that corresponds to a printable ASCII non-special-punctuation character is simply
     * that character.  Finally, if a byte value (unsigned) corresponds to a special punctuation character,
     * or is unprintable (127, or < 32), or is greater than 127, it is %NN encoded.
     *
     * @param s the string of characters
     * @return the UTF-8, %-encoded string
     */
    static String encode(String s) {
        StringBuilder resultStr = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {   // get UTF-8 encoded bytes
            char c = (char) (b & 0xFF);
            if (isUnsafe(c)) {
                resultStr.append('%');
                resultStr.append(toHex(c / 16));
                resultStr.append(toHex(c % 16));
            } else{
                resultStr.append(c);
            }
        }
        return resultStr.toString();
    }

    private static boolean isUnsafe(char ch) {
        if ((ch < 32) || (ch >= 127)) {
            return true;
        }
        return " +%$&,:;=?@<>#".indexOf(ch) >= 0;   // '/' must not be encoded
    }

    private static char toHex(int n) {
        return (char) ((n < 10) ? '0' + n : 'A' + n - 10);
    }
}
