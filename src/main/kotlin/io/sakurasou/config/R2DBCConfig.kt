package io.sakurasou.config
//
// import io.r2dbc.spi.ConnectionFactory
// import org.springframework.context.annotation.Bean
// import org.springframework.core.io.ClassPathResource
// import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator
// import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
// import org.springframework.r2dbc.connection.init.DatabasePopulator
// import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
// import reactor.core.publisher.Mono
//
// /**
//  * @author mashirot
//  * 2024/5/17 下午2:20
//  */
// class R2DBCConfig {
//     @Bean
//     fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer =
//         ConnectionFactoryInitializer().apply {
//             setConnectionFactory(connectionFactory)
//             setDatabasePopulator(
//                 CompositeDatabasePopulator().apply {
//                     addPopulators(ResourceDatabasePopulator(ClassPathResource("sql/random_img.sql")))
//                     addPopulators(imagesIdx())
//                     addPopulators(postImageIdx())
//                 }
//             )
//         }
//
//     fun imagesIdx() = DatabasePopulator { connection ->
//         Mono.from(
//             connection.createStatement(
//                 """
//                 SELECT COUNT(*)
//                 FROM information_schema.statistics
//                 WHERE table_name = 'images'
//                     AND index_name = 'idx_uid_is_deleted_pid_url_path_id';
//             """.trimIndent()
//             ).execute()
//         ).flatMap {
//             Mono.from(it.map { row, _ -> row.get(0, Int::class.java) })
//         }
//             .single()
//             .flatMap {
//                 if (it == 0) {
//                     Mono.from(
//                         connection.createStatement(
//                             """
//                             create index idx_uid_is_deleted_pid_url_path_id
//                                 on images (uid, is_deleted, pid, url, path, id);
//                         """.trimIndent()
//                         ).execute()
//                     ).then()
//                 } else {
//                     Mono.empty()
//                 }
//             }
//             .then()
//     }
//
//     fun postImageIdx() = DatabasePopulator { connection ->
//         Mono.from(
//             connection.createStatement(
//                 """
//                 SELECT COUNT(*)
//                 FROM information_schema.statistics
//                 WHERE table_name = 'post_images'
//                   AND index_name = 'idx_source_post_id_is_deleted_image_id_url';
//             """.trimIndent()
//             ).execute()
//         ).flatMap {
//             Mono.from(it.map { row, _ -> row.get(0, Int::class.java) })
//         }
//             .single()
//             .flatMap {
//                 if (it == 0) {
//                     Mono.from(
//                         connection.createStatement(
//                             """
//                             create index idx_source_post_id_is_deleted_image_id_url
//                                 on post_images (source, post_id, is_deleted, image_id, url);
//                         """.trimIndent()
//                         ).execute()
//                     ).then()
//                 } else {
//                     Mono.empty()
//                 }
//             }
//             .then()
//     }
// }