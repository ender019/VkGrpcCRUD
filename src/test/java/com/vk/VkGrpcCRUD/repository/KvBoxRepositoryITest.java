package com.vk.VkGrpcCRUD.repository;

import com.vk.VkGrpcCRUD.entity.Kv;
import com.vk.VkGrpcCRUD.testconfig.TarantoolTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class KvBoxRepositoryITest extends TarantoolTestConfig {

    @Autowired
    private KvBoxRepository repository;

    @Test
    @DisplayName("Должен сохранять и возвращать значение")
    void shouldPutAndGet() {
        String key = "test_key";
        byte[] value = "hello".getBytes();

        repository.save(new Kv(key, value));
        Optional<Kv> result = repository.findByKey(key);

        assertTrue(result.isPresent());
        assertArrayEquals(value, result.get().getValue());
    }

    @Test
    @DisplayName("Должен корректно работать с NULL значениями (Требование ТЗ)")
    void shouldHandleNullValue() {
        String key = "null_key";

        repository.save(new Kv(key, null));

        Optional<Kv> result = repository.findByKey(key);

        assertTrue(result.isPresent());
        assertNull(result.get().getValue(), "Значение в поле value должно быть null");
    }

    @Test
    @DisplayName("Должен перезаписывать значение для существующих ключей (Требование ТЗ)")
    void shouldOverwriteExistingKey() {
        String key = "overwrite_key";
        byte[] firstVal = "first".getBytes();
        byte[] secondVal = "second".getBytes();

        repository.save(new Kv(key, firstVal));
        repository.save(new Kv(key, secondVal)); // Перезапись

        Optional<Kv> result = repository.findByKey(key);
        assertArrayEquals(secondVal, result.get().getValue());
    }

    @Test
    @DisplayName("Должен возвращать корректное кол-во записей")
    void shouldReturnCorrectCount() {
        long initialCount = repository.count();

        repository.save(new Kv("c1", "v1".getBytes()));
        repository.save(new Kv("c2", "v2".getBytes()));

        assertEquals(initialCount + 2, repository.count());
    }

    @Test
    @DisplayName("Должен удалять запись")
    void shouldDeleteKey() {
        String key = "delete_me";
        repository.save(new Kv(key, "data".getBytes()));

        repository.deleteByKey(key);

        Optional<Kv> result = repository.findByKey(key);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Должен возвращать данные в диапазоне (Логика для 5 млн записей)")
    void shouldReturnRange() {
        repository.save(new Kv("a", "1".getBytes()));
        repository.save(new Kv("b", "2".getBytes()));
        repository.save(new Kv("c", "3".getBytes()));
        repository.save(new Kv("d", "4".getBytes()));

        List<Kv> collected = new ArrayList<>();

        repository.findRangeByKey("b", "c", collected::add).join();

        assertEquals(2, collected.size());
        assertEquals("b", collected.get(0).getKey());
        assertEquals("c", collected.get(1).getKey());
    }
}
