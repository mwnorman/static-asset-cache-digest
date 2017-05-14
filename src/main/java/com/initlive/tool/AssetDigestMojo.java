package com.initlive.tool;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PREPARE_PACKAGE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.select.Elements;

@Mojo(name = "generate", defaultPhase = PREPARE_PACKAGE)
public class AssetDigestMojo extends AbstractMojo {

    static final String DEFAULT_DIGEST_ALG = "MD5";
    static final String DEFAULT_SOURCE_DIRECTORY = "${basedir}/src/main/webapp";
    static final String DEFAULT_TARGET_DIRECTORY = "${project.build.directory}/static-assets";

    //static-assets

    /**
     * digest
     */
    @Parameter(defaultValue = DEFAULT_DIGEST_ALG)
    protected String digestAlgorithm;

    /**
     * contextPath
     */
    @Parameter(required = true)
    protected String contextPath;

    /**
     * source directory
     */
    @Parameter(defaultValue = DEFAULT_SOURCE_DIRECTORY, required = true)
    protected String sourceDirectory;

    /**
     * target directory
     */
    @Parameter(defaultValue = DEFAULT_TARGET_DIRECTORY, required = true)
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

    /**
     * specified files to be rewritten
     */
    @Parameter(required = true)
    protected List<String> rewriteFiles;

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
            Map<String, String> sortedMap = new TreeMap<>(filenameManifest);
            for (Entry<String, String> entry : sortedMap.entrySet()) {
                getLog().info("mapped " + entry.getKey() + " to " + entry.getValue());
            }
            for (String rewriteFile : rewriteFiles) {
                Path rewriteFilePath = Paths.get(rewriteFile);
                if (Files.notExists(rewriteFilePath)) {
                    getLog().debug(rewriteFilePath.toString() + "does not exist" );
                }
                else {
                    Document rewriteDoc = Jsoup.parse(rewriteFilePath.toFile(), US_ASCII.name());
                    //first do <link rel="stylesheet" href= ...>
                    Elements linkHrefs = rewriteDoc.select("link[href]");
                    for (Element linkHref : linkHrefs) {
                        String hrefAttr = linkHref.attr("href");
                        if (hrefAttr != null && !hrefAttr.isEmpty()) {
                            String renamedRef = filenameManifest.get(hrefAttr);
                            if (renamedRef != null && !hrefAttr.isEmpty()) {
                                linkHref.attr("href", renamedRef);
                            }
                        }
                    }
                    //next do <script src= ...>
                    Elements scriptSrcs = rewriteDoc.select("script[src]");
                    for (Element scriptSrc : scriptSrcs) {
                        String srcAttr = scriptSrc.attr("src");
                        if (srcAttr != null && !srcAttr.isEmpty()) {
                            String renamedRef = filenameManifest.get(srcAttr);
                            if (renamedRef != null && !srcAttr.isEmpty()) {
                                scriptSrc.attr("src", renamedRef);
                            }
                        }
                    }
                    //next do <img src= ...>
                    Elements imgSrcs = rewriteDoc.select("img[src]");
                    for (Element imgSrc : imgSrcs) {
                        String srcAttr = imgSrc.attr("src");
                        if (srcAttr != null && !srcAttr.isEmpty()) {
                            String renamedRef = filenameManifest.get(srcAttr);
                            if (renamedRef != null && !srcAttr.isEmpty()) {
                                imgSrc.attr("src", renamedRef);
                            }
                        }
                    }
                    rewriteDoc.outputSettings().prettyPrint(false).escapeMode(EscapeMode.extended);
                    Files.write(rewriteFilePath, rewriteDoc.outerHtml().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
                }
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
        //TODO - so far only tested MD5, should test others
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(digestAlg);
        }
        catch (NoSuchAlgorithmException ex) {
            getLog().error("cannot file digest algorithm " + digestAlg, ex);
        }
        return digest;
    }
}
