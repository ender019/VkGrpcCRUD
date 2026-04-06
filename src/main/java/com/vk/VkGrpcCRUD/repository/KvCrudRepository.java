package com.vk.VkGrpcCRUD.repository;

import com.vk.VkGrpcCRUD.entity.Kv;
import io.tarantool.client.crud.Condition;
import io.tarantool.client.crud.TarantoolCrudClient;
import io.tarantool.client.crud.options.UpsertManyOptions;
import io.tarantool.mapping.Tuple;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class KvCrudRepository {
    private static final String SPACE_NAME = "KV";
    private final TarantoolCrudClient crudClient;

    public KvCrudRepository(TarantoolCrudClient crudClient) {
        this.crudClient = crudClient;
    }

    public Optional<Kv> findByKey(String key) {
        var result = crudClient.space(SPACE_NAME)
                .select(
                        List.of(Condition.create("=", "primary", Collections.singletonList(key))),
                        Kv.class
                )
                .join();
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.getFirst().get());
    }

    public List<Kv> findRangeByKey(String startKey, String endKey) {
        var result = crudClient.space(SPACE_NAME)
                .select(
                        List.of(Condition.create("=", "key", Collections.singletonList(startKey))),
                        Kv.class
                )
                .join();
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().map(Tuple::get).toList();
    }

    public void deleteByKey(String key) {
        crudClient.space(SPACE_NAME)
                .delete(Condition.create("=", "key", Collections.singletonList(key)))
                .join();
    }

    public Integer count() {
        return crudClient.space(SPACE_NAME)
                .count()
                .join();
    }

    public void save(Kv kv) {
        var opt = UpsertManyOptions.builder()
                .withFields("key")
                .stopOnError()
                .build();
        crudClient.space(SPACE_NAME).upsert(opt, kv).join();
    }
}
