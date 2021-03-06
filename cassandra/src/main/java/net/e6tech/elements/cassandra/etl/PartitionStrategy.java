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

package net.e6tech.elements.cassandra.etl;

import net.e6tech.elements.cassandra.Session;
import net.e6tech.elements.cassandra.Sibyl;
import net.e6tech.elements.cassandra.async.AsyncPrepared;
import net.e6tech.elements.cassandra.driver.cql.Prepared;
import net.e6tech.elements.cassandra.driver.cql.ResultSet;
import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.TextBuilder;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public class PartitionStrategy<S extends Partition, C extends PartitionContext> implements BatchStrategy<S, C> {

    @Override
    public int load(C context, List<S> source) {
        if (context.getLoadDelegate() != null) {
            return context.getLoadDelegate().applyAsInt(source);
        }
        return 0;
    }

    /**
     * Return a map of partitions and count.  The partitions are sorted from small to large.
     * @param context PartitionContext
     * @return map of partition anc count
     */
    public Map<Comparable, Long> queryPartitions(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        AtomicReference<Map<Comparable, Long>> ref = new AtomicReference<>(new HashMap<>());
        List<Comparable> partitions = new ArrayList<>();
        String query = TextBuilder.using(
                "select ${pk}, count(*) from ${table} " +
                        "where ${pk} > ${start} and ${pk} < ${end} group by ${pk} allow filtering")
                .build("pk", partitionKey, "table", table,
                        "start", lastUpdate.getLastUpdate(), "end", end);
        context.open().accept(Resources.class, res -> {
            ResultSet rs = res.getInstance(Session.class).execute(query);
            List<Row> rows = rs.all();
            ref.set(new HashMap<>((int)(rows.size() * 1.4 + 16)));
            Map<Comparable, Long> map = ref.get();
            for (Row row : rows) {
                Comparable pk = (Comparable) row.get(0, context.getPartitionKeyType());
                map.put(pk, row.get(1, Long.class));
                partitions.add(pk);
            }
        });
        Map<Comparable, Long> map = ref.get();
        Collections.sort(partitions);
        Map<Comparable, Long> result = new LinkedHashMap<>(partitions.size() + 1, 1.0f);
        for (Comparable partition : partitions) {
            result.put(partition, map.get(partition));
        }
        return result;
    }

    @Override
    public List<S> extract(C context) {
        return context.open().apply(Sibyl.class, sibyl -> {
            String query = TextBuilder.using("select * from ${tbl} where ${pk} = :partitionKey")
                    .build("tbl", context.tableName(), "pk", context.getInspector().getPartitionKeyColumn(0));
            Prepared pstmt = context.getPreparedStatements().computeIfAbsent("extract",
                    key -> sibyl.getSession().prepare(query));
            AsyncPrepared<?> async = sibyl.createAsync(pstmt);
            for (Comparable hour : context.getPartitions()) {
                async.execute(bound -> bound.set("partitionKey", hour, (Class) hour.getClass()));
            }
            List<S> list = new ArrayList<>();
            async.inExecutionOrder(rs -> list.addAll(sibyl.mapAll(context.getSourceClass(), rs)));
            return list;
        });

    }

    @Override
    public int run(C context) {
        int importedCount = 0;
        context.initialize();
        Map<Comparable, Long> partitions = queryPartitions(context);  // partition key vs count

        logger.info("Extracting Class {} to {}", context.getSourceClass(), getClass());
        context.reset();

        List<Comparable> concurrent = new LinkedList<>();
        while (partitions.size() > 0) {
            LastUpdate lastUpdate = context.getLastUpdate();
            concurrent.clear();
            boolean first = true;
            long count = 0;
            for (Map.Entry<Comparable, Long> entry : partitions.entrySet()) {
                if (first) { // always add first regardless of batch size
                    concurrent.add(entry.getKey());
                    first = false;
                    count = entry.getValue();
                } else if (count + entry.getValue() <= context.getBatchSize()) {
                    count += entry.getValue();
                    concurrent.add(entry.getKey());
                } else {
                    break;
                }
            }

            importedCount += run(context, concurrent);
            for (Comparable partition : concurrent) {
                partitions.remove(partition);
                lastUpdate.update(partition);
            }
            context.saveLastUpdate(lastUpdate);
        }

        logger.info("Done loading {} instances of {}", importedCount, context.getSourceClass());
        context.reset();
        return importedCount;
    }

    public int run(C context, List<Comparable> partitions) {
        context.setPartitions(partitions);
        List<S> batchResults = extract(context);
        int processedCount = load(context, batchResults);
        if (logger.isInfoEnabled())
            logger.info("Processed {} instance of {}", processedCount, context.extractor());
        return processedCount;
    }

    public List<Comparable> queryRange(C context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        Comparable end = context.getCutoff();
        String partitionKey = context.getInspector().getPartitionKeyColumn(0);
        String table = context.tableName();

        List<Comparable> list = new LinkedList<>();
        // select distinct only works when selecting partition keys
        String query = TextBuilder.using(
                "select distinct ${pk} from ${table} " +
                        "where ${pk} > ${start} and ${pk} < ${end} allow filtering")
                .build("pk", partitionKey, "table", table,
                        "start", lastUpdate.getLastUpdate(), "end", end);
        context.open().accept(Resources.class, res -> {

            ResultSet rs = res.getInstance(Session.class).execute(query);
            for (Row row : rs.all()) {
                list.add((Comparable) row.get(0, context.getPartitionKeyType()));
            }
        });

        list.sort(null);
        return list;
    }

    public int runPartitions(C context) {
        context.initialize();
        List<Comparable> list = queryRange(context);

        List<Comparable> batch = new ArrayList<>(context.getBatchSize());
        for (Comparable c : list) {
            batch.add(c);
            if (batch.size() == context.getBatchSize()) {
                LastUpdate lastUpdate = context.getLastUpdate();
                runPartitions(batch, context);
                lastUpdate.update(batch.get(batch.size() - 1));
                context.saveLastUpdate(lastUpdate);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            LastUpdate lastUpdate = context.getLastUpdate();
            runPartitions(batch, context);
            lastUpdate.update(batch.get(batch.size() - 1));
            context.saveLastUpdate(lastUpdate);
            batch.clear();
        }
        return list.size();
    }

    public void runPartitions(List<Comparable> list, C context) {
        context.setPartitions(list);
        int processCount = context.getLoadDelegate().applyAsInt(list);
        if (logger.isInfoEnabled())
            logger.info("Processed {} partitions of {}", processCount, context.extractor());
    }
}
