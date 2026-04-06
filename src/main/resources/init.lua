-- Считываем переменные окружения, которые передали через Docker Compose
local user = os.getenv('TARANTOOL_USER_NAME') or 'admin'
local pass = os.getenv('TARANTOOL_USER_PASSWORD') or

-- Настройка памяти (для 5 000 000 записей выделим 2 ГБ, можно больше)
box.cfg{
    listen = 3301,
    memtx_memory = 2 * 1024 * 1024 * 1024, -- 2 GB RAM
    memtx_min_tuple_size = 16
}

-- Создание пользователя (если нужно отличаться от admin)
box.once("init_user", function()
    if not box.schema.user.exists(user) then
        box.schema.user.create(user, {password = pass})
    else
        box.schema.user.passwd(user, pass)
    end
    box.schema.user.grant(user, 'read,write,execute', 'universe', nil, {if_not_exists = true})
end)

-- Создание спейса KV и индекса
box.once("init_space", function()
    local kv = box.schema.space.create('KV', { if_not_exists = true })

    -- Определение схемы данных согласно заданию
    kv:format({
        { name = 'key',   type = 'string' },
        { name = 'value', type = 'varbinary', is_nullable = true }
    })

    -- Создание первичного индекса (TREE идеален для метода range)
    kv:create_index('primary', {
        parts = { 'key' },
        type = 'TREE',
        unique = true,
        if_not_exists = true
    })

    print("Space 'KV' initialized successfully")
end)
