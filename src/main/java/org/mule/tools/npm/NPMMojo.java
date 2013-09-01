/**
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.tools.npm;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Goal that offers Recess support in Maven builds.
 *
 * @goal fetch-modules
 * @phase generate-sources
 */
public class NPMMojo extends AbstractJavascriptMojo {

    public static String NPM_URL = "http://registry.npmjs.org/%s/%s";

    /**
     * Where the resulting files will be downloaded.
     *
     * @parameter expression="${recess.outputDirectory}" default-value="${basedir}/src/main/resources/META-INF"
     */
    private File outputDirectory;

    /**
     * Package file which declares the dependencies.
     *
     * @parameter expression="${recess.inputFile}" default-value="${basedir}/package.json"
     */
    private File inputFile;

    /**
     * Override URL to download packages from.
     *
     * @parameter expression="${recess.registryUrl}" default-value="http://registry.npmjs.org/%s/%s"
     */
    private String registryUrl;

    public void execute() throws MojoExecutionException {
        Log log = getLog();

        Map<String, String> dependencies;
        try {
            dependencies = (Map<String, String>) NPMModule.downloadMetadata(inputFile.toURI().toURL()).get("dependencies");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not open " + inputFile, e);
        }

        for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
            NPMModule.fromNameAndVersion(registryUrl,log,dependency.getKey(),dependency.getValue()).saveToFileWithDependencies(outputDirectory);
        }
    }
}
