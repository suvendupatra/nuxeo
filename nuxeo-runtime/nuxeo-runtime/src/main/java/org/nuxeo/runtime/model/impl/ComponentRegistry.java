/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.runtime.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.RegistrationInfo;

/**
 * This class is synchronized to safely update and access the different maps managed by the registry
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ComponentRegistry {

    private final Log log = LogFactory.getLog(ComponentRegistry.class);

    /**
     * All registered components including unresolved ones. You can check the state of a component for getting the
     * unresolved ones.
     */
    protected Map<ComponentName, RegistrationInfoImpl> components;

    /**
     * The list of resolved components. We need to use a linked hash map preserve the resolve order.
     * We don't use a simple list to optimize removal by name (used by unregister operations)
     */
    protected LinkedHashMap<ComponentName, RegistrationInfoImpl> resolved;

    /** Map of aliased name to canonical name. */
    protected Map<ComponentName, ComponentName> aliases;

    /**
     * Maps a component name to a set of component names that are depending on that component. Values are always
     * unaliased.
     */
    protected MappedSet requirements;

    /**
     * Map pending components to the set of unresolved components they are waiting for. Key is always unaliased.
     */
    protected MappedSet pendings;

    /**
     * Map deployment source ids to component names
     * This was previously managed by DefaultRuntimeContext - but is no more usable in the original form.
     * This map is only useful for unregister by location - which is used by some tests.
     * Remove this if the unregister API will be removed.
     */
    protected Map<String, ComponentName> deployedFiles;

    public ComponentRegistry() {
        components = new HashMap<ComponentName, RegistrationInfoImpl>();
        aliases = new HashMap<ComponentName, ComponentName>();
        requirements = new MappedSet();
        pendings = new MappedSet();
        resolved = new LinkedHashMap<ComponentName, RegistrationInfoImpl>();
        deployedFiles = new HashMap<>();
    }

    public ComponentRegistry(ComponentRegistry reg) {
        components = new HashMap<>(reg.components);
        aliases = new HashMap<>(reg.aliases);
        requirements = new MappedSet(reg.requirements);
        pendings = new MappedSet(reg.pendings);
        resolved = new LinkedHashMap<>(reg.resolved);
        deployedFiles = new HashMap<>(reg.deployedFiles);
    }

    public synchronized void destroy() {
        components = null;
        aliases = null;
        requirements = null;
        pendings = null;
        deployedFiles = null;
    }

    public synchronized final boolean isResolved(ComponentName name) {
        RegistrationInfo ri = components.get(unaliased(name));
        if (ri == null) {
            return false;
        }
        return ri.getState() > RegistrationInfo.REGISTERED;
    }

    /**
     * @param ri
     * @return true if the component was resolved, false if the component is pending
     */
    public synchronized boolean addComponent(RegistrationInfoImpl ri) {
        ComponentName name = ri.getName();
        Set<ComponentName> al = ri.getAliases();
        String aliasInfo = al.isEmpty() ? "" : ", aliases=" + al;
        log.info("Registering component: " + name + aliasInfo);
        ri.register();
        // map the source id with the component name - see ComponentManager.unregisterByLocation
        if (ri.sourceId != null) {
        	deployedFiles.put(ri.sourceId, ri.getName());
        }
        components.put(name, ri);
        for (ComponentName n : al) {
            aliases.put(n, name);
        }
        boolean hasUnresolvedDependencies = computePendings(ri);
        if (!hasUnresolvedDependencies) {
            resolveComponent(ri);
            return true;
        }
        return false;
    }

    public synchronized RegistrationInfoImpl removeComponent(ComponentName name) {
        RegistrationInfoImpl ri = components.remove(name);
        if (ri != null) {
            try {
                unresolveComponent(ri);
            } finally {
                ri.unregister();
            }
        }
        return ri;
    }

    /**
     * Get a copy of the resolved components map
     * @since TODO
     * @return
     */
    public synchronized LinkedHashMap<ComponentName, RegistrationInfoImpl> getResolvedMap() {
        return new LinkedHashMap<ComponentName,RegistrationInfoImpl>(resolved);
    }

    /**
     * Get a copy of the resolved components as a list
     * @since TODO
     * @return
     */
    public synchronized List<RegistrationInfoImpl> getResolvedList() {
        return new ArrayList<RegistrationInfoImpl>(resolved.values());
    }

    /**
     * Get a copy of the resolved components as an array
     * @since TODO
     * @return
     */
    public synchronized RegistrationInfoImpl[] getResolvedArray() {
        return resolved.values().toArray(new RegistrationInfoImpl[resolved.size()]);
    }

    /**
     * Get a list of the resolved component names
     * @since TODO
     * @return
     */
    public synchronized List<ComponentName> getResolvedNames() {
        return new ArrayList<ComponentName>(resolved.keySet());
    }

    /**
     * Get a copy of the missing dependencies set
     * @param name
     * @return
     */
    public synchronized Set<ComponentName> getMissingDependencies(ComponentName name) {
        return new HashSet<ComponentName>(pendings.get(name));
    }

    /**
     * Get the registration info for the given component name or null if none was registered.
     * @param name
     * @return
     */
    public synchronized RegistrationInfoImpl getComponent(ComponentName name) {
        return components.get(unaliased(name));
    }

    /**
     * Check if the component is already registered against this registry
     * @param name
     * @return
     */
    public synchronized boolean contains(ComponentName name) {
        return components.containsKey(unaliased(name));
    }

    /**
     * Get the registered components count
     * @return
     */
    public synchronized int size() {
        return components.size();
    }

    /**
     * Get a copy of the registered components list
     * @return
     */
    public synchronized List<RegistrationInfo> getComponents() {
        return new ArrayList<RegistrationInfo>(components.values());
    }

    /**
     * Get a copy of the registered components as an array
     * @return
     */
    public synchronized RegistrationInfoImpl[] getComponentsArray() {
        return components.values().toArray(new RegistrationInfoImpl[components.size()]);
    }


    /**
     * Get a copy of the pending components map
     * @return
     */
    public synchronized Map<ComponentName, Set<ComponentName>> getPendingComponents() {
        return new MappedSet(pendings).map;
    }

    protected ComponentName unaliased(ComponentName name) {
        ComponentName alias = aliases.get(name);
        return alias == null ? name : alias;
    }

    /**
     * Fill the pending map with all unresolved dependencies of the given component. Returns false if no unresolved
     * dependencies are found, otherwise returns true.
     *
     * @param ri
     * @return
     */
    protected final boolean computePendings(RegistrationInfo ri) {
        Set<ComponentName> set = ri.getRequiredComponents();
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean hasUnresolvedDependencies = false;
        // fill the requirements and pending map
        for (ComponentName name : set) {
            if (!isResolved(name)) {
                pendings.put(ri.getName(), name);
                hasUnresolvedDependencies = true;
            }
            requirements.put(name, ri.getName());
        }
        return hasUnresolvedDependencies;
    }

    protected void resolveComponent(RegistrationInfoImpl ri) {
        ComponentName riName = ri.getName();
        Set<ComponentName> names = new HashSet<ComponentName>();
        names.add(riName);
        names.addAll(ri.getAliases());

        ri.resolve();
        resolved.put(ri.getName(), ri); // track resolved components

        // try to resolve pending components that are waiting the newly
        // resolved component
        Set<ComponentName> dependsOnMe = new HashSet<ComponentName>();
        for (ComponentName n : names) {
            Set<ComponentName> reqs = requirements.get(n);
            if (reqs != null) {
                dependsOnMe.addAll(reqs); // unaliased
            }
        }
        if (dependsOnMe == null || dependsOnMe.isEmpty()) {
            return;
        }
        for (ComponentName name : dependsOnMe) { // unaliased
            for (ComponentName n : names) {
                pendings.remove(name, n);
            }
            Set<ComponentName> set = pendings.get(name);
            if (set == null || set.isEmpty()) {
                RegistrationInfoImpl waitingRi = components.get(name);
                resolveComponent(waitingRi);
            }
        }
    }

    protected void unresolveComponent(RegistrationInfoImpl ri) {
        Set<ComponentName> reqs = ri.getRequiredComponents();
        ComponentName name = ri.getName();
        ri.unresolve();
        resolved.remove(name);
        pendings.remove(name);
        if (reqs != null) {
            for (ComponentName req : reqs) {
                requirements.remove(req, name);
            }
        }
        Set<ComponentName> set = requirements.get(name); // unaliased
        if (set != null && !set.isEmpty()) {
            for (ComponentName dep : set.toArray(new ComponentName[set.size()])) {
                RegistrationInfoImpl depRi = components.get(dep);
                if (depRi != null) {
                    unresolveComponent(depRi);
                }
            }
        }
    }

    static class MappedSet {
        protected Map<ComponentName, Set<ComponentName>> map;

        public MappedSet() {
            map = new HashMap<ComponentName, Set<ComponentName>>();
        }

        /**
         * Create a clone of a mapped set (set values are cloned too)
         * @param mset
         */
        public MappedSet(MappedSet mset) {
        	this ();
        	for (Map.Entry<ComponentName, Set<ComponentName>> entry : mset.map.entrySet()) {
        		ComponentName name = entry.getKey();
        		Set<ComponentName> set = entry.getValue();
        		HashSet<ComponentName> newSet = new HashSet<ComponentName>(set);
        		map.put(name, newSet);
        	}
        }

        public Set<ComponentName> get(ComponentName name) {
            return map.get(name);
        }

        public Set<ComponentName> put(ComponentName key, ComponentName value) {
            Set<ComponentName> set = map.get(key);
            if (set == null) {
                set = new HashSet<ComponentName>();
                map.put(key, set);
            }
            set.add(value);
            return set;
        }

        public Set<ComponentName> remove(ComponentName key) {
            return map.remove(key);
        }

        public Set<ComponentName> remove(ComponentName key, ComponentName value) {
            Set<ComponentName> set = map.get(key);
            if (set != null) {
                set.remove(value);
                if (set.isEmpty()) {
                    map.remove(key);
                }
            }
            return set;
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public int size() {
            return map.size();
        }

        public void clear() {
            map.clear();
        }
    }
}
