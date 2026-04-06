package com.vk.VkGrpcCRUD.repository;

import com.vk.VkGrpcCRUD.entity.Kv;
import io.tarantool.client.box.TarantoolBoxClient;
import io.tarantool.client.box.options.SelectOptions;
import io.tarantool.core.protocol.BoxIterator;
import io.tarantool.mapping.SelectResponse;
import io.tarantool.mapping.Tuple;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;


@Repository
public class KvBoxRepository {

    private static final String SPACE_NAME = "KV";
    int BATCH_SIZE = 100;

    private final TarantoolBoxClient boxClient;

    public KvBoxRepository(TarantoolBoxClient boxClient) {
        this.boxClient = boxClient;
    }

    /**
     * get(key) - получение по ключу
     */
    public Optional<Kv> findByKey(String key) {
        SelectOptions options = SelectOptions.builder()
                .withIndex("primary")
                .build();
        var response = boxClient.space(SPACE_NAME)
                .select(Collections.singletonList(key), options)
                .join();

        List<Tuple<List<?>>> data = response.get();

        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapToKv(data.getFirst()));
    }

    /**
     * range(since, to) - получение диапазона
     */
    public CompletableFuture<Void> findRangeByKey(String startKey, String endKey, Consumer<Kv> consumer) {
        return processBatch(startKey, endKey, consumer, BoxIterator.GE);
    }

    private CompletableFuture<Void> processBatch(
            String currentKey,
            String endKey,
            Consumer<Kv> consumer,
            BoxIterator iterator
    ) {
        return fetchRange(currentKey, iterator).thenCompose(res -> {
            var batch = res.get();
            String last = getLastKey(batch);

            if (last == null || last.compareTo(endKey) >= 0) {
                batch.stream()
                        .map(this::mapToKv)
                        .takeWhile(key -> key.getKey().compareTo(currentKey) < 0)
                        .forEach(consumer);
                return CompletableFuture.completedFuture(null);
            }
            batch.stream()
                    .map(this::mapToKv)
                    .forEach(consumer);
            return processBatch(last, endKey, consumer, BoxIterator.GT);
        });
    }

    /**
     * delete(key)
     */
    public void deleteByKey(String key) {
        boxClient.space(SPACE_NAME)
                .delete(Collections.singletonList(key))
                .join();
    }

    /**
     * count()
     */
    public Long count() {
        var result = boxClient.eval(String.format("return box.space.%s.index.primary:count()", SPACE_NAME)).join().get();

        if (result == null || result.isEmpty()) return 0L;

        return ((Number) result.getFirst()).longValue();
    }

    /**
     * put(key, value) - сохранение или перезапись
     */
    public void save(Kv kv) {
        List<List<?>> operations = List.of(
                Arrays.asList("=", 1, kv.getValue())
        );

        // Метод replace заменяет существующий или вставляет новый (Upsert)
        boxClient.space(SPACE_NAME)
                .upsert(Arrays.asList(kv.getKey(), kv.getValue()), operations)
                .join();
    }

    /**
     * Вспомогательный метод маппинга Tuple<List<?>> в объект Kv
     */
    private Kv mapToKv(Tuple<List<?>> tuple) {
        List<?> fields = tuple.get();

        String key = (String) fields.get(0);
        byte[] value = (byte[]) fields.get(1);

        return new Kv(key, value);
    }

    private CompletableFuture<SelectResponse<List<Tuple<List<?>>>>> fetchRange(
            String startKey,
            BoxIterator iteratorType
    ) {
        SelectOptions options = SelectOptions.builder()
                .withIndex("primary")
                .withIterator(iteratorType)
                .withLimit(BATCH_SIZE)
                .build();

        return boxClient.space(SPACE_NAME)
                .select(
                        Collections.singletonList(startKey),
                        options
                );
    }

    private String getLastKey(List<Tuple<List<?>>> tuples) {
        if (tuples == null || tuples.isEmpty()) return null;
        var last = tuples.getLast().get();
        if (last == null || last.isEmpty()) return null;
        return last.getFirst().toString();
    }
}
