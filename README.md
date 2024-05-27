## random-img

详细请见[博客](https://blog.sakurasou.io/archives/random-image-api)

### 项目介绍

使用SpringMVC搭配Kotlin使用，由于Spring5.2之后对于Kotlin Coroutines的支持，使得可以使用协程+命令式的风格实现响应式，具体请看这篇文章：[Going Reactive with Spring, Coroutines and Kotlin Flow](https://spring.io/blog/2019/04/12/going-reactive-with-spring-coroutines-and-kotlin-flow)。

后端早期使用WebFlux写过一个demo，功能实现后的代码实在是太丑陋了，于是作罢。

代码仓库：[random-img](https://github.com/ShiinaKin/random-img)

需要注意的是本项目没有提供可视化的UI，一切操作都通过直接发送请求进行，可以使用`curl`, `insomnia`, `postman`等工具。

### 一些说明

设计之初只考虑个人使用，因此只是用了固定用户名密码的Basic验证，也就不区分上传者。

图片个人全部使用 [PixivBatchDownloader](https://github.com/xuejianxianzun/PixivBatchDownloader) 从Pixiv上获取，也就按照p站的uid/pid来进行区分。

由于demo实验后发现Cloudflare Worker免费版的性能确实不足以支持大量的图片尺寸转换，因此在后端上传时对图片进行了预处理，除原图外分别存储了`1920px`, `1440px`, `128px`, `960px`, `640px`, `320px` 这几种宽度的`webp`格式。

### 部署

1. clone本项目，按需修改`docker-compose.yml`文件，参照配置部分
2. 运行`docker-compose up -d`或是`docker compose up -d`
3. （可选）自行配置反代

#### 配置

##### Basic

`BASIC_AUTH_USERNAME`：Basic验证携带的username

`BASIC_AUTH_PASSWORD`：Basic验证携带的原始密码的SHA256结果

##### S3

以Cloudflare R2为例，首先创建一个存储桶，假设取名为`images`

将`S3_BUCKET_NAME`的值填写为`images`，`S3_REGION`为`auto`

再创建一个存储桶，取名为`temp`，将`S3_MANUAL_UPLOAD_BUCKET_NAME`填写为`temp`

回到R2的主页，点击右上角的`管理R2 API令牌`，创建一个允许`对象读写`的，指定存储桶为`images`和`temp`的令牌，将创建后的`访问密钥 ID`和`机密访问密钥`填写为`S3_ACCESS_ID`和`S3_ACCESS_TOKEN`，并将`为 S3 客户端使用管辖权地特定的终结点`填写为`S3_ENDPOINT`

请参照Cloudflare Worker的[部署部分](https://blog.sakurasou.io/archives/random-image-api#Cloudflare-Worker)，将`wrangler.toml`文件的`route - pattern`值设置为`S3_CDN_URL`，如`https://images.your.domain`

##### PERSISTENCE_REFERER

这个设置项是一个String Array，如果请求携带完全相同的Referer，那么这个请求的PostId和随机图会被持久化到数据库中，保证之后的每次随机都是相同图片。

请填写去除所有`:`的Referer（因为会作为Redis的key）。

由于`compose file`的限制，如果有多个请用 `,` 隔开，而不是使用`yaml`的对应语法（未测试）。

例如：`https//a.domain.haha, https//b.domain.haha`

##### PROBABILITY

默认是没有使用的，无需管它，用法请见`io.sakurasou.dao.ImageDAO#randomSelImage`