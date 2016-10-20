/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.automation.scripting.internals;

import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.AUTOMATION_SCRIPTING_PRECOMPILE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.COMPLIANT_JAVA_VERSION_CACHE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.COMPLIANT_JAVA_VERSION_CLASS_FILTER;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.DEFAULT_PRECOMPILE_STATUS;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_JAVA_VERSION;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_WARN_CACHE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_WARN_CLASS_FILTER;
import static org.nuxeo.launcher.config.ConfigurationGenerator.checkJavaVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.automation.scripting.api.AutomationScripting;
import org.nuxeo.automation.scripting.internals.wrappers.AutomationMapper;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 *
 * @since TODO
 */
public class AutomationScriptingImpl implements AutomationScripting {

    final ScriptEngine engine = new SupplierSelector().supplier.get();

    final Compilable compilable = ((Compilable) engine);

    final Invocable invocable = ((Invocable) engine);

    final CompiledScript mapperScript = AutomationMapper.compile((Compilable) engine);


    @Override
    public Context get(CoreSession session) {
        return get(new OperationContext(session));
    }

    @Override
    public Context get(OperationContext context) {
        return new ContextImpl(new AutomationMapper(context));
    }


    class ContextImpl implements Context {

        final ScriptContext context = new SimpleScriptContext();

        final ScriptObjectMirror global;

        final AutomationMapper mapper;

        ContextImpl(AutomationMapper mapper) {
            this.mapper = mapper;
            context.setBindings(mapper, ScriptContext.ENGINE_SCOPE);
            try {
                mapperScript.eval(mapper);
            } catch (ScriptException cause) {
                throw new NuxeoException("Cannot execute mapper " + mapperScript, cause);
            }
            global = (ScriptObjectMirror) mapper.get("nashorn.global");
        }


        @Override
        public <T> T handleof(InputStream input, Class<T> typeof) {
            run(input);
            T handle = invocable.getInterface(global, typeof);
            if (handle == null) {
                throw new NuxeoException("Script doesn't implements " + typeof.getName());
            }
            return typeof.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class[] { typeof }, new InvocationHandler() {

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            return mapper.unwrap(method.invoke(handle, mapper.wrap(args[0]), mapper.wrap(args[1])));
                        }
                    }));
        }

        @Override
        public Object run(InputStream input) {
            try {
                return mapper.unwrap(engine.eval(new InputStreamReader(input), mapper));
            } catch (ScriptException cause) {
                throw new NuxeoException("Cannot evaluate automation script", cause);
            }
        }

        <T> T handleof(Class<T> typeof) {
            return invocable.getInterface(global, typeof);
        }

        @Override
        public <T> T adapt(Class<T> typeof) {
            if (typeof.isAssignableFrom(engine.getClass())) {
                return typeof.cast(engine);
            }
            if (typeof.isAssignableFrom(context.getClass())) {
                return typeof.cast(context);
            }
            throw new IllegalArgumentException("Cannot adapt scripting context to " + typeof.getName());
        }

        @Override
        public void close() throws Exception {
            mapper.flush();
        }

        @Override
        public int size() {
            return mapper.size();
        }

        @Override
        public boolean isEmpty() {
            return mapper.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return mapper.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return mapper.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            return mapper.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return mapper.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            return mapper.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ? extends Object> m) {
            mapper.putAll(m);
        }

        @Override
        public void clear() {
            mapper.clear();
        }

        @Override
        public Set<String> keySet() {
            return mapper.keySet();
        }

        @Override
        public Collection<Object> values() {
            return mapper.values();
        }

        @Override
        public Set<java.util.Map.Entry<String, Object>> entrySet() {
            return mapper.entrySet();
        }

        @Override
        public Object getOrDefault(Object key, Object defaultValue) {
            return mapper.getOrDefault(key, defaultValue);
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super Object> action) {
            mapper.forEach(action);
        }

        @Override
        public void replaceAll(BiFunction<? super String, ? super Object, ? extends Object> function) {
            mapper.replaceAll(function);
        }

        @Override
        public Object putIfAbsent(String key, Object value) {
            return mapper.putIfAbsent(key, value);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return mapper.remove(key, value);
        }

        @Override
        public boolean replace(String key, Object oldValue, Object newValue) {
            return mapper.replace(key, oldValue, newValue);
        }

        @Override
        public Object replace(String key, Object value) {
            return mapper.replace(key, value);
        }

        @Override
        public Object computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction) {
            return mapper.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public Object computeIfPresent(String key,
                BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
            return mapper.computeIfPresent(key, remappingFunction);
        }

        @Override
        public Object compute(String key,
                BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
            return mapper.compute(key, remappingFunction);
        }

        @Override
        public Object merge(String key, Object value,
                BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
            return mapper.merge(key, value, remappingFunction);
        }

    }

    class SupplierSelector {

        final NashornScriptEngineFactory nashorn = new NashornScriptEngineFactory();

        final Supplier<ScriptEngine> supplier = supplier();

        Supplier<ScriptEngine> supplier() {

            Log log = LogFactory.getLog(AutomationScriptingImpl.class);
            String version = Framework.getProperty("java.version");
            // Check if jdk8
            if (!checkJavaVersion(version, NASHORN_JAVA_VERSION)) {
                throw new UnsupportedOperationException(NASHORN_JAVA_VERSION);
            }
            // Check if version < jdk8u25 -> no cache.
            if (!checkJavaVersion(version, COMPLIANT_JAVA_VERSION_CACHE)) {
                log.warn(NASHORN_WARN_CACHE);
                return noCache();
            }
            // Check if jdk8u25 <= version < jdk8u40 -> only cache.
            if (!checkJavaVersion(version, COMPLIANT_JAVA_VERSION_CLASS_FILTER)) {
                if (Boolean.parseBoolean(
                        Framework.getProperty(AUTOMATION_SCRIPTING_PRECOMPILE, DEFAULT_PRECOMPILE_STATUS))) {
                    log.warn(NASHORN_WARN_CLASS_FILTER);
                    return cache();
                } else {
                    log.warn(NASHORN_WARN_CLASS_FILTER);
                    return noCache();
                }
            }
            // Else if version >= jdk8u40 -> cache + class filter
            try {
                if (Boolean.parseBoolean(
                        Framework.getProperty(AUTOMATION_SCRIPTING_PRECOMPILE, DEFAULT_PRECOMPILE_STATUS))) {
                    return cacheClass();
                } else {
                    return noCacheClass();
                }
            } catch (NoClassDefFoundError cause) {
                log.warn(NASHORN_WARN_CLASS_FILTER);
                return cache();
            }
        }

        Supplier<ScriptEngine> noCache() {
            return () -> nashorn.getScriptEngine(new String[] { "-strict" });
        }

        Supplier<ScriptEngine> cache() {
            return () -> nashorn.getScriptEngine(
                    new String[] { "-strict", "--persistent-code-cache", "--class-cache-size=50" },
                    Thread.currentThread().getContextClassLoader());
        }

        Supplier<ScriptEngine> cacheClass() {
            return () -> nashorn.getScriptEngine(
                    new String[] { "-strict", "--persistent-code-cache", "--class-cache-size=50" },
                    Thread.currentThread().getContextClassLoader(), new ClassFilter() {

                        @Override
                        public boolean exposeToScripts(String className) {
                            return false;
                        }

                    });
        }

        Supplier<ScriptEngine> noCacheClass() {
            return () -> nashorn.getScriptEngine(new String[] { "-strict" },
                    Thread.currentThread().getContextClassLoader(), new ClassFilter() {

                        @Override
                        public boolean exposeToScripts(String className) {
                            return false;
                        }

                    });
        };
    }

}
