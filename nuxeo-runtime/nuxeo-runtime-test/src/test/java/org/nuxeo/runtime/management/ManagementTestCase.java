/*
 * (C) Copyright 2006-2008 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     matic
 */
package org.nuxeo.runtime.management;

import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

/**
 * @author matic
 */
public abstract class ManagementTestCase extends NXRuntimeTestCase {

    protected static final String OSGI_BUNDLE_NAME = "org.nuxeo.runtime.management";

    protected ResourcePublisherService publisherService;

    protected ServerLocatorService locatorService;

    @Override
    public void setUp() throws Exception {
        deployContrib(OSGI_BUNDLE_NAME, "OSGI-INF/management-server-locator-service.xml");
        deployContrib(OSGI_BUNDLE_NAME, "OSGI-INF/management-resource-publisher-service.xml");
        deployTestContrib(OSGI_BUNDLE_NAME, "isolated-server.xml");
    }

    @Override
    protected void postSetUp() throws Exception {
        locatorService = (ServerLocatorService) Framework.getLocalService(ServerLocator.class);
        publisherService = (ResourcePublisherService) Framework.getLocalService(ResourcePublisher.class);
    }

    protected void doBindResources() throws InstanceNotFoundException, ReflectionException, MBeanException {
        String qualifiedName = ObjectNameFactory.formatQualifiedName(ResourcePublisherService.NAME);
        ObjectName objectName = ObjectNameFactory.getObjectName(qualifiedName);
        MBeanServer server = locatorService.lookupServer(objectName.getDomain());
        server.invoke(objectName, "bindResources", null, null);
    }

    protected Set<ObjectName> doQuery(String name) {
        String qualifiedName = ObjectNameFactory.getQualifiedName(name);
        ObjectName objectName = ObjectNameFactory.getObjectName(qualifiedName);
        MBeanServer server = locatorService.lookupServer(objectName.getDomain());
        return server.queryNames(objectName, null);
    }

}
