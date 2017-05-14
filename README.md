# static-asset-cache-digest
Maven plugin to clone a directory and rename its static assets to include a digest hash

This plugin performs a single transformation: walk the *source* directory tree, cloning to
the *target* directory. Along the way, for the identified file types (identified by extension)
calculate a digest-hash of the file's content and rename the file to include the hash-value.

Configuration
=============

  * Configure plugin in pom.xml:

```xml
<plugin>
    <groupId>com.initlive.tool</groupId>
    <artifactId>asset-digest</artifactId>
    <version>0.0.1</version>
    <executions>
        <execution>
            <phase>prepare-package</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
      <!-- [Standard JavaSE MessageDigest names](goo.gl/NYpyk3) -->
      <digestAlgorithm>MD5</digestAlgorithm>
      <!-- default sourceDirectory -->
      <sourceDirectory>${basedir}/src/main/webapp<sourceDirectory>
      <!-- default targetDirectory -->
      <targetDirectory>${project.build.directory}/static-assets</targetDirectory>
      <contextPath>web-admin/app</contextPath
      <skipDirs>
          <!-- relative to contextPath if specified; otherwise rel to sourceDirectory -->
          <skipDir>css/fonts</skipDir>
          <skipDir>fonts</skipDir>
      </skipDirs>
      <fileExtensions>
          <fileExtension>js</fileExtension>
          <fileExtension>css</fileExtension>
          <fileExtension>png</fileExtension>
      </fileExtensions>
      <rewriteFiles>
          <rewriteFile>${project.build.directory}/static-assets/web-admin/app/index.html</rewriteFile>
      </rewriteFiles>
    </configuration>
</plugin>
```
