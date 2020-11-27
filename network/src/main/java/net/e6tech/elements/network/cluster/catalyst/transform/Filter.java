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

package net.e6tech.elements.network.cluster.catalyst.transform;

import net.e6tech.elements.network.cluster.catalyst.Mapping;
import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.util.stream.Stream;

@SuppressWarnings("squid:S00119")
public class Filter<Re extends Reactor, T> implements Transform<Re, T, T> {
    private static final long serialVersionUID = -8421079688575649162L;
    Mapping<Re, T, Boolean> mapping;

    public Filter(Mapping<Re, T, Boolean> mapping) {
        this.mapping = mapping;
    }

    @Override
    public Stream<T> transform(Re reactor, Stream<T> stream) {
        return stream.filter(t ->
                mapping.apply(reactor, t));
    }
}
