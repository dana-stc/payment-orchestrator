create table payments (
    id              uuid         primary key,
    idempotency_key varchar(128) not null,
    merchant_id     varchar(64)  not null,
    amount          numeric(19, 4) not null,
    currency        varchar(3)   not null,
    state           varchar(32)  not null,
    provider        varchar(32),
    provider_ref    varchar(128),
    created_at      timestamptz  not null,
    version         bigint       not null,
    constraint uq_payments_idempotency_key unique (idempotency_key)
);

create table payment_transitions (
    payment_id  uuid         not null references payments (id),
    position    int          not null,
    from_state  varchar(32),
    to_state    varchar(32)  not null,
    reason      varchar(512),
    occurred_at timestamptz  not null,
    primary key (payment_id, position)
);

create table outbox_events (
    id           uuid        primary key,
    aggregate_id uuid        not null,
    type         varchar(64) not null,
    payload      text        not null,
    created_at   timestamptz not null,
    published_at timestamptz
);

create index idx_outbox_unpublished on outbox_events (created_at) where published_at is null;
