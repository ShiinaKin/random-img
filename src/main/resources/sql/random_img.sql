create table if not exists images
(
    id          bigint                  not null
        primary key,
    uid         bigint      default 0   not null,
    pid         varchar(64) default '0' not null,
    url         varchar(255)            not null,
    path        varchar(128)            not null,
    create_time datetime                not null,
    update_time datetime                not null,
    is_deleted  tinyint(1)  default 0   not null,
    constraint uk_path
        unique (path)
);

create table if not exists post_images
(
    id          bigint               not null
        primary key,
    source      varchar(255)         not null comment 'uuid所属来源',
    post_id     char(36)             not null,
    image_id    bigint               not null,
    url         varchar(255)         not null,
    create_time datetime             not null,
    update_time datetime             not null,
    is_deleted  tinyint(1) default 0 not null,
    constraint uk_source_post_id
        unique (source, post_id)
);