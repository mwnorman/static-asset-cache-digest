package com.initlive.tool;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

public class RenameWithMD5HashFilenameVisitor extends SimpleFileVisitor<Path> {

    static final String FILEPATH_MATCHER_GLOB_PREFIX = "glob:**.{";
    static final String DIRPATH_MATCHER_GLOB_PREFIX = "glob:**/{";

    protected Path sourcePath;
    protected Path targetPath;
    protected String contextPath;
    protected MessageDigest digest;
    protected List<String> fileExtensions;
    protected List<String> skipDirs;
    protected PathMatcher fileTypeMatcher = null;
    protected Log mavenLog = null;
    protected Map<String, String> filenameManifest = new HashMap<>();

    public RenameWithMD5HashFilenameVisitor(Path sourcePath, Path targetPath, String contextPath, MessageDigest digest, List<String> fileExtensions,
        List<String> skipDirs, Log mavenLog) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.contextPath = contextPath;
        this.digest = digest;
        this.fileExtensions = fileExtensions;
        this.skipDirs = skipDirs;
        this.mavenLog = mavenLog;
        StringBuilder sb = new StringBuilder(FILEPATH_MATCHER_GLOB_PREFIX);
        String csFileExtensions = asCommaSeparatedString(fileExtensions);
        sb.append(csFileExtensions).append("}");
        fileTypeMatcher = FileSystems.getDefault().getPathMatcher(sb.toString());
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        Path newdir = targetPath.resolve(sourcePath.relativize(dir));
        try {
            //mavenLog.debug("creating " + newdir.toString());
            Files.copy(dir, newdir, StandardCopyOption.COPY_ATTRIBUTES);
        }
        catch (FileAlreadyExistsException faee) {
            // ignore
        }
        catch (IOException ioe) {
            mavenLog.error(ioe.getLocalizedMessage());
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path visitedFile, BasicFileAttributes attrs) throws IOException {
        boolean rename = true;
        String visitedFileFullname = visitedFile.toString();
        String visitedFileSimplename = visitedFile.getFileName().toString();
        String newFilename = visitedFileSimplename;
        String sourcePathStr = sourcePath.toString();
        String subPath = visitedFileFullname.substring(visitedFileFullname.indexOf(sourcePathStr) + sourcePathStr.length(),
            visitedFileFullname.lastIndexOf("/") + 1);
        Path newFile = Paths.get(targetPath + subPath + visitedFileSimplename);
        /* check for all the reasons to NOT rename the file:
         *   - is not one of the asset file types
         *   - in an excluded directory
         */
        if (!fileTypeMatcher.matches(visitedFile)) {
            rename = false;
        }
        else {
            for (String skipDir : skipDirs) {
                StringBuilder contextSkipBuilder = new StringBuilder(sourcePathStr);
                if (!sourcePathStr.endsWith("/")) {
                    contextSkipBuilder.append("/");
                }
                contextSkipBuilder.append(contextPath);
                if (!contextPath.endsWith("/")) {
                    contextSkipBuilder.append("/");
                }
                contextSkipBuilder.append(skipDir);
                Path contextSkipPath = Paths.get(contextSkipBuilder.toString());
                if (isChild(visitedFile, contextSkipPath)) {
                    rename = false;
                    break;
                }
            }
        }
        if (rename) {
            InputStream fis =  new FileInputStream(visitedFile.toFile());
            byte[] buffer = new byte[1024];
            int numRead;
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    digest.update(buffer, 0, numRead);
                }
            }
            while (numRead != -1);
            fis.close();
            byte[] hash = digest.digest();
            digest.reset();
            String hashHexstring = String.format("%032x", new BigInteger(1, hash));
            String basename = visitedFileSimplename;
            String extension = "";
            int extensionIdx = visitedFileSimplename.lastIndexOf(".");
            if (extensionIdx != -1) {
                basename = visitedFileSimplename.substring(0, extensionIdx);
                extension = visitedFileSimplename.substring(extensionIdx + 1);
            }
            newFilename = new StringBuilder(basename)
                .append(".")
                .append(hashHexstring)
                .append(".")
                .append(extension)
            .toString();
            newFile = Paths.get(targetPath + subPath + newFilename);
            String contextSubPath = subPath.substring(subPath.indexOf(contextPath) + contextPath.length() + 1);
            filenameManifest.put(contextSubPath + visitedFileSimplename, contextSubPath + newFilename);
        }
        //mavenLog.debug("copy " + visitedFile.toString() +  " to " + newFile.toString());
        Files.copy(visitedFile, newFile, REPLACE_EXISTING, COPY_ATTRIBUTES);
        return CONTINUE;
    }

    public Map<String, String> getFilenameManifest() {
        return filenameManifest;
    }

    protected boolean isChild(Path childFile, Path excludeDir) {
        boolean isChild = false;
        Path childFileParent = childFile.getParent();
        if (childFileParent.endsWith(excludeDir)) {
            isChild = true;
            //mavenLog.debug("excluding " +  childFile.toString() + " (under " + excludeDir.toString() + ")");
        }
        return isChild;
    }

    protected String asCommaSeparatedString(List<String> stringArray) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (String s : stringArray) {
            sb.append(sep);
            sb.append(s);
            sep = ",";
        }
        return sb.toString();
    }
}