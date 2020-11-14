package com.rph.paritizer.fileaccessservice;

import com.rph.paritizer.fileaccessservice.exceptions.DiskNotFoundException;
import com.rph.paritizer.fileaccessservice.exceptions.NotDirectoryException;
import com.rph.paritizer.fileaccessservice.exceptions.NotFileException;
import com.rph.paritizer.fileaccessservice.exceptions.NotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


class FileAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAccessor.class);

    static final String DEFAULT_DISK_NAME = "DefaultDisk";   // current directory (System.getProperty("user.dir"))

    private static Map<String, String> diskNameToTop = new HashMap<>();

    static void addNewDisk(String diskName, String top)
            throws FileNotFoundException, NotDirectoryException, NotReadableException {
        File topFile = new File(top);
        if (!topFile.exists()) {
            throw new FileNotFoundException(topFile.toString());
        }
        if (!topFile.isDirectory()) {
            throw new NotDirectoryException(topFile.toString());
        }
        if (!topFile.canRead()) {
            throw new NotReadableException(topFile.toString());
        }
        diskNameToTop.put(diskName, top);
    }

    private static String getDiskTopDirectory(String diskName)
            throws DiskNotFoundException, FileNotFoundException, NotDirectoryException, NotReadableException {
        String top = diskNameToTop.get(diskName);
        if (top == null) {
            if (!DEFAULT_DISK_NAME.equals(diskName)) {
                throw new DiskNotFoundException("no such disk: " + diskName);
            }
            top = getDefaultDiskTop();
            addNewDisk(diskName, top);
            return top;
        }
        File topFile = new File(top);
        if (!topFile.exists()) {
            throw new FileNotFoundException(topFile.toString());
        }
        if (!topFile.isDirectory()) {
            throw new NotDirectoryException(topFile.toString());
        }
        if (!topFile.canRead()) {
            throw new NotReadableException(topFile.toString());
        }
        return top;
    }

    static String getDefaultDiskTop() throws FileNotFoundException {
        String currentDir = System.getProperty("user.dir");
        if (currentDir == null) {
            LOGGER.warn("getDefaultDiskTop: null user.dir system property!");
        } else {
            String dir = getReadableDefaultDiskDirectory(currentDir);
            if (dir != null) {
                return dir;
            }
        }

        String homeDir = System.getProperty("user.home");
        if (homeDir == null) {
            LOGGER.warn("getDefaultDiskTop: null user.home system property!");
        } else {
            String dir = getReadableDefaultDiskDirectory(homeDir);
            if (dir != null) {
                return dir;
            }
        }
        throw new FileNotFoundException("A readable directory named DefaultDir was not found in the current director (" + currentDir + ")"
                                        + " nor in the user's home directory (" + homeDir + ")");
    }

    private static String getReadableDefaultDiskDirectory(String parentDir) {
        File defaultDir = new File(parentDir, DEFAULT_DISK_NAME);
        return (defaultDir.isDirectory() && defaultDir.canRead()) ? defaultDir.getPath() : null;
    }

    static String getDiskList() {
        StringBuilder buf = new StringBuilder();
        buf.append("{ \"disks\": [\n");
        for (String diskName : diskNameToTop.keySet()) {
            buf.append("    \"");
            buf.append(diskName);
            buf.append("\",\n");
        }

        // delete trailing ','
        int index = buf.length();
        while ((index > 0) && Character.isWhitespace(buf.charAt(--index))) {
            // do nothing
        }
        if ((index > 0) && (buf.charAt(index) == ',')) {
            buf.deleteCharAt(index);
        }

        buf.append("]}\n");
        return buf.toString();
    }

    static String getJsonUrlList(String uri, String diskName)
            throws IllegalArgumentException, FileNotFoundException, NotDirectoryException, NotReadableException,
            IOException {
        // uri: http://localhost:8090/FileAccessService/api/fileAccessor/fileList/Videos01
        if ((diskName == null) || (diskName.length() == 0)) {
            throw new IllegalArgumentException("empty disk name");
        }
        String top = getDiskTopDirectory(diskName);
        File topFile = new File(top);
        if (!topFile.exists()) {
            throw new FileNotFoundException(topFile.toString());
        }
        if (!topFile.isDirectory()) {
            throw new NotDirectoryException(topFile.toString());
        }
        if (!topFile.canRead()) {
            throw new NotReadableException(topFile.toString());
        }
        Path topPath = topFile.toPath();
        StringBuilder buf = new StringBuilder();
        buf.append("{ \"disk\" : \"");
        buf.append(diskName);
        buf.append("\",\n");
        buf.append("  \"urls\" : [\n");
        Files.walk(topPath)
                .filter(path -> (Files.isRegularFile(path) && !path.endsWith(".DS_Store")))
                .forEach(path -> addFileUri(buf, uri, topPath.relativize(path)));

        // delete trailing ','
        int index = buf.length();
        while ((index > 0) && Character.isWhitespace(buf.charAt(--index))) {
            // do nothing
        }
        if ((index > 0) && (buf.charAt(index) == ',')) {
            buf.deleteCharAt(index);
        }

        buf.append("] }\n");
        return buf.toString();
    }

    private static void addFileUri(StringBuilder buf, String uri, Path path) {
        buf.append("    \"");
        buf.append(uri);
        buf.append('/');
        buf.append(FileAccessorRESTService.encode(path.toString()));
        buf.append("\",\n");
    }

    static String getFilePath(String diskName, String relativePath)
            throws IllegalArgumentException, DiskNotFoundException, FileNotFoundException, NotFileException,
                   NotReadableException, IOException {
        if ((diskName == null) || (diskName.length() == 0)) {
            throw new IllegalArgumentException("empty disk name");
        }
        if ((relativePath == null) || (relativePath.length() == 0)) {
            throw new IllegalArgumentException("empty relative path");
        }
        String top = getDiskTopDirectory(diskName);
        File file = new File(top, relativePath);
        if (!file.exists()) {
            throw new FileNotFoundException(file.toString());
        }
        if (!file.isFile()) {
            throw new NotFileException(file.toString());
        }
        if (!file.canRead()) {
            throw new NotReadableException(file.toString());
        }
        return file.toString();
    }

    static long copyFileToOutputStream(String file, OutputStream out) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            long count = 0;
            byte[] buffer = new byte[1024 * 1024];
            while (true) {
                int n = in.read(buffer);
                if (n < 0) {
                    return count;
                }
                count += n;
                out.write(buffer, 0, n);
            }
        }
    }

    static {
        try {
            getDiskTopDirectory(DEFAULT_DISK_NAME);
        } catch (Exception e) {
            // do nothing
        }
    }
}
