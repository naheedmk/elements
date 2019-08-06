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

package net.e6tech.elements.cassandra.driver.v4;

import com.datastax.oss.driver.api.core.AsyncPagingIterable;
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.ReadOptions;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.WriteOptions;
import net.e6tech.elements.cassandra.async.Async;
import net.e6tech.elements.cassandra.async.AsyncFutures;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.cassandra.etl.PrimaryKey;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.util.SystemException;

import java.util.*;

public class SibylV4 extends Sibyl {

    private MappingManager mappingManager;

    public MappingManager getMappingManager() {
        return mappingManager;
    }

    @Inject
    public void setMappingManager(MappingManager mappingManager) {
        this.mappingManager = mappingManager;
    }

    private WriteOptions writeOptions(WriteOptions userOptions) {
        WriteOptions options = WriteOptions.from(userOptions);
        return options.merge(getWriteOptions());
    }

    private ReadOptions readOptions(ReadOptions userOptions) {
        ReadOptions options = ReadOptions.from(userOptions);
        return options.merge(getReadOptions());
    }

    @Override
    public <T> T get(Class<T> cls, PrimaryKey primaryKey) {
        return mappingManager.getMapper(cls).get(primaryKey.getKeys());
    }

    @Override
    public <X> AsyncFutures<X, PrimaryKey> get(Collection<PrimaryKey> list, Class<X> cls, ReadOptions userOptions) {
        Async async = createAsync();
        Mapper<X> mapper = mappingManager.getMapper(cls);
        return async.accept(list, item ->
            mapper.getAsync(readOptions(userOptions), item.getKeys()).toCompletableFuture()
        );
    }

    @Override
    public <T> void save(Class<T> cls, T entity) {
        mappingManager.getMapper(cls).save(entity);
    }

    @Override
    public <T> void save(Class<T> cls, T entity, WriteOptions options) {
        mappingManager.getMapper(cls).save(options, entity);
    }

    @Override
    public <T> void delete(Class<T> cls, T entity) {
        mappingManager.getMapper(cls).delete(entity);
    }

    @Override
    public <X> AsyncFutures<Void, X> save(Collection<X> list, Class<X> cls, WriteOptions userOptions) {
        Async async = createAsync();
        Mapper<X> mapper = mappingManager.getMapper(cls);
        return async.accept(list, item -> mapper.saveAsync(writeOptions(userOptions), item).toCompletableFuture());
    }

    @Override
    public <X> X one(Class<X> cls, String query, Map<String, Object> map) {
        ResultSet resultSet = execute(query, map);
        com.datastax.oss.driver.api.core.cql.ResultSet rs = ((ResultSetV4) resultSet).unwrap();
        return mappingManager.getMapper(cls).one(rs);
    }

    @Override
    public <X> List<X> all(Class<X> cls, String query, Map<String, Object> map) {
        return mapAll(cls, execute(query, map));
    }

    @Override
    public <X> List<X> mapAll(Class<X> cls, ResultSet resultSet) {
        if (resultSet instanceof  ResultSetV4) {
            com.datastax.oss.driver.api.core.cql.ResultSet rs = ((ResultSetV4) resultSet).unwrap();
            return mappingManager.getMapper(cls).all(rs).all();
        } else {
            com.datastax.oss.driver.api.core.cql.AsyncResultSet rs = ((AsyncResultSetV4) resultSet).unwrap();
            MappedAsyncPagingIterable<X> iterable = mappingManager.getMapper(cls).all(rs);

            List<X> list = new LinkedList<>();
            AsyncIterator<X> asyncIterator = new AsyncIterator<>(iterable);
            while (asyncIterator.hasNext()) {
                list.add(asyncIterator.next());
            }
            return list;
        }
    }

    private static class AsyncIterator<X> implements Iterator<X> {
        MappedAsyncPagingIterable<X> pagingIterable;

        AsyncIterator(MappedAsyncPagingIterable<X> pagingIterable) {
            this.pagingIterable = pagingIterable;
        }

        private void prepareNextPage() {
            while (!pagingIterable.currentPage().iterator().hasNext()) {
                if (pagingIterable.hasMorePages()) {
                    try {
                        pagingIterable = pagingIterable.fetchNextPage().toCompletableFuture().get();
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }
                else break;
            }
        }

        @Override
        public boolean hasNext() {
            prepareNextPage();
            return pagingIterable.currentPage().iterator().hasNext();
        }

        @Override
        public X next() {
            return pagingIterable.currentPage().iterator().next();
        }

        @Override
        public void remove() {
            pagingIterable.currentPage().iterator().remove();
        }
    };
}
