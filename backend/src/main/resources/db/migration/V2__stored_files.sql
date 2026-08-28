--
-- V2: documents stored in the database rather than on the container's disk.
--
-- The disk-backed store writes under ./storage, which any container platform
-- discards on the next restart or redeploy. The rows survived and the files did
-- not, so the console would list candidates whose documents no longer existed.
--
-- The key is the primary key, and it is the same key the disk store generates,
-- so the two implementations are interchangeable without rewriting a single
-- stored reference.
--
create table stored_files (
    storage_key       varchar(400)             not null,
    original_filename varchar(400)             not null,
    content_type      varchar(200),
    size_bytes        bigint                   not null,
    sha256            varchar(64)              not null,
    data              bytea                    not null,
    created_at        timestamp(6) with time zone not null,
    primary key (storage_key)
);
