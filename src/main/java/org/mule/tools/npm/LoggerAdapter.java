/**
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.tools.npm;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;

public class LoggerAdapter implements Logger {
    private Log log;
    private int threshold;

    public LoggerAdapter(Log log) {
        this.log = log;
    }
    @Override
    public void debug(String message) {
        log.debug(message);
    }

    @Override
    public void debug(String message, Throwable throwable) {
        log.debug(message,throwable);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        log.info(message,throwable);
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        log.warn(message,throwable);
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public void error(String message) {
        log.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        log.error(message,throwable);
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public void fatalError(String message) {
        log.error(message);
    }

    @Override
    public void fatalError(String message, Throwable throwable) {
        log.error(message,throwable);
    }

    @Override
    public boolean isFatalErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public int getThreshold() {
        return threshold;
    }

    // Maven 3.x only
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public Logger getChildLogger(String name) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}
