/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.runtime.model.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.model.StreamRef;
import org.nuxeo.runtime.model.URLStreamRef;
import org.nuxeo.runtime.osgi.OSGiRuntimeActivator;
import org.nuxeo.runtime.osgi.OSGiRuntimeContext;
import org.osgi.framework.Bundle;

/**
 * New behavior @since TODO
 *
 * As the runtime lifecycle changed there make no sense to unregister components by their own.
 * The component registry is either reset to a clean state or shutdown.
 * So methods like unregister by location used in tests make no sense. These methods are preserved yet for compatibility
 * with some tests but may be removed in future.
 * Also when a context is destroyed  we unregister all the components the context deployed.
 * This is also deprecated since the unregister component is deprecated too.
 * The code inside destroy methpd was removed too since the deployedFiles map was removed.
 * This map posed problems with the registry snapshot approaches since it was not kept in sync with the registry.
 *
 * If unregistering components will be removed completely don't forget to remove {@link ComponentManager#unregisterByLocation(String)} and the {@link ComponentRegistry#deployedFiles}
 *
 * Features like studio or IDE which needs unregistering components must use their own mechanism.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class DefaultRuntimeContext implements RuntimeContext {

	private static final Log log = LogFactory.getLog(RuntimeContext.class);

	protected RuntimeService runtime;

	protected final ComponentDescriptorReader reader;

	public DefaultRuntimeContext() {
		this(Framework.getRuntime());
	}

	public DefaultRuntimeContext(RuntimeService runtime) {
		this.runtime = runtime;
		reader = new ComponentDescriptorReader();
	}

	public void setRuntime(RuntimeService runtime) {
		this.runtime = runtime;
	}

	@Override
	public RuntimeService getRuntime() {
		return runtime;
	}

	@Override
	public URL getResource(String name) {
		return Thread.currentThread().getContextClassLoader().getResource(name);
	}

	@Override
	public URL getLocalResource(String name) {
		return Thread.currentThread().getContextClassLoader().getResource(name);
	}

	@Override
	public Class<?> loadClass(String className) throws ClassNotFoundException {
		return Thread.currentThread().getContextClassLoader().loadClass(className);
	}

	@Override
	public RegistrationInfo deploy(URL url) throws IOException {
		return deploy(new URLStreamRef(url));
	}

	@Override
	public RegistrationInfo deploy(StreamRef ref) throws IOException {
		String name = ref.getId();
		RegistrationInfoImpl ri = createRegistrationInfo(ref);
		if (ri == null || ri.name == null) {
			// not parsed correctly, e.g., faces-config.xml
			return null;
		}
		log.debug("Deploying component from url " + name);
		ri.sourceId = name;
		ri.context = this;
		ri.xmlFileUrl = ref.asURL();
		if (ri.getBundle() != null) {
			// this is an external component XML.
			// should use the real owner bundle as the context.
			Bundle bundle = OSGiRuntimeActivator.getInstance().getBundle(ri.getBundle());
			if (bundle != null) {
				ri.context = new OSGiRuntimeContext(bundle);
			}
		}
		runtime.getComponentManager().register(ri);
		return ri;
	}

	@Override
	@Deprecated
	public void undeploy(URL url) {
		runtime.getComponentManager().unregisterByLocation(url.toString());
	}

	@Override
	@Deprecated
	public void undeploy(StreamRef ref) {
		runtime.getComponentManager().unregisterByLocation(ref.getId());
	}

	@Override
	@Deprecated
	public boolean isDeployed(URL url) {
		return runtime.getComponentManager().hasComponentFromLocation(url.toString());
	}

	@Override
	@Deprecated
	public boolean isDeployed(StreamRef ref) {
		return runtime.getComponentManager().hasComponentFromLocation(ref.getId());
	}

	@Override
	public RegistrationInfo deploy(String location) {
		URL url = getLocalResource(location);
		if (url == null) {
			throw new IllegalArgumentException("No local resources was found with this name: " + location);
		}
		try {
			return deploy(url);
		} catch (IOException e) {
			throw new RuntimeServiceException("Cannot deploy: " + location, e);
		}
	}

	@Override
	@Deprecated
	public void undeploy(String location) {
		URL url = getLocalResource(location);
		if (url == null) {
			throw new IllegalArgumentException("No local resources was found with this name: " + location);
		}
		undeploy(url);
	}

	@Override
	@Deprecated
	public boolean isDeployed(String location) {
		URL url = getLocalResource(location);
		if (url != null) {
			return isDeployed(url);
		} else {
			log.warn("No local resources was found with this name: " + location);
			return false;
		}
	}

	@Override
	public void destroy() {
		// do nothing - components are no more unregistered by the context
		/*
		Iterator<ComponentName> it = deployedFiles.values().iterator();
		ComponentManager mgr = runtime.getComponentManager();
		while (it.hasNext()) {
			ComponentName name = it.next();
			it.remove();
			mgr.unregister(name);
		}
		*/
	}

	@Override
	public Bundle getBundle() {
		return null;
	}

	public RegistrationInfoImpl createRegistrationInfo(StreamRef ref) throws IOException {
		String source = IOUtils.toString(ref.getStream(), Charsets.UTF_8);
		String expanded = Framework.expandVars(source);
		try (InputStream in = new ByteArrayInputStream(expanded.getBytes())) {
			return createRegistrationInfo(in);
		}
	}

	public RegistrationInfoImpl createRegistrationInfo(InputStream in) throws IOException {
		return reader.read(this, in);
	}

}
