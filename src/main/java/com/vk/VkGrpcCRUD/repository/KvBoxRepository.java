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


/**
 * Репозиторий для управления данными в спейсе "KV" базы данных Tarantool.
 * <p>
 * Реализует высокопроизводительные методы доступа к данным, адаптированные для работы
 * со структурами объемом более 5 000 000 записей. Основной упор сделан на
 * асинхронность и эффективное использование оперативной памяти при стриминге данных.
 * </p>
 *
 * @see <a href="https://github.com/tarantool/tarantool-java-sdk">Tarantool Java SDK</a>
 */
@Repository
public class KvBoxRepository {

    private static final String SPACE_NAME = "KV";

    /**
     * Размер пачки для пагинации при выполнении Range-запросов.
     * Значение 100 выбрано для баланса между сетевым оверхедом и потреблением памяти JVM.
     */
    private static final int BATCH_SIZE = 100;

    private final TarantoolBoxClient boxClient;

    /**
     * Конструктор репозитория.
     *
     * @param boxClient настроенный клиент Tarantool в режиме Box API.
     */
    public KvBoxRepository(TarantoolBoxClient boxClient) {
        this.boxClient = boxClient;
    }

    /**
     * Осуществляет поиск записи по первичному ключу.
     * <p>
     * Метод является блокирующим (использует {@code .join()}), что допустимо для точечных Get-запросов.
     * Корректно обрабатывает пустые результаты и значения {@code null} в поле value.
     * </p>
     *
     * @param key уникальный строковый идентификатор записи.
     * @return {@link Optional}, содержащий объект {@link Kv}, если ключ найден, иначе пустой Optional.
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
     * Выполняет асинхронный поиск диапазона ключей с использованием стриминга.
     * <p>
     * Для предотвращения OutOfMemoryError при работе с 5 000 000+ записей, данные
     * вычитываются из Tarantool пачками (Batching) и передаются в {@code Consumer} по мере поступления.
     * </p>
     *
     * @param startKey ключ, с которого начинается диапазон (включительно).
     * @param endKey   ключ, на котором диапазон заканчивается (включительно).
     * @param consumer обратный вызов для обработки каждой найденной записи (например, отправка в gRPC Stream).
     * @return {@link CompletableFuture<Void>}, который завершится по окончании обработки всего диапазона.
     */
    public CompletableFuture<Void> findRangeByKey(String startKey, String endKey, Consumer<Kv> consumer) {
        return processBatch(startKey, endKey, consumer, BoxIterator.GE);
    }

    /**
     * Рекурсивный метод для асинхронной обработки пачек данных.
     * <p>
     * Реализует конвейерную обработку: пока текущая пачка обрабатывается процессором,
     * драйвер запрашивает следующую порцию данных из сети.
     * </p>
     *
     * @param currentKey ключ для начала текущей итерации.
     * @param endKey     граница диапазона.
     * @param consumer   обработчик данных.
     * @param iterator   тип итератора Tarantool (GE для первой пачки, GT для последующих).
     * @return CompletableFuture для управления цепочкой вызовов.
     */
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
                        .takeWhile(key -> key.getKey().compareTo(endKey) <= 0)
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
     * Удаляет запись по ключу.
     *
     * @param key строковый ключ записи.
     */
    public void deleteByKey(String key) {
        boxClient.space(SPACE_NAME)
                .delete(Collections.singletonList(key))
                .join();
    }

    /**
     * Возвращает общее количество записей в спейсе.
     * <p>
     * Использует прямой вызов Lua-функции {@code count()} через индекс,
     * что обеспечивает сложность O(1) и мгновенный ответ на движке memtx.
     * </p>
     *
     * @return количество записей.
     */
    public Long count() {
        var result = boxClient.eval(String.format("return box.space.%s.index.primary:count()", SPACE_NAME)).join().get();

        if (result == null || result.isEmpty()) return 0L;

        return ((Number) result.getFirst()).longValue();
    }

    /**
     * Сохраняет данные. Реализует логику "Put": вставка новой записи или перезапись существующей.
     * <p>
     * Использует операцию {@code upsert}, которая на стороне сервера обновляет поле value
     * по индексу 1, если ключ уже существует.
     * </p>
     *
     * @param kv объект, содержащий ключ и (возможно пустое) значение.
     */
    public void save(Kv kv) {
        List<List<?>> operations = List.of(
                Arrays.asList("=", 1, kv.getValue())
        );

        boxClient.space(SPACE_NAME)
                .upsert(Arrays.asList(kv.getKey(), kv.getValue()), operations)
                .join();
    }

    /**
     * Преобразует внутренний кортеж Tarantool в доменную сущность {@link Kv}.
     *
     * @param tuple кортеж данных.
     * @return объект Kv.
     */
    private Kv mapToKv(Tuple<List<?>> tuple) {
        List<?> fields = tuple.get();
        String key = (String) fields.get(0);
        byte[] value = (byte[]) fields.get(1);
        return new Kv(key, value);
    }

    /**
     * Выполняет низкоуровневый запрос на получение пачки данных.
     */
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
                .select(Collections.singletonList(startKey), options);
    }

    /**
     * Извлекает строковое значение ключа из последнего кортежа пачки.
     */
    private String getLastKey(List<Tuple<List<?>>> tuples) {
        if (tuples == null || tuples.isEmpty()) return null;
        var last = tuples.getLast().get();
        if (last == null || last.isEmpty()) return null;
        return last.getFirst().toString();
    }
}
