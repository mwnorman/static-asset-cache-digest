package com.initlive.tool;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PREPARE_PACKAGE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generate", defaultPhase = PREPARE_PACKAGE)
public class AssetDigestMojo extends AbstractMojo {

    /**
     * digest
     */
    @Parameter(defaultValue = "MD5")
    protected String digestAlgorithm;

    /**
     * contextPath
     */
    @Parameter(required = true)
    protected String contextPath;

    /**
     * source directory
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp", required = true)
    protected String sourceDirectory;

    /**
     * target directory
     */
    @Parameter(defaultValue = "${project.build.directory}/webapp-digest", required = true)
    protected String targetDirectory;

    /**
     * excluded directories (relative to contextPath)
     */
    @Parameter
    protected List<String> skipDirs;

    /**
     * specified file types (by extensions)
     */
    @Parameter
    protected List<String> fileExtensions;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path sourcePath = Paths.get(sourceDirectory);
        if (!Files.isDirectory(sourcePath)) {
            throw new MojoExecutionException("source directory is not a directory: " + sourceDirectory);
        }
        Path targetPath = Paths.get(targetDirectory);
        if (Files.notExists(targetPath)) {
            //try to create
            try {
                Files.createDirectory(targetPath);
            }
            catch (Exception e) {
                throw new MojoExecutionException("unable to create target directory: " + targetDirectory, e);
            }
        }
        if (fileExtensions.isEmpty()) {
            getLog().info("no file types specified");
            return;
        }
        MessageDigest digest = getDigest(digestAlgorithm);
        if (digest == null) {
            throw new MojoExecutionException("problem with " + digestAlgorithm);
        }

        Map<String, String> filenameManifest = null;
        try {
            filenameManifest = walkFileTree(sourcePath, targetPath, contextPath, digest, fileExtensions, skipDirs, getLog());
            //TODO use JSoup to find all the places in index.html that needs altering
            Map<String, String> sortedMap = new TreeMap<>(filenameManifest);
            for (Entry<String, String> entry : sortedMap.entrySet()) {
                getLog().info("mapped " + entry.getKey() + " to " + entry.getValue());
            }
        }
        catch (Exception e) {
            throw new MojoExecutionException("problem walking source directory", e);
        }
    }

    protected Map<String, String> walkFileTree(Path sourcePath, Path targetPath, String contextPath, MessageDigest digest, List<String> fileExtensions, List<String> skipDirs, Log mavenLog) throws IOException {
        RenameWithMD5HashFilenameVisitor filenameVisitor =
            new RenameWithMD5HashFilenameVisitor(sourcePath, targetPath, contextPath, digest, fileExtensions, skipDirs, mavenLog);
        Files.walkFileTree(sourcePath, filenameVisitor);
        return filenameVisitor.getFilenameManifest();
    }

    protected MessageDigest getDigest(String digestAlg) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(digestAlg);
        }
        catch (NoSuchAlgorithmException ex) {
            getLog().error("cannot file digest algorithm " + digestAlg, ex);
        }
        return digest;
    }

    public static void main(String ...args) {
        AssetDigestMojo mojo = new AssetDigestMojo();
        mojo.digestAlgorithm = "MD5";
        mojo.contextPath = "web-admin/app";
        mojo.sourceDirectory = "/Users/mwnorman/git/initlive-server/src/main/webapp";
        mojo.targetDirectory = "/Users/mwnorman/git/initlive-server/target/webapp-digest";
        mojo.fileExtensions = new ArrayList<String>(Arrays.asList("js", "css", "png", "svg", "jpg", "gif", "jpeg"));
        mojo.skipDirs = new ArrayList<String>(Arrays.asList(
            "css/fonts", "files", "fonts", "js/libs", "less", "lib", "lib/angular", "lib/angular/i18n", "lib/angular-1-5", "lib/angular-1-5/i18n"));
        try {
            mojo.execute();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
