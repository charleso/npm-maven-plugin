/**
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.tools.npm.version;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;
import org.mule.tools.npm.NPMModule;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionResolver {

    private static final String VERSION_WITH_SIGN_REGEX =
            "(?:(~|=|>=|<|<=)?\\s*((?:\\d|x)+(?:\\.(?:\\d|x)+)?(?:\\.(?:\\d|x)+)?(?:rc\\d+)?)\\s*)";
    public static final String VERSION_REGEX = "(?:(\\*)|" +
            VERSION_WITH_SIGN_REGEX + VERSION_WITH_SIGN_REGEX + "?)";

    private final String npmUrl;

    public VersionResolver(String npmUrl) {
        this.npmUrl = npmUrl;
    }

    private String removeXFromVersion(String leftVersion) {
        int xPosition = leftVersion.indexOf("x");
        if ( xPosition != -1 ) {
            leftVersion = leftVersion.substring(0,xPosition);
        }
        return leftVersion;
    }

    public String getNextVersion(Log log, Proxy proxy, String dependencyName, String versionRange)
            throws IOException, MojoExecutionException {
        Pattern pattern = Pattern.compile(VERSION_REGEX);
        Matcher matcher = pattern.matcher(versionRange);
        Condition condition;

        if (!matcher.matches()) {
            throw new IllegalArgumentException("No valid version found in range: " +
                    versionRange + " of " + dependencyName );
        }

        Set set = NPMModule.downloadMetadataList(npmUrl, proxy, dependencyName);

        /* Case where '*' */
        if ("*".equals(matcher.group())) {
            return (String) (set.toArray()[0]);

        /* Case where '>2.3.1' (no second version range) */
        } else if(matcher.group(4) == null) {
            String modifier = matcher.group(2);
            String version = removeXFromVersion(matcher.group(3));

            condition = new SimpleCondition(modifier,version);

        /* Complex conditions >=1.2.3 <1.2.2 */
        } else {
            String leftModifier = matcher.group(2);
            String leftVersion = removeXFromVersion(matcher.group(3));

            String rightModifier = matcher.group(4);
            String rightVersion = removeXFromVersion(matcher.group(5));

            condition = new ComplexCondition(new SimpleCondition(leftModifier,leftVersion),
                    new SimpleCondition(rightModifier,rightVersion));
        }

        for (Object o : set) {
            String dependencyVersion = (String) o;
            if ( condition.satisfies(dependencyVersion) ) {
                return dependencyVersion;
            }
        }

        throw new IllegalArgumentException("No valid version found in range: " +
                versionRange + " of " + dependencyName );

    }
}
