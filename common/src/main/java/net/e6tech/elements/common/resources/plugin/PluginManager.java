/*
 * Copyright 2015-2019 Futeh Kao
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
 */

package net.e6tech.elements.common.resources.plugin;

import net.e6tech.elements.common.inject.Injector;
import net.e6tech.elements.common.inject.Module;
import net.e6tech.elements.common.inject.ModuleFactory;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.file.FileUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings({ "squid:S134", "unchecked"})
public class PluginManager {

    private static final String DEFAULT_PLUGIN = "defaultPlugin";
    private static final Object NULL_OBJECT = new Object();

    private PluginClassLoader classLoader;
    private ResourceManager resourceManager;
    private Resources resources;
    private Map<PluginPath, Object> plugins = new HashMap<>();
    private Map<Class, Object> defaultPlugins = new HashMap<>();

    public PluginManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        classLoader = new PluginClassLoader(resourceManager.getClass().getClassLoader());
    }

    public PluginManager from(Resources resources) {
        PluginManager plugin = new PluginManager(resourceManager);
        plugin.resources = resources;
        plugin.defaultPlugins = defaultPlugins;
        plugin.classLoader = classLoader;
        plugin.plugins = plugins;
        return plugin;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public void loadPlugins(String[] directories) {
        for (String dir: directories) {
            String[] paths;
            try {
                paths = FileUtil.listFiles(dir, ".jar");
            } catch (IOException e) {
                throw new SystemException(e);
            }
            for (String p : paths) {
                String fullPath;
                try {
                    fullPath = new File(p).getCanonicalPath();
                } catch (IOException e) {
                    continue;
                }

                try {
                    classLoader.addURL(Paths.get(fullPath).toUri().toURL());
                } catch (IOException e) {
                    throw new SystemException(e);
                }
            }
        }
    }

    public URLClassLoader getPluginClassLoader() {
        return classLoader;
    }

    @SuppressWarnings({"unchecked", "squid:S3824", "squid:S3776"})
    protected Optional getDefaultPlugin(Class type) {
        Object lookup = defaultPlugins.get(type);
        if (lookup == NULL_OBJECT)
            return Optional.empty();

        if (lookup == null) {
            Class t = type;
            while (t != null && !t.equals(Object.class)) {
                try {
                    Field field = t.getField(DEFAULT_PLUGIN);
                    lookup = field.get(null);
                    defaultPlugins.put(type, lookup);
                    break;
                } catch (NoSuchFieldException | IllegalAccessException e1) {
                    Logger.suppress(e1);
                }
                t = t.getSuperclass();
            }
            if (lookup == null
                    && type != null
                    && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers())
                    && Modifier.isPublic(type.getModifiers())
                    && AutoPlugin.class.isAssignableFrom(type)) {
                try {
                    // test for existence of zero argument constructor
                    type.getDeclaredConstructor();
                    lookup = type;
                    defaultPlugins.put(type, lookup);
                } catch (NoSuchMethodException e) {
                    // ok
                }
            }

            if (lookup == null)
                defaultPlugins.put(type, NULL_OBJECT);
        }

        return Optional.ofNullable(lookup);
    }

    public Map<PluginPath, Object> startsWith(PluginPaths<?> paths) {
        Map<PluginPath, Object> map = new LinkedHashMap<>();

        for (PluginPath path : paths.getPaths()) {
            for (Map.Entry<PluginPath, Object> entry : plugins.entrySet()) {
                if (entry.getKey().startsWith(path) && !map.containsKey(entry.getKey()))
                    map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    public <T extends Plugin> Optional<T> get(PluginPaths<T> paths, Object ... args) {
        Object lookup = null;
        PluginPath<T> pluginPath = null;

        // look up from paths
        for (PluginPath path : paths.getPaths()) {
            lookup = plugins.get(path);
            if (lookup != null) {
                pluginPath = path;
                break;
            }
        }

        // if still null, look up from default plugin
        if (lookup == null) {
            pluginPath = PluginPath.of(paths.getType(), DEFAULT_PLUGIN);
        }
        return Optional.ofNullable(createInstance(pluginPath, lookup, args));
    }

    public <T extends Plugin> T createInstance(PluginPath<T> pluginPath, Object obj, Object ... args) {
        if (obj == null && pluginPath == null)
            return null;

        if (obj == null) {
            // get default plugin
            Optional defaultPlugin = getDefaultPlugin(pluginPath.getType());
            if (!defaultPlugin.isPresent())
                return null;
            obj = defaultPlugin.get();
            pluginPath = PluginPath.of(pluginPath.getType(), DEFAULT_PLUGIN);
        }

        T plugin;
        if (obj instanceof Class) {
            plugin = createFromClass(pluginPath, (Class) obj, args);
        } else if (obj instanceof PluginFactory) {
            plugin = createFromFactory(pluginPath, (PluginFactory) obj, args);
        } else {
            plugin = createFromInstance(pluginPath, obj, args);
        }
        return plugin;
    }

    private  <T extends Plugin> T createFromClass(PluginPath<T> pluginPath, Class cls, Object ... args) {
        try {
            T plugin =  (T) cls.getDeclaredConstructor().newInstance();
            plugin.initialize(pluginPath);
            inject(plugin, args);
            return plugin;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private  <T extends Plugin> T createFromFactory(PluginPath<T> pluginPath, PluginFactory factory, Object ... args) {
        T plugin = factory.create(this);
        plugin.initialize(pluginPath);
        inject(plugin, args);
        return plugin;
    }

    private  <T extends Plugin> T createFromInstance(PluginPath<T> pluginPath, Object obj, Object ... args) {
        T plugin;
        T prototype = (T) obj;
        if (prototype instanceof Prototype) {
            plugin = (T) ((Prototype) prototype).newInstance();
            plugin.initialize(pluginPath);
            inject(plugin, args);
        } else if (prototype.isPrototype()) {
            try {
                plugin = (T) prototype.getClass().getDeclaredConstructor().newInstance();
                Reflection.copyInstance(plugin, prototype);
                plugin.initialize(pluginPath);
                inject(plugin, args);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        } else {
            // this is a singleton, no need to initialize and inject.
            plugin = prototype;
        }
        return plugin;
    }

    @SuppressWarnings("squid:S3776")
    public void inject(Object instance, Object ... args) {
        if (instance != null && args != null && args.length > 0) {
            ModuleFactory factory = (resources != null) ? resources.getModule().getFactory() :
                    resourceManager.getModule().getFactory();
            Module module = factory.create();
            for (Object arg : args) {
                if (arg instanceof  Binding) {
                    Binding binding = (Binding) arg;
                    if (binding.getName() != null) {
                        module.bindNamedInstance(binding.getBoundClass(), binding.getName(), binding.get());
                    } else {
                        module.bindInstance(binding.getBoundClass(), binding.get());
                    }
                } else {
                    module.bindInstance(arg.getClass(), arg);
                }
            }

            Injector injector = (resources != null) ?
                    module.build(resources.getModule(), resourceManager.getModule())
                    : module.build(resourceManager.getModule());
            InjectionListener injectionListener = null;
            ResourcePool resourcePool = (resources != null) ? resources : resourceManager;
            if (instance instanceof InjectionListener) {
                injectionListener = (InjectionListener) instance;
                injectionListener.preInject(resourcePool);
            }
            injector.inject(instance, true);
            if (injectionListener != null)
                injectionListener.injected(resourcePool);
        } else if (instance != null && resources != null) {
            resources.inject(instance);
        }
    }

    public <T extends Plugin> Optional<T> get(PluginPath<T> path, Object ... args) {
        return get(PluginPaths.of(path), args);
    }

    public synchronized <T extends Plugin> void add(PluginPath<T> path, Class<T> cls) {
        plugins.put(path, cls);
    }

    public synchronized <T extends Plugin> void add(PluginPath<T> path, T singleton) {
        plugins.put(path, singleton);
        resourceManager.inject(singleton, !singleton.isPrototype());
        singleton.initialize(path);
    }

    public synchronized <T extends Plugin> Object remove(PluginPath<T> path) {
        return plugins.remove(path);
    }

    public synchronized <T extends Plugin, U extends T> void addDefault(Class<T> cls, U singleton) {
        defaultPlugins.put(cls, singleton);
        resourceManager.inject(singleton, !singleton.isPrototype());
        singleton.initialize(PluginPath.of(cls, DEFAULT_PLUGIN));
    }

    public synchronized <T extends Plugin, U extends T> void addDefault(Class<T> cls, Class<U> implClass) {
        defaultPlugins.put(cls, implClass);
    }

    public Object removeDefault(Class cls) {
        return defaultPlugins.remove(cls);
    }

    public static class PluginClassLoader extends URLClassLoader {

        private List<URL> pluginURLs = new ArrayList<>();

        public PluginClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
            pluginURLs.add(url);
        }

        public URL[] getPluginURLs() {
            return pluginURLs.toArray(new URL[0]);
        }

    }
}
