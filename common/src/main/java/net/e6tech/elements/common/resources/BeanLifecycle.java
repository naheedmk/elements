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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.util.SystemException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S1149")
public class BeanLifecycle {
    private static final int BEAN_INITIALIZED = 0;
    private static final int BEAN_STARTED = 1;
    private static final int BEAN_LAUNCHED = 2;

    private Map<String, Object> initializedBeans = new ConcurrentHashMap<>();
    private Map<String, Object> startedBeans = new ConcurrentHashMap<>();
    private Map<String, Object> launchedBeans = new ConcurrentHashMap<>();
    private Set<Object> disabledBeans = new HashSet<>();
    private Map<String, List<BeanListener>> namedBeanListeners = new ConcurrentHashMap<>();
    private Map<Class, List<BeanListener>> classBeanListeners = new ConcurrentHashMap<>();

    public void addBeanListener(String name, BeanListener beanListener) {
        if (initializedBeans.get(name) != null) {
            beanListener.initialized(initializedBeans.get(name));
            return;
        }
        List<BeanListener> listeners = namedBeanListeners.computeIfAbsent(name, n -> new CopyOnWriteArrayList<>());
        listeners.add(beanListener);
    }

    @SuppressWarnings("unchecked")
    public void addBeanListener(Class cls, BeanListener beanListener) {
        for (Object bean : initializedBeans.values()) {
            if (cls.isAssignableFrom(bean.getClass())) {
                beanListener.initialized(bean);
            }
        }

        List<BeanListener> listeners = classBeanListeners.computeIfAbsent(cls, n -> new CopyOnWriteArrayList<>());
        listeners.add(beanListener);
    }

    public void removeBeanListener(BeanListener listener) {
        for (List<BeanListener> listeners : namedBeanListeners.values())
            listeners.remove(listener);
        for (List<BeanListener> listeners : classBeanListeners.values())
            listeners.remove(listener);
    }

    public void fireBeanInitialized(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_INITIALIZED);
        initializedBeans.put(beanName, bean);
    }

    public boolean isBeanInitialized(Object bean) {
        return initializedBeans.containsValue(bean);
    }

    public void fireBeanStarted(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_STARTED);
        startedBeans.put(beanName, bean);
    }

    public boolean isBeanStarted(Object bean) {
        return startedBeans.containsValue(bean);
    }

    public void fireBeanLaunched(String beanName, Object bean) {
        fireBeanEvent(beanName, bean, BEAN_LAUNCHED);
        launchedBeans.put(beanName, bean);
    }

    public boolean isBeanLaunched(Object bean) {
        return launchedBeans.containsValue(bean);
    }

    public void disableBean(Object bean) {
        disabledBeans.add(bean);
    }

    public boolean isBeanDisabled(Object bean) {
        return disabledBeans.contains(bean);
    }

    public void clearBeanListeners() {
        initializedBeans.clear();
        startedBeans.clear();
        launchedBeans.clear();
        disabledBeans.clear();
        namedBeanListeners.clear();
        classBeanListeners.clear();
    }

    @SuppressWarnings("unchecked")
    private void fireBeanEvent(String beanName, Object bean, int eventType) {
        List<BeanListener> list = null; // to avoid concurrent mod to listeners
        if (beanName != null) {
            list = new ArrayList<>();
            List<BeanListener> listeners = namedBeanListeners.get(beanName);
            if (listeners != null) {
                list.addAll(listeners);
            }
        }
        for (Map.Entry<Class, List<BeanListener>> entry : classBeanListeners.entrySet()) {
            if (list == null)
                list = new ArrayList<>();
            if (entry.getKey().isAssignableFrom(bean.getClass())) {
                List<BeanListener> listeners = entry.getValue();
                if (listeners != null)
                    list.addAll(listeners);
            }
        }
        if (list != null)
            list.forEach(beanListener -> notifyBeanListener(beanListener, bean, eventType));
    }

    private void notifyBeanListener(BeanListener beanListener, Object bean, int eventType) {
        switch (eventType) {
            case BEAN_INITIALIZED: beanListener.initialized(bean);
            break;
            case BEAN_STARTED: beanListener.started(bean);
            break;
            case BEAN_LAUNCHED: beanListener.launched(bean);
            break;
            default: throw new SystemException("Unrecognized eventType " + eventType);
        }
    }
}
