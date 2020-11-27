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

package net.e6tech.elements.network.cluster.catalyst.scalar;

import net.e6tech.elements.network.cluster.catalyst.Reactor;

import java.io.Serializable;
import java.util.Collection;

@SuppressWarnings("squid:S00119")
public class Reduce<Re extends Reactor, T, R> extends Scalar<Re, T, R, R> {

    private static final long serialVersionUID = 2541978434272522759L;

    public Reduce() {
    }

    public Reduce(ReduceOp<R> reduce) {
        setMapping((reactor, collection) -> reduce.reduce(collection));
    }

    @FunctionalInterface
    public interface ReduceOp<R> extends Serializable {
        default R reduce(Collection<R> collection) {
            R accumulator = null;
            boolean first = true;
            for (R r : collection) {
                if (r == null)
                    continue;
                if (first) {
                    first = false;
                    accumulator = r;
                } else {
                    accumulator = reduce(accumulator, r);
                }
            }
            return accumulator;
        }

        R reduce(R t1, R t2);
    }

}
