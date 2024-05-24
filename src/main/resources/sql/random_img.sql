create table if not exists images
(
    id              bigint               not null
        primary key,
    uid             bigint               not null,
    pid             varchar(64)          not null,
    authority       varchar(64)          not null,
    original_width  int                  not null,
    original_path   varchar(128)         not null,
    width_1920_path varchar(128)         not null,
    width_1600_path varchar(128)         not null,
    width_1280_path varchar(128)         not null,
    width_960_path  varchar(128)         not null,
    width_640_path  varchar(128)         not null,
    width_320_path  varchar(128)         not null,
    create_time     datetime             not null,
    update_time     datetime             not null,
    is_deleted      tinyint(1) default 0 not null,
    constraint uk_path
        unique (original_path, is_deleted)
);
# ---
create table if not exists post_images
(
    id              bigint               not null
        primary key,
    origin          varchar(64)          not null comment 'postId所属来源',
    post_id         varchar(60)          not null,
    image_id        bigint               not null,
    query_condition varchar(64)          not null,
    url             varchar(160)         not null,
    create_time     datetime             not null,
    update_time     datetime             not null,
    is_deleted      tinyint(1) default 0 not null,
    constraint uk_source_post_id_img_id_query_cond_url
        unique (origin, post_id, image_id, query_condition, url)
);