# Key-Value gRPC Service (Tarantool 3.2 + Java 21)

Высокопроизводительный gRPC микросервис на стеке Spring Boot 3, оптимизированный для работы с большими объемами данных (5 000 000+ записей) в БД Tarantool.

## 🎯 Реализованное API

Согласно техническому заданию, сервис реализует следующие методы:
*   `put(key, value)` — сохранение или перезапись (Upsert). Поддерживает **null** значения.
*   `get(key)` — получение значения по ключу.
*   `delete(key)` — удаление записи.
*   `range(key_since, key_to)` — **gRPC stream** пар ключ-значение из диапазона.
*   `count()` — получение общего количества записей в БД.

## 🚀 Ключевые архитектурные решения

### 1. Обработка Big Data (5 млн записей)
Для обеспечения стабильности при просмотре больших диапазонов реализован **асинхронный рекурсивный стриминг (Pipelining)**:
*   Данные вычитываются из Tarantool пачками (Batch Size = 100).
*   Используется неблокирующий итератор `GT` (Greater Than) для последовательного доступа.
*   Пока CPU мапит текущую пачку в gRPC-ответ, сетевой драйвер в фоне запрашивает следующую пачку из БД. Это исключает `OutOfMemoryError` и снижает Latency.

### 2. Полный асинхронный стек
Весь путь запроса является неблокирующим:
*   **gRPC**: Использование Netty Event Loop.
*   **Java**: Обработка через `CompletableFuture` и `thenCompose` (защита от переполнения стека).
*   **Tarantool SDK**: Асинхронный бинарный протокол.

### 3. Схема данных и индексация
*   **Спейс**: `KV` (движок memtx).
*   **Формат**: `{key: string, value: varbinary (nullable)}`.
*   **Индекс**: `primary` типа `TREE` по полю `key`. Позволяет выполнять Range-запросы за $O(\log N)$.

## 🛠 Технологии
*   **Java 21** & **Spring Boot 3.4.2**
*   **Tarantool 3.2.x** + **Tarantool Java SDK 1.5.0**
*   **gRPC Spring Boot Starter** (v3.1.0.RELEASE)
*   **Docker & Docker Compose** (с настроенными Healthchecks)
*   **Testcontainers** (интеграционное тестирование)

## 📂 Структура проекта
```text
VKGRPCCRUD/
├── src/main/proto/            # Контракт gRPC (Protobuf)
├── src/main/java/.../config/  # Настройки подключения и gRPC-сервера
├── src/main/java/.../entity/  # Доменная модель (Kv)
├── src/main/java/.../repository/ # Асинхронный репозиторий (Box API)
├── src/main/java/.../service/    # Реализация методов gRPC
├── src/main/resources/        # application.yaml и init.lua
└── src/test/java/             # Интеграционные тесты (Tarantool + gRPC)
```

## 🔧 Запуск и развертывание

### 1. Настройка переменных окружения
Приложение настроено на работу через переменные окружения (см. `docker-compose.yml`):
*   `TARANTOOL_USER_NAME` / `TARANTOOL_USER_PASSWORD`
*   `TARANTOOL_HOST` / `TARANTOOL_PORT`

### 2. Запуск всей инфраструктуры (Docker)
```bash
docker-compose up --build -d
```
*Tarantool инициализируется через `init.lua`, создавая пользователя и спейс. Сервис запустится автоматически после прохождения базой Healthcheck (`tt status`).*

### 3. Ручной запуск (для разработки)
Если Tarantool запущен локально:
```bash
mvn clean compile
mvn spring-boot:run
```

## 🧪 Тестирование
В проекте реализованы полноценные интеграционные тесты с использованием **Testcontainers**. Тесты проверяют:
*   Корректную передачу `null` значений между Java и Tarantool.
*   Логику перезаписи ключей (Upsert).
*   Стриминг диапазона через gRPC Stub.

Запуск тестов:
```bash
mvn test
```

## 📈 Взаимодействие с сервисом
Для тестирования gRPC методов можно использовать **Postman** (Import Proto) или **Evans CLI**:
```bash
evans --proto src/main/proto/kv_service.proto --port 9091 repl
```

---
**Разработано как демонстрация владения асинхронным стеком Java и базами данных семейства NoSQL.**
