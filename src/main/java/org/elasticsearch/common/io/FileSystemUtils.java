/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.io;

import org.apache.lucene.util.Constants;
import org.apache.lucene.util.ThreadInterruptedException;
import org.elasticsearch.Version;
import org.elasticsearch.common.logging.ESLogger;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 *
 */
public class FileSystemUtils {

    public static boolean mkdirs(File dir) {
        return dir.mkdirs();
    }

    public static boolean hasExtensions(File root, String... extensions) {
        if (root != null && root.exists()) {
            if (root.isDirectory()) {
                File[] children = root.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory()) {
                            boolean has = hasExtensions(child, extensions);
                            if (has) {
                                return true;
                            }
                        } else {
                            for (String extension : extensions) {
                                if (child.getName().endsWith(extension)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if at least one of the files exists.
     */
    public static boolean exists(File... files) {
        for (File file : files) {
            if (file.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes the given files recursively. if <tt>deleteRoots</tt> is set to <code>true</code>
     * the given root files will be deleted as well. Otherwise only their content is deleted.
     */
    public static boolean deleteRecursively(File[] roots, boolean deleteRoots) {
        boolean deleted = true;
        for (File root : roots) {
            deleted &= deleteRecursively(root, deleteRoots);
        }
        return deleted;
    }

    /**
     * Deletes the given files recursively including the given roots.
     */
    public static boolean deleteRecursively(File... roots) {
       return deleteRecursively(roots, true);
    }

    /**
     * Delete the supplied {@link java.io.File} - for directories,
     * recursively delete any nested directories or files as well.
     *
     * @param root       the root <code>File</code> to delete
     * @param deleteRoot whether or not to delete the root itself or just the content of the root.
     * @return <code>true</code> if the <code>File</code> was deleted,
     *         otherwise <code>false</code>
     */
    public static boolean deleteRecursively(File root, boolean deleteRoot) {
        if (root != null && root.exists()) {
            if (root.isDirectory()) {
                File[] children = root.listFiles();
                if (children != null) {
                    for (File aChildren : children) {
                        deleteRecursively(aChildren, true);
                    }
                }
            }

            if (deleteRoot) {
                return root.delete();
            } else {
                return true;
            }
        }
        return false;
    }

    static {
        assert Version.CURRENT.luceneVersion == org.apache.lucene.util.Version.LUCENE_47 : "Use IOUtils#fsync instead of syncFile in Lucene 4.8";
    }

    /**
     * Ensure that any writes to the given file is written to the storage device that contains it.
     * @param fileToSync the file to fsync
     * @param isDir if true, the given file is a directory (we open for read and ignore IOExceptions,
     *  because not all file systems and operating systems allow to fsync on a directory)
     */
    public static void syncFile(File fileToSync, boolean isDir) throws IOException {
        IOException exc = null;

        // If the file is a directory we have to open read-only, for regular files we must open r/w for the fsync to have an effect.
        // See http://blog.httrack.com/blog/2013/11/15/everything-you-always-wanted-to-know-about-fsync/
        try (final FileChannel file = FileChannel.open(fileToSync.toPath(), isDir ? StandardOpenOption.READ : StandardOpenOption.WRITE)) {
            for (int retry = 0; retry < 5; retry++) {
                try {
                    file.force(true);
                    return;
                } catch (IOException ioe) {
                    if (exc == null) {
                        exc = ioe;
                    }
                    try {
                        // Pause 5 msec
                        Thread.sleep(5L);
                    } catch (InterruptedException ie) {
                        ThreadInterruptedException ex = new ThreadInterruptedException(ie);
                        ex.addSuppressed(exc);
                        throw ex;
                    }
                }
            }
        } catch (IOException ioe) {
            if (exc == null) {
                exc = ioe;
            }
        }

        if (isDir) {
            assert (Constants.LINUX || Constants.MAC_OS_X) == false :
                    "On Linux and MacOSX fsyncing a directory should not throw IOException, "+
                            "we just don't want to rely on that in production (undocumented). Got: " + exc;
            // Ignore exception if it is a directory
            return;
        }

        // Throw original exception
        throw exc;
    }

    /**
     * Check that a directory exists, is a directory and is readable
     * by the current user
     */
    public static boolean isAccessibleDirectory(File directory, ESLogger logger) {
        assert directory != null && logger != null;

        if (!directory.exists()) {
            logger.debug("[{}] directory does not exist.", directory.getAbsolutePath());
            return false;
        }
        if (!directory.isDirectory()) {
            logger.debug("[{}] should be a directory but is not.", directory.getAbsolutePath());
            return false;
        }
        if (!directory.canRead()) {
            logger.debug("[{}] directory is not readable.", directory.getAbsolutePath());
            return false;
        }
        return true;
    }

    private FileSystemUtils() {}

    public static void tryDeleteFile(File file) {
        try {
            file.delete();
        } catch (SecurityException e1) {
            // ignore
        }
    }
}
