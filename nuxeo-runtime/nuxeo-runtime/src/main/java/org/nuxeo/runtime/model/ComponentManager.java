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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */

package org.nuxeo.runtime.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.nuxeo.runtime.ComponentListener;

/**
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public interface ComponentManager {

    /**
     * Adds a component listener.
     * <p>
     * Does nothing if the given listener is already registered.
     *
     * @param listener the component listener to add
     */
    void addComponentListener(ComponentListener listener);

    /**
     * Removes a component listener.
     * <p>
     * Does nothing if the given listener is not registered.
     *
     * @param listener the component listener to remove
     */
    void removeComponentListener(ComponentListener listener);

    /**
     * Handles the registration of the given registration info.
     * <p>
     * This is called by the main registry when all dependencies of this registration info were solved and the object
     * can be registered.
     * <p>
     * If true is returned, the object will be added to the main registry under the name given in RegistrationInfo.
     *
     * @param ri the registration info
     */
    void register(RegistrationInfo ri);

    /**
     * Handles the unregistration of the given registration info.
     * <p>
     * This is called by the main registry when the object is unregistered.
     * <p>
     * If true is returned, the object will be removed from the main registry.
     *
     * @param ri the registration info
     */
    void unregister(RegistrationInfo ri);

    /**
     * Unregisters a component given its name.
     *
     * @param name the component name
     */
    void unregister(ComponentName name);

    /**
     * Gets the component if there is one having the given name.
     *
     * @param name the component name
     * @return the component if any was registered with that name, null otherwise
     */
    RegistrationInfo getRegistrationInfo(ComponentName name);

    /**
     * Gets object instance managed by the named component.
     *
     * @param name the object name
     * @return the object instance if any. may be null
     */
    ComponentInstance getComponent(ComponentName name);

    /**
     * Checks whether or not a component with the given name was registered.
     *
     * @param name the object name
     * @return true if an object with the given name was registered, false otherwise
     */
    boolean isRegistered(ComponentName name);

    /**
     * Gets the registered components.
     *
     * @return a read-only collection of components
     */
    Collection<RegistrationInfo> getRegistrations();

    /**
     * Gets the pending registrations and their dependencies.
     *
     * @return the pending registrations
     */
    Map<ComponentName, Set<ComponentName>> getPendingRegistrations();

    /**
     * Returns the missing registrations, linked to missing target extension points.
     *
     * @since 8.10
     */
    Map<ComponentName, Set<Extension>> getMissingRegistrations();

    /**
     * Gets the pending extensions by component.
     *
     * @return the pending extensions
     */
    Collection<ComponentName> getActivatingRegistrations();

    /**
     * Gets the resolved component names in the order they were resolved
     *
     * @since TODO
     */
    Collection<ComponentName> getResolvedRegistrations();

    /**
     * Gets the components that fail on applicationStarted notification
     *
     * @since 7.4
     */
    Collection<ComponentName> getStartFailureRegistrations();

    /**
     * Gets the number of registered objects in this registry.
     *
     * @return the number of registered objects
     */
    int size();

    /**
     * Shuts down the component registry.
     * <p>
     * This unregisters all objects registered in this registry.
     */
    void shutdown();

    /**
     * Gets the service of type serviceClass if such a service was declared by a resolved runtime component.
     * <p>
     * If the component is not yet activated it will be prior to return the service.
     *
     * @param <T> the service type
     * @param serviceClass the service class
     * @return the service object
     */
    <T> T getService(Class<T> serviceClass);

    /**
     * Get the list of all registered service names An empty array is returned if no registered services are found.
     *
     * @return an array of registered service.
     */
    String[] getServices();

    /**
     * Gets the component that provides the given service.
     *
     * @param serviceClass the service class
     * @return the component or null if none
     */
    ComponentInstance getComponentProvidingService(Class<?> serviceClass);

    Set<String> getBlacklist();

    void setBlacklist(Set<String> blacklist);

    /**
     * Activate and start all resolved components. If components were already started do nothing.
     * @return false if components were already started, true otherwise
     * @since TODO
     */
    boolean start();

    /**
     * Stop and deactivate all resolved components. If components were not yet started do nothing
     * @return false if components were not yet started, true otherwise
     * @since TODO
     */
    boolean stop();

    /**
     * Make a snapshot of the component registry.
     * When calling restart
     * @since TODO
     */
    void snapshot();

    /**
     * Reset the registry to the last snapshot and restart the components.
     * If no snapshot was created then the components will be restarted without changing the registry.
     * <p>
     * If the <code>reset</code> argument is true then the registry will be reverted to the last snapshot before starting the components.
     * @param reset whether or not to revert to the last snapshot
     * @since TODO
     */
    void restart(boolean reset);

    /**
     * Reset the registry to the last snapshot if any and stop the components (if they are currently started).
     * After a reset all the components are stopped so we can contribute new components if needed.
     * You must call {@link #start()} to start again the components
     * @return true if the components were stopped, false otherwise
     * @since TODO
     */
    boolean reset();

    /**
     * Refresh the registry using stashed registrations if any.
     * If the <code>reset</code> argument is true then the registry will be reverted to the last snapshot before applying the stash.
     * <p>
     * If the stash is empty it does nothing and return true, otherwise it will:
     * <ol>
     * <li> stop the components (if they are started)
     * <li> revert to the last snapshot (if reset flag is true)
     * <li> apply the stash (the stash will remain empty after this operation)
     * <li> start the components (if they was started)
     * </ol>
     * @param reset whether or not to revert to the last snapshot
     * @return false if stash is empty and nothing was done, true otherwise
     * @since TODO
     */
    boolean refresh(boolean reset);

    /**
     * Tests whether the components were already started.
     * @return true if components are started, false
     * @since TODO
     */
    boolean isStarted();

    /**
     * Check if a snapshot was done
     * @return true if a snapshot already exists, false otherwise
     * @since TODO
     */
    boolean hasSnapshot();
}
