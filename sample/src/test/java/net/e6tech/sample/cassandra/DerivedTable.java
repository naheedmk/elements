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

package net.e6tech.sample.cassandra;


import net.e6tech.elements.cassandra.annotations.ClusteringColumn;
import net.e6tech.elements.cassandra.annotations.PartitionKey;
import net.e6tech.elements.cassandra.annotations.Table;
import net.e6tech.elements.cassandra.etl.Partition;
import net.e6tech.elements.cassandra.etl.PartitionOrderBy;

@Table(name = "derived_table")
public class DerivedTable implements Partition {
    @PartitionKey
    Long creationTime;

    @ClusteringColumn
    Long id;

    Long value = 0L;

    public Long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Long creationTime) {
        this.creationTime = creationTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }
}
