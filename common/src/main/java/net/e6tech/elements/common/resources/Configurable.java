/*
 * Copyright 2017 Futeh Kao
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

import net.e6tech.elements.common.reflection.Annotator;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by futeh.
 */
public interface Configurable<U> {

    Configurator configurator();

    U configurable();

    default U annotation(Class<? extends Annotation> key) {
        configurator().annotation(key);
        return configurable();
    }

    default <T> T get(String key) {
        return configurator().get(key);
    }

    default <T> T get(String key, T defval) {
         return configurator().get(key, defval);
    }

    default  <T> T get(Class<T> key) {
        return configurator().get(key);
    }

    default  <T> T get(Class<T> key, T defval) {
        return configurator().get(key);
    }

    default <T> T computeIfAbsent(String key, Function<String, T> mappingFunction) {
        return configurator().computeIfAbsent(key, mappingFunction);
    }

    default <T> T computeIfAbsent(Class<T> key, Function<Class<T>, T> mappingFunction) {
        return configurator().computeIfAbsent(key, mappingFunction);
    }

    default <T extends Annotation> U annotate(Class<T> cls, BiConsumer<Annotator.AnnotationValue, T> consumer) {
        configurator().annotate(cls, consumer);
        return configurable();
    }

    default <V, T extends Annotation> U annotate(Class<T> cls, Function<T, V> func, V value) {
        return annotate(cls, ((annotationValue, annotation) -> annotationValue.set(() -> func.apply(annotation), value)));
    }

    default <T extends Annotation> ConfigurableAnnotator<U, T> annotate(Class<T> cls) {
        return new ConfigurableAnnotator<>(cls, this);
    }

    default <T> U put(Class<T> cls, T instance) {
        configurator().put(cls, instance);
        return configurable();
    }

    default U put(String key, Object value) {
        configurator().put(key, value);
        return configurable();
    }

    default U putAll(Configurator configurator) {
        configurator().putAll(configurator);
        return configurable();
    }

    default U putAll(Map map) {
        configurator().putAll(map);
        return configurable();
    }

    class ConfigurableAnnotator<U, T extends Annotation> implements Configurable<U> {
        Class<T> annotationClass;
        Configurable<U> configurable;

        ConfigurableAnnotator(Class<T> annotationClass, Configurable<U> configurable) {
            this.annotationClass = annotationClass;
            this.configurable = configurable;
        }

        public Configurator configurator() {
            return configurable.configurator();
        }

        public U configurable() {
            return configurable.configurable();
        }

        public <V> ConfigurableAnnotator<U, T> annotate(Function<T, V> func, V value) {
            this.annotate(annotationClass, func, value);
            return this;
        }
    }
}
