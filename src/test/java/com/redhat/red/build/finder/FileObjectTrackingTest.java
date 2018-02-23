/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.build.finder;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
// Uncomment this to get byteman logging!
// @BMUnitConfig(bmunitVerbose=true, debug=true, verbose = true)
public class FileObjectTrackingTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    @BMRules(rules = {
        // DefaultFileSystemManager / StandardFileSystemManager
        @BMRule(name = "track-filesystem-create",
            targetClass = "DefaultFileSystemManager",
            targetMethod = "<init>",
            targetLocation = "AT ENTRY",
            action = "link(\"fileSystemManagerCounter\", incrementCounter(\"DefaultFileSystemManager\")) "
//                        + "; traceStack() ; System.out.println (\"init: \" + $0.getClass().getName() + \" and \" + $0.hashCode()); "
        ),
        @BMRule(name = "track-filesystem-close",
            targetClass = "DefaultFileSystemManager",
            targetMethod = "close",
            targetLocation = "AT ENTRY",
            action = "link(\"fileSystemManagerCounter\", decrementCounter(\"DefaultFileSystemManager\"))"
//                            + " ; System.out.println (\"close: \" + $0.getClass().getName() + \" and linked \" + linked(\"fileSystemManagerCounter\") ); "
        ),
        @BMRule(name = "pass-file-info",
            targetClass = "FileObjectTrackingTest",
            targetMethod = "getFileSystemCounter",
            targetLocation = "AT ENTRY",
            action = "return linked(\"fileSystemManagerCounter\")"
        ),
        // FileObject
        @BMRule(name = "track-file-object-create",
            targetClass = "AbstractFileObject",
            targetMethod = "<init>",
            targetLocation = "AT ENTRY",
            // Ignore LocalFiles as they are used for the temporary file cache.
            condition = "$0.getClass().getName() != \"org.apache.commons.vfs2.provider.local.LocalFile\" ",
            action = "link(\"fileObjectCounter\", incrementCounter(\"AbstractFileObject\")) "
        ),
        @BMRule(name = "track-file-object-close",
            targetClass = "AbstractFileObject",
            targetMethod = "close",
            targetLocation = "AT ENTRY",
            // Ignore LocalFiles as they are used for the temporary file cache.
            condition = " $0.getClass().getName() != \"org.apache.commons.vfs2.provider.local.LocalFile\" ",
            action = "link(\"fileObjectCounter\", decrementCounter(\"AbstractFileObject\"))"
        ),
        @BMRule(name = "pass-object-info",
            targetClass = "FileObjectTrackingTest",
            targetMethod = "getAbstractFileObjectCounter",
            targetLocation = "AT ENTRY",
            action = "return linked(\"fileObjectCounter\")"
        )
    })
    public void verifyObjectCreation() throws IOException {
        File cache = temp.newFolder();
        System.setProperty("java.io.tmpdir", cache.getAbsolutePath());

        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        DistributionAnalyzer da = new DistributionAnalyzer(target, KojiChecksumType.md5.getAlgorithm());
        da.checksumFiles();

        Object fCounter = getAbstractFileObjectCounter();
        Object sCounter = getFileSystemCounter();
        assertTrue(fCounter instanceof Integer && ((Integer) fCounter) == 0);
        assertTrue(sCounter instanceof Integer && ((Integer) sCounter) == 0);
    }

    /**
     * Byteman modifies this method to return the actual counter value.
     *
     * @return Integer counter
     */
    private Object getAbstractFileObjectCounter() {
        return null;
    }

    /**
     * Byteman modifies this method to return the actual counter value.
     *
     * @return Integer counter
     */
    private Object getFileSystemCounter() {
        return null;
    }
}
