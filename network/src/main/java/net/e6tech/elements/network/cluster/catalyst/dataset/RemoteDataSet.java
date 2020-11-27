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

package net.e6tech.elements.network.cluster.catalyst.dataset;

import net.e6tech.elements.network.cluster.catalyst.Catalyst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RemoteDataSet<E> implements DataSet<E> {
    private List<Segment<E>> segments = new ArrayList<>();

    public RemoteDataSet() {
    }

    public RemoteDataSet(Collection<? extends Segment<E>> segments) {
        this.segments.addAll(segments);
    }

    public RemoteDataSet<E> add(Segment<E> segment) {
        segments.add(segment);
        return this;
    }

    public RemoteDataSet<E> addAll(Collection<? extends Segment<E>> segments) {
        this.segments.addAll(segments);
        return this;
    }

    @Override
    public Segments<E> segment(Catalyst catalyst) {
        return new Segments<>(catalyst, segments);
    }

    @Override
    public Collection<E> asCollection() {
        return Collections.emptyList();
    }
}
