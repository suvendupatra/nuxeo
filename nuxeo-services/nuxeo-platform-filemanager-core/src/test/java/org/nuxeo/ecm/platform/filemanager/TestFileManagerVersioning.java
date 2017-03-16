/*
 * (C) Copyright 2006-2013 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thierry Martins
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.filemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(init = RepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.types.api", "org.nuxeo.ecm.platform.types.core",
        "org.nuxeo.ecm.platform.filemanager.core", "org.nuxeo.ecm.platform.dublincore",
        "org.nuxeo.ecm.platform.versioning.api", "org.nuxeo.ecm.platform.versioning" })
@LocalDeploy("org.nuxeo.ecm.platform.types.core:ecm-types-test-contrib.xml")
public class TestFileManagerVersioning {

    private static final String TEST_BUNDLE = "org.nuxeo.ecm.platform.filemanager.core.tests";

    private static final String INCORRECT_XML = "nxfilemanager-incorrect-versioning-contrib.xml";

    private static final String CONTRIB_XML = "nxfilemanager-versioning-contrib.xml";

    private static final String CONTRIB2_XML = "nxfilemanager-versioning2-contrib.xml";

    private static final String HELLO_DOC = "test-data/hello.doc";

    private static final String APPLICATION_MSWORD = "application/msword";

    protected FileManager service;

    @Inject
    protected CoreSession coreSession;

    protected DocumentModel root;


    @Before
    public void setUp() throws Exception {
        service = Framework.getLocalService(FileManager.class);
        root = coreSession.getRootDocument();
        // createWorkspaces();
    }

    @Test
    public void testDefaultVersioningOption() {
        assertEquals(VersioningOption.MINOR, service.getVersioningOption());
        assertFalse(service.doVersioningAfterAdd());
    }

    @Test
    @Deploy(TEST_BUNDLE+":"+INCORRECT_XML)
    public void testIncorrectVersioningOption() throws Exception {
        assertEquals(VersioningOption.MINOR, service.getVersioningOption());
        assertFalse(service.doVersioningAfterAdd());
    }

    @Test
    @Deploy(TEST_BUNDLE+":"+CONTRIB_XML)
    public void testCreateDocumentNoVersioningAfterAdd() throws Exception {
        assertEquals(VersioningOption.MAJOR, service.getVersioningOption());
        assertFalse(service.doVersioningAfterAdd());

        // create doc
        File file = getTestFile(HELLO_DOC);
        Blob input = Blobs.createBlob(file, APPLICATION_MSWORD);
        DocumentModel doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);
        DocumentRef docRef = doc.getRef();

        assertNotNull(doc.getPropertyValue("file:content"));
        assertTrue(doc.isCheckedOut());
        assertEquals(0, coreSession.getVersions(docRef).size());
        assertEquals("0.0", doc.getVersionLabel());

        // overwrite file
        doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);

        assertTrue(doc.isCheckedOut());
        assertEquals(1, coreSession.getVersions(docRef).size());
        assertEquals("1.0+", doc.getVersionLabel());

        // overwrite again
        doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);

        assertTrue(doc.isCheckedOut());
        assertEquals(2, coreSession.getVersions(docRef).size());
        assertEquals("2.0+", doc.getVersionLabel());
    }

    @Test
    @Deploy(TEST_BUNDLE+":"+CONTRIB2_XML)
    public void testCreateDocumentVersioningAfterAdd() throws Exception {
        assertEquals(VersioningOption.MINOR, service.getVersioningOption());
        assertTrue(service.doVersioningAfterAdd());

        // create doc
        File file = getTestFile(HELLO_DOC);
        Blob input = Blobs.createBlob(file, APPLICATION_MSWORD);
        DocumentModel doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);
        DocumentRef docRef = doc.getRef();

        assertNotNull(doc.getPropertyValue("file:content"));
        assertFalse(doc.isCheckedOut());
        assertEquals(1, coreSession.getVersions(docRef).size());
        assertEquals("0.1", doc.getVersionLabel());

        // overwrite file
        doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);

        assertFalse(doc.isCheckedOut());
        assertEquals(2, coreSession.getVersions(docRef).size());
        assertEquals("0.2", doc.getVersionLabel());

        // overwrite again
        doc = service.createDocumentFromBlob(coreSession, input, root.getPathAsString(), true, HELLO_DOC);

        assertFalse(doc.isCheckedOut());
        assertEquals(3, coreSession.getVersions(docRef).size());
        assertEquals("0.3", doc.getVersionLabel());
    }

    protected File getTestFile(String relativePath) {
        return new File(FileUtils.getResourcePathFromContext(relativePath));
    }

}
