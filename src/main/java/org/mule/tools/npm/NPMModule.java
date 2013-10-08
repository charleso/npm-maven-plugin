/**
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.tools.npm;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.mule.tools.npm.version.VersionResolver;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NPMModule {

    private String name;
    public String version;
    private Log log;
    private List<NPMModule> dependencies;
    private URL downloadURL;
    private String npmURL;
    private Proxy proxy;
    
    public String getName() {
        return name;
    }

    public String getVerion() {
        return version;
    }

    public List<NPMModule> getDependencies() {
        return dependencies;
    }

    public void saveToFileWithDependencies(File file) throws MojoExecutionException {
        this.saveToFile(file);

        for (NPMModule dependency : dependencies) {
            dependency.saveToFileWithDependencies(file);
        }
    }

    private static InputStream getInputStreamFromUrl(final URL url,
        final Proxy proxy) throws IOException {

        URLConnection conn = null;
        if (proxy != null) {
            final String proxyUser = proxy.getUsername();
            final String proxyPassword = proxy.getPassword();
            final String proxyAddress = proxy.getHost();
            final int proxyPort = proxy.getPort();

            java.net.Proxy.Type proxyProtocol = java.net.Proxy.Type.DIRECT;
            if (proxy.getProtocol() != null && proxy.getProtocol().equalsIgnoreCase("HTTP")) {
                proxyProtocol = java.net.Proxy.Type.HTTP;
            } else if (proxy.getProtocol() != null && proxy.getProtocol().equalsIgnoreCase("SOCKS")) {
                proxyProtocol = java.net.Proxy.Type.SOCKS;
            }

            final InetSocketAddress sa = new InetSocketAddress(proxyAddress, proxyPort);
            final java.net.Proxy jproxy = new java.net.Proxy(proxyProtocol, sa);
            conn = url.openConnection(jproxy);

            if (proxyUser != null && proxyUser != "") {
                @SuppressWarnings("restriction")
                final sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
                @SuppressWarnings("restriction")
                final String encodedUserPwd = encoder.encode((proxyUser + ":" + proxyPassword).getBytes());
                conn.setRequestProperty("Proxy-Authorization", "Basic " + encodedUserPwd);
            }
        } else {
            conn = url.openConnection();
        }
        return conn.getInputStream();
    }

    private static String loadTextFromUrl(final URL url, final Proxy proxy)
        throws IOException {
        return IOUtils.toString(getInputStreamFromUrl(url, proxy));
    }

    public void saveToFile(File file) throws MojoExecutionException {
        URL dl;
        OutputStream os = null;
        InputStream is = null;
        File outputFolderFileTmp = new File(file, name + "_tmp");
        File outputFolderFile = new File(file, name);

        if ( outputFolderFile.exists() ) {
            //Already downloaded nothing to do
            return;
        }


        outputFolderFileTmp.mkdirs();

        File tarFile = new File(outputFolderFileTmp, name + "-" + version + ".tgz");
        ProgressListener progressListener = new ProgressListener(log);
        log.debug("Downloading " + this.name + ":" + this.version);

        try {
            os = new FileOutputStream(tarFile);
            is = getDownloadURL().openStream();

            DownloadCountingOutputStream dcount = new DownloadCountingOutputStream(os);
            dcount.setListener(progressListener);

            getDownloadURL().openConnection().getHeaderField("Content-Length");

            IOUtils.copy(is, dcount);

        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(String.format("Error downloading module %s:%s", name,version),e);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Error downloading module %s:%s", name,version),e);
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }

        final TarGZipUnArchiver ua = new TarGZipUnArchiver();
        ua.enableLogging(new LoggerAdapter(log));
        ua.setSourceFile(tarFile);
        ua.setDestDirectory(outputFolderFileTmp);
        ua.extract();

        FileUtils.deleteQuietly(tarFile);


        File fileToMove;

        File[] files = outputFolderFileTmp.listFiles();
        if (files != null && files.length == 1) {
            fileToMove = files[0];

        } else {
            File aPackage = new File(outputFolderFileTmp, "package");
            if (aPackage.exists() && aPackage.isDirectory()) {
                fileToMove = aPackage;
            } else {
                throw new MojoExecutionException(String.format(
                        "Only one file should be present at the folder when " +
                        "unpacking module %s:%s: ", name, version));
            }
        }

        try {
            FileUtils.moveDirectory(fileToMove, outputFolderFile);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Error moving to the final folder when " +
                    "unpacking module %s:%s: ", name, version),e);
        }

        try {
            FileUtils.deleteDirectory(outputFolderFileTmp);
        } catch (IOException e) {
            log.info("Error while deleting temporary folder: " + outputFolderFileTmp, e);
        }

    }

    private void downloadDependencies(Map dependenciesMap) throws IOException, MojoExecutionException {
        for (Object dependencyAsObject :dependenciesMap.entrySet()){
            Map.Entry dependency = (Map.Entry) dependencyAsObject;
            String dependencyName = (String) dependency.getKey();

            String version = ((String) dependency.getValue());

            try {
                version = new VersionResolver(npmURL).getNextVersion(log, proxy, dependencyName, version);
                dependencies.add(fromNameAndVersion(npmURL, proxy, log, dependencyName, version));
            } catch (Exception e) {
                throw new RuntimeException("Error resolving dependency: " +
                        dependencyName + ":" + version + " not found.", e);
            }

        }
    }

    public static Set downloadMetadataList(String npmURL, Proxy proxy, String name) throws IOException, JsonParseException {
        URL dl = new URL(String.format(npmURL,name,""));
        ObjectMapper objectMapper = new ObjectMapper();
        Map allVersionsMetadata = objectMapper.readValue(loadTextFromUrl(dl, proxy),Map.class);
        return ((Map) allVersionsMetadata.get("versions")).keySet();
    }

    private Map downloadMetadata(String name, String version) throws IOException, JsonParseException {
        return downloadMetadata(new URL(String.format(npmURL,name,version != null ? version : "latest")), proxy);
    }

    public static Map downloadMetadata(URL dl, Proxy proxy) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(loadTextFromUrl(dl, proxy), Map.class);
        } catch (IOException e) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e1) {
            }
            return objectMapper.readValue(loadTextFromUrl(dl, proxy), Map.class);
        }
    }

    private void downloadModule() throws MojoExecutionException {

        try {
            Map jsonMap = downloadMetadata(name,version);

            Map distMap = (Map) jsonMap.get("dist");
            this.downloadURL = new URL((String) distMap.get("tarball"));
            this.version = (String) jsonMap.get("version");

            Map dependenciesMap = (Map) jsonMap.get("dependencies");

            if (dependenciesMap != null) {
                downloadDependencies(dependenciesMap);
            }

        } catch (MalformedURLException e) {
            throw new MojoExecutionException(String.format("Error downloading module info %s:%s", name,version),e);
        } catch (JsonMappingException e) {
            throw new MojoExecutionException(String.format("Error downloading module info %s:%s", name,version),e);
        } catch (JsonParseException e) {
            throw new MojoExecutionException(String.format("Error downloading module info %s:%s", name,version),e);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Error downloading module info %s:%s", name,version),e);
        }
    }

    private NPMModule(String npmURL, Proxy proxy) {
        this.npmURL = npmURL;
        this.proxy = proxy;
    }

    public static NPMModule fromQueryString(String npmURL, Proxy proxy, Log log, String nameAndVersion) throws MojoExecutionException {
        String[] splitNameAndVersion = nameAndVersion.split(":");
        return fromNameAndVersion(npmURL, proxy, log, splitNameAndVersion[0], splitNameAndVersion[1]);
    }

    public static NPMModule fromNameAndVersion(String npmUrl, Proxy proxy, Log log, String name, String version)
            throws IllegalArgumentException,
            MojoExecutionException {
        NPMModule module = new NPMModule(npmUrl, proxy);
        module.log = log;
        module.name = name;

        if ("*".equals(version)) {
            throw new IllegalArgumentException();
        }

        module.version = version;
        module.dependencies = new ArrayList<NPMModule>();
        module.downloadModule();
        return module;
    }

    public URL getDownloadURL() {
        return downloadURL;
    }

    public static NPMModule fromName(String npmURL, Proxy proxy, Log log, String name) throws MojoExecutionException {
        return fromNameAndVersion(npmURL, proxy, log, name, null);
    }

}
