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

package net.e6tech.elements.cassandra.driver.v3;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.ReadOptions;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.WriteOptions;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.driver.cql.Bound;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.common.resources.Provision;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class SibylV3 extends Sibyl {

    public <X> X one(Class<X> cls, String query, Map<String, Object> map) {
        net.e6tech.elements.cassandra.driver.cql.ResultSet resultSet = execute(query, map);
        Result<X> result = getMapper(cls).map(((ResultSetV3) resultSet).unwrap());
        return result.one();
    }

    public <X> List<X> all(Class<X> cls, String query, Map<String, Object> map) {
        net.e6tech.elements.cassandra.driver.cql.ResultSet resultSet = execute(query, map);
        Result<X> result = getMapper(cls).map(((ResultSetV3) resultSet).unwrap());
        return result.all();
    }

    public MappingManager getMappingManager() {
        return getResources().getInstance(MappingManager.class);
    }

    public <T> void save(Class<T> cls, T entity) {
        getMapper(cls).save(entity);
    }

    public <T> void save(Class<T> cls, T entity, WriteOptions options) {
        getMapper(cls).save(entity, writeOptions(options));
    }

    public <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, WriteOptions userOptions) {
        Async async = createAsync();
        Mapper<X> mapper = getMapper(cls);
        return async.accept(list, item -> mapper.saveAsync(item, writeOptions(userOptions)));
    }

    private Mapper.Option[] writeOptions(WriteOptions userOptions) {
        WriteOptions options = WriteOptions.from(userOptions);
        LinkedList<Mapper.Option> mapperOptions = new LinkedList<>();

        if (options.consistency != null)
            mapperOptions.add(Mapper.Option.consistencyLevel(ConsistencyLevel.valueOf(options.consistency.name())));
        else if (getWriteOptions() != null && getWriteOptions().consistency != null)
            mapperOptions.add(Mapper.Option.consistencyLevel(ConsistencyLevel.valueOf(getWriteOptions().consistency.name())));

        if (options.ttl != null)
            mapperOptions.add(Mapper.Option.ttl(options.ttl));
        else if (getWriteOptions() != null && getWriteOptions().ttl != null)
            mapperOptions.add(Mapper.Option.ttl(getWriteOptions().ttl));

        if (options.saveNullFields != null)
            mapperOptions.add(Mapper.Option.saveNullFields(options.saveNullFields));
        else if (getWriteOptions() != null && getWriteOptions().saveNullFields != null)
            mapperOptions.add(Mapper.Option.saveNullFields(getWriteOptions().saveNullFields));

        if (options.ifNotExists != null)
            mapperOptions.add(Mapper.Option.ifNotExists(options.ifNotExists));
        else if (getWriteOptions() != null && getWriteOptions().ifNotExists != null)
            mapperOptions.add(Mapper.Option.ifNotExists(getWriteOptions().ifNotExists));

        return mapperOptions.toArray(new Mapper.Option[0]);
    }

    public <T> void delete(Class<T> cls, T entity) {
        getMapper(cls).delete(entity);
    }

    public <T> T get(Class<T> cls, PrimaryKey primaryKey) {
        return getMapper(cls).get(primaryKey.getKeys());
    }

    private Mapper.Option[] readOptions(ReadOptions userOptions) {
        LinkedList<Mapper.Option> mapperOptions = new LinkedList<>();
        ReadOptions options = ReadOptions.from(userOptions);
        if (options.consistency != null)
            mapperOptions.add(Mapper.Option.consistencyLevel(ConsistencyLevel.valueOf(options.consistency.name())));
        else if (getReadOptions() != null && getReadOptions().consistency != null)
            mapperOptions.add(Mapper.Option.consistencyLevel(ConsistencyLevel.valueOf(getReadOptions().consistency.name())));

        return mapperOptions.toArray(new Mapper.Option[0]);
    }

    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls, ReadOptions userOptions) {
        Async async = createAsync();
        Mapper<X> mapper = getMapper(cls);
        mapper.setDefaultGetOptions(readOptions(userOptions));
        return async.accept(list, k -> mapper.getAsync(k.getKeys()));
    }

    public <T> List<T> mapAll(Class<T> cls, ResultSet rs) {
        return getMapper(cls).map(((ResultSetV3) rs).unwrap()).all();
    }

    public <T> Mapper<T> getMapper(Class<T> cls) {
        return getMappingManager().mapper(cls);
    }

}
