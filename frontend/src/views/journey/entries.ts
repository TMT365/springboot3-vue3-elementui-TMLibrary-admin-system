/**
 * 学习经历 —— 内容数据。
 *
 * <h2>这些内容的来源</h2>
 * <p><b>全部是这个项目开发过程中真实踩过的坑</b>,没有一条是编的。每条都包含:
 * 症状(看到什么现象)、根因(为什么会这样)、解法(怎么修的)、代价(学到了什么)。</p>
 *
 * <p>为什么强调"真实的":学习经历的价值在于"下次遇到同样的问题能少走弯路",
 * 而编出来的问题没有这个价值 —— 它们不会被真正遇到。</p>
 *
 * <h2>为什么数据单独一个文件</h2>
 * <p>内容有上百行,和渲染逻辑混在一个 .vue 里会让组件难读。分开之后,
 * 以后加一条经历只需要改这里,不用碰模板。</p>
 */

/** 经历分类 —— 决定卡片配色和目录里的分组 */
export type JourneyCategory = 'tech' | 'arch' | 'skill'

export interface JourneyEntry {
  /** 锚点 id,目录跳转用 */
  id: string
  title: string
  category: JourneyCategory
  /** 关键词标签 */
  tags: string[]
  /** 症状 —— 现象是什么(用代码块或行内代码时用反引号) */
  symptom: string
  /** 根因 —— 为什么会这样 */
  cause: string
  /** 解法 —— 怎么修的 */
  solution: string
  /** 代价/收获 —— 一句话总结 */
  takeaway: string
  /** 相关代码片段(可选) */
  code?: { lang: string; content: string }
}

export const CATEGORY_META: Record<
  JourneyCategory,
  { label: string; color: string; icon: string; desc: string }
> = {
  tech: {
    label: '技术难点',
    color: '#f56c6c',
    icon: 'WarningFilled',
    desc: '卡住过、排查过、最后解决掉的问题',
  },
  arch: {
    label: '架构决策',
    color: '#9c27b0',
    icon: 'Share',
    desc: '选了什么、放弃了什么、为什么',
  },
  skill: {
    label: '技能习得',
    color: '#4caf50',
    icon: 'MagicStick',
    desc: '这次开发新掌握的东西',
  },
}

export const JOURNEY_ENTRIES: JourneyEntry[] = [
  // ============================================================
  // 技术难点
  // ============================================================
  {
    id: 'es-9x-client-api',
    title: 'Elasticsearch 9.x 的 Java 客户端,官方文档里的类根本不存在',
    category: 'tech',
    tags: ['Elasticsearch', 'Java Client', 'API 变更'],
    symptom:
      '按 8.x 的写法找 `RestClient.builder(HttpHost...)`,编译报错说找不到 HttpHost;' +
      '翻遍 `elasticsearch-java` 的 jar 包,里面既没有 RestClient 也没有 HttpHost。',
    cause:
      '9.x 把客户端内部结构改了:老的 `org.apache.http`(HttpClient 4)换成了 ' +
      '`org.apache.hc.client5`(HttpClient 5),而且 Elastic 不再单独发布 ' +
      '`elasticsearch-rest-client` 模块 —— 那个 GAV 在 Maven Central 直接 404。' +
      '更坑的是 `Rest5ClientBuilder` 在这个版本里是 package-private,业务代码 new 不出来。',
    solution:
      '改用高层公开入口 `ElasticsearchTransportConfig.Builder`:传 hosts(URI 列表)+ ' +
      'usernameAndPassword + JsonpMapper,它内部自己选传输实现。' +
      '这也是 9.x 官方推荐路径 —— 底层细节不该让业务层碰。',
    takeaway:
      '升大版本时先翻 jar 包看真实 API,别信搜索引擎里的旧代码 —— ' +
      '用 javap 把候选类的方法签名打出来,比读文档快。',
    code: {
      lang: 'java',
      content: `// ✗ 8.x 写法:9.x 编译不过
// RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();

// ✓ 9.x 的高层入口
ElasticsearchTransportConfig cfg = new ElasticsearchTransportConfig.Builder()
        .hosts(List.of(URI.create("http://localhost:9200")))
        .usernameAndPassword("elastic", password)
        .jsonMapper(new JacksonJsonpMapper())
        .build();
ElasticsearchClient client = new ElasticsearchClient(cfg.buildTransport());`,
    },
  },
  {
    id: 'ik-version-mismatch',
    title: 'IK 中文分词器:版本差一个 patch,ES 直接拒绝安装',
    category: 'tech',
    tags: ['Elasticsearch', 'IK', '插件'],
    symptom:
      '集群是 9.5.4,IK 官方源上最高只有 9.5.3。安装时被硬拒绝:' +
      '`Plugin [analysis-ik] was built for Elasticsearch version 9.5.3 but version 9.5.4 is running`。',
    cause:
      'ES 在 `PluginsUtils.verifyCompatibility` 里做**严格**版本断言 —— 不是"警告一下继续",' +
      '是直接抛异常。patch 号不同也拦。这是 ES 的保护机制:插件的字节码要调用 ES 内部的 Lucene API,' +
      '版本不匹配可能直接崩在运行期。',
    solution:
      '下载最接近的 IK 版本,把 `plugin-descriptor.properties` 里的 `elasticsearch.version` ' +
      '改成集群实际版本,重新打包安装。同一个 minor 版本内 API 面是稳定的,实测可用。' +
      '关键是**把这一步脚本化**(`scripts/install-ik.sh`),否则升级 ES 时没人记得做过什么。',
    takeaway:
      '绕过框架的保护性断言可以做,但必须:① 写清楚为什么安全 ② 脚本化以便重放 ' +
      '③ 记下回滚命令。藏在脑子里的一次性操作,下次升级就是事故。',
  },
  {
    id: 'chinese-tokenized-to-single-chars',
    title: '中文搜索搜出毫不相关的结果:「设计」命中了《深入理解计算机系统》',
    category: 'tech',
    tags: ['Elasticsearch', '中文分词', '相关性'],
    symptom:
      '搜索「设计」,结果里出现了《深入理解计算机系统》。两本书毫无关系。',
    cause:
      'ES 默认的 standard 分析器把中文**按字切分**:「设计」→ 设计两个单字 token。' +
      '而《深入理解计算机系统》的标题里有个「计」字 —— 多字段查询默认是 OR 语义,' +
      '命中任意一个 token 就算匹配。单字粒度下,常用字造成的假阳性会非常多。',
    solution:
      '分两步。**临时方案**:索引侧用 `ngram` 展开所有子串,查询侧加 ' +
      '`minimumShouldMatch: "100%"` —— 要求每个字都出现但不要求相邻,' +
      '这样「算导」能搜到《算法导论》而「设计」不会命中只有「计」的书。' +
      '**最终方案**:装 IK 分词器,索引用 `ik_max_word`(细粒度、提高召回)、' +
      '查询用 `ik_smart`(粗粒度、提高精度),按词匹配后那个 100% 的补丁就可以拆掉了。',
    takeaway:
      '中文搜索绕不开分词器。ngram 能"救急"但索引会膨胀(每个词的所有子串都进索引),' +
      '而且不理解为语义。IK 的词典分词才是正解。',
    code: {
      lang: 'java',
      content: `// 索引 mapping:索引侧细粒度,查询侧粗粒度
"title": {
  "type": "text",
  "analyzer": "ik_max_word",      // 建索引:尽量多切,提高召回
  "search_analyzer": "ik_smart"   // 查询:按词切,提高精度
}

// 实测对比
// ik_smart("深入理解计算机系统") → [深入, 理解, 计算机, 系统]   ✓
// standard("深入理解计算机系统") → [深, 入, 理, 解, 计, 算, 机, 系, 统]  ✗`,
    },
  },
  {
    id: 'mybatis-spring-boot-4',
    title: 'MyBatis 官方 starter 与 Spring Boot 4.x 不兼容,自动配置静默失效',
    category: 'tech',
    tags: ['MyBatis', 'Spring Boot 4', '自动配置'],
    symptom:
      'mapper 接口注入不进来,启动报找不到 SqlSessionFactory。但依赖明明加了,' +
      '启动日志里连一句报错都没有 —— 自动配置像是被跳过了。',
    cause:
      '双重不兼容。① `MybatisAutoConfiguration` 的 `@AutoConfigureAfter` 引用了 ' +
      'Spring Boot 3.x 的旧包路径,4.x 里那个类搬了家,导致条件不满足、自动配置静默跳过。' +
      '② 就算绕过自动配置直接调 `MybatisProperties.CoreConfiguration.applyTo()`,' +
      '它内部又调了 4.x 已删除的 `PropertyMapper.alwaysApplyingWhenNonNull()` —— ' +
      '整个 `mybatis-spring-boot-autoconfigure` 3.0.5 跟 4.x 是硬不兼容。',
    solution:
      '手写 `MyBatisConfig`:`new org.apache.ibatis.session.Configuration()` ' +
      '配好下划线转驼峰和 log-impl,再交给 `SqlSessionFactoryBean`;' +
      'mapper 扫描用 `MapperScannerConfigurer`。完全绕开 starter 的自动配置。',
    takeaway:
      '"启动没报错但功能不生效"通常比"启动失败"更难查 —— 自动配置是条件装配,' +
      '条件不满足就静默跳过。排查时看 `--debug` 的条件评估报告,别只看有没有异常。',
  },
  {
    id: 'xff-spoofing',
    title: 'IP 风控可以直接绕过:伪造一个请求头就能让封禁失效',
    category: 'tech',
    tags: ['安全', '反向代理', '限流'],
    symptom:
      '风控逻辑是"取 X-Forwarded-For 的第一段当客户端 IP"。看起来没问题 —— ' +
      '网关会把真实 IP 放在最前面。但压测时发现:每个请求换一个 XFF 值,计数永远到不了阈值。',
    cause:
      '`X-Forwarded-For` 是**客户端可以随便写的**请求头。应用直接采信它的第一段,' +
      '等于把"我是谁"的决定权交给了请求方:攻击者每次换一个值,风控就永远按不同 IP 计数。' +
      '更糟的是他还能填别人的 IP,把无辜的地址封掉。',
    solution:
      '只在**直连方是可信代理**时才读这个头,而且要从右往左扫:' +
      '跳过所有可信代理地址,取第一个不可信的 —— 那才是真正由我们自己的代理追加的段。' +
      '可信代理名单默认**为空**,即直连部署时完全不认这个头,这才是安全的默认值。',
    takeaway:
      '任何来自请求方的"身份声明"都必须先验证再采信。默认拒绝(不认头)' +
      '比默认信任安全得多 —— 前者坏了是"功能不生效",后者坏了是"安全边界消失"。',
    code: {
      lang: 'java',
      content: `// ✗ 无条件相信客户端
// return req.getHeader("X-Forwarded-For").split(",")[0];

// ✓ 只在直连方是可信代理时才读,且从右往左取第一个不可信的
if (!isTrustedProxy(req.getRemoteAddr())) return req.getRemoteAddr();
String[] hops = xff.split(",");
for (int i = hops.length - 1; i >= 0; i--) {
    if (!isTrustedProxy(hops[i].trim())) return hops[i].trim();
}
return req.getRemoteAddr();`,
    },
  },
  {
    id: 'log-injection',
    title: '日志里能凭空多出一行"系统日志"',
    category: 'tech',
    tags: ['安全', '日志', '输入校验'],
    symptom:
      '给请求链路加 traceId 时,想着"如果上游传了 X-Trace-Id 就直接沿用,方便跨服务串联"。' +
      '但这个值会原样写进日志 —— 而它是客户端可控的。',
    cause:
      '日志是**按行**解析的。如果传入的值里带换行符,攻击者就能在日志文件里凭空造出' +
      '一整行看起来像系统输出的记录(比如伪造一条 ERROR),把审计线索搅乱。' +
      '这叫 log injection,凡是"把外部输入写进日志"的地方都有这个风险。',
    solution:
      '两道防线。① 采信前用白名单正则卡死:`[A-Za-z0-9_-]{1,32}`,换行、空格、控制字符一律拒收, ' +
      '改用自己生成的 ID。② 日志格式里用 `%replace` 把消息中的换行转义成 ` | `,' +
      '这样即使用户在别的字段(比如 ISBN)里塞换行也伪造不出新行。',
    takeaway:
      '"日志"和"用户输入"是两个信任级别的东西,中间必须有一道转义。',
  },
  {
    id: 'password-in-source',
    title: '数据库密码明文躺在源码里,而且已经推到 GitHub 了',
    category: 'tech',
    tags: ['安全', '凭据管理', 'Git 历史'],
    symptom:
      '做生产就绪检查时扫了一遍仓库,发现 `RedisConfig.java` 里有一行被注释掉的 ' +
      '`config.setPassword(RedisPassword.of("真实密码"))`。是早期调试时留下的,' +
      '注释掉了但值是真的 —— 而且它已经在几个 commit 之前推上去了。',
    cause:
      '注释掉不等于删除。Git 保存的是每一次提交的完整快照,' +
      '把密码写进文件再注释掉,等于把它永久留在历史里 —— 克隆仓库的人 ' +
      '`git log -p` 就能看到。而且当时 DB 和 Redis 用的是**同一个密码**,泄露一次两个都完。',
    solution:
      '① 立刻轮换密码(已经泄露的凭据要按"已泄露"处理,清历史也救不回来)。' +
      '② 把代码里那行删掉,改成从环境变量读。' +
      '③ 加 `gitignore` 规则 + 写一份 `.env.example` 模板(只有键名没有值),' +
      '让"该配什么"和"值是什么"分开。',
    takeaway:
      '凭据永远不进代码库,一次都不行 —— 因为"删掉"这个动作在 Git 里不存在。' +
      '清理历史要用 filter-repo 重写所有 commit hash,代价远大于一开始就用环境变量。',
  },
  {
    id: 'atob-jwt-mojibake',
    title: '中文用户名在顶栏是乱码,在登录提示里却是好的',
    category: 'tech',
    tags: ['编码', 'JWT', 'Base64'],
    symptom:
      '用户名是中文时,顶栏右上角显示成 `å¼ ä¸‰` 这种东西。' +
      '但同一个用户登录成功时弹出的「欢迎,张三」完全正常 —— **同一个用户名,两处显示不一样**。',
    cause:
      '两处走的不是一条路。\n' +
      '登录提示用的是**响应体**,axios 拿 `JSON.parse` 解,UTF-8 天然正确;\n' +
      '顶栏用的是 **JWT**,前端自己 `atob()` 解 payload —— ' +
      '而 `atob()` 返回的是"一个字符 = 一个字节"的 **Latin-1** 字符串,' +
      'JWT 里却是 UTF-8 编码的 JSON。逐字节按 Latin-1 解读,中文就成了乱码(`张三` → `å¼ ä¸‰`)。',
    solution:
      '解 base64 之后不要直接 `JSON.parse`,中间插一步"按 UTF-8 解码":\n' +
      '`atob` → 逐字节转 `Uint8Array` → `TextDecoder("utf-8")` → 再 `JSON.parse`。',
    takeaway:
      '`atob`/`btoa` 是**字节级** API,不是文本级 API —— 凡是要和 UTF-8 打交道的地方都得自己转一道。' +
      'ASCII 下看不出问题,一旦出现非 ASCII 字符才会暴露。',
    code: {
      lang: 'typescript',
      content: `// ✗ 中文必乱码:atob 给的是 Latin-1
// return JSON.parse(atob(b64))

// ✓ 先还原成字节,再按 UTF-8 解码
function base64UrlDecode(input: string): string {
  const b64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(b64)
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}
return JSON.parse(base64UrlDecode(payload))`,
    },
  },
  {
    id: 'whitelist-prefix-drift',
    title: '验证码接口一直 401,因为白名单短了一截前缀',
    category: 'tech',
    tags: ['鉴权', '反向代理', '前后端契约'],
    symptom:
      '注册页一打开就弹「验证码加载失败: 未登录」。' +
      '直接 curl 后端验证码接口,返回 `401 {"msg":"缺少 Authorization 头"}` —— ' +
      '可验证码本来就是给未登录用户用的,要 token 就说不通了。',
    cause:
      '后端鉴权过滤器里有一张**免鉴权白名单**,当时是按 `"/captcha/"` 写的。' +
      '但前端走的是 **vite / nginx 的 `/api/*` 反向代理**,代理是**原样转发**、不会剥掉前缀,' +
      '所以后端真正收到的是 `/api/captcha/login` —— 拿它去匹配 `"/captcha/"`,永远匹配不上。\n' +
      '关键在于:controller 上的 `@RequestMapping("/captcha")` **没有** `/api`, ' +
      '这就让人误以为"路径里没有 /api",而过滤器看到的却是完整 URI。两边各看一半,谁都没错。',
    solution:
      '把白名单里的路径补全成**过滤器实际看到的形态**(带 `/api`):' +
      '`/api/captcha/`、`/api/users/login`、`/api/books` …;' +
      '同时给所有 controller 的类级 `@RequestMapping` 补上 `/api` 前缀,让两侧对齐。\n' +
      '注意 `/actuator/` 那条**不能**加 —— 它本来就不在 `/api` 下,加了反而会失效。',
    takeaway:
      '"改了代理规则"是个容易漏配套改动的动作:代理只负责转发,**路径前缀不会自动对齐**。' +
      '排查这类问题时,要在**过滤器、controller、代理**三处分别确认各自看到的路径,不能只看一处。',
  },
  {
    id: 'origin-scoped-theme',
    title: 'dev 和 preview 长得完全不一样,一度以为是 CSS 坏了',
    category: 'tech',
    tags: ['调试方法', 'localStorage', '构建产物'],
    symptom:
      '同一个项目,`npm run dev` 打开的页面和 `vite preview` 打开的页面**观感差别很大**' +
      '(层次感、透明度都不一样),很像是打包把样式搞坏了。',
    cause:
      '**逐条比对同一个元素在两个端口上的计算样式,结果完全一致** —— 连 box-shadow、' +
      'backdrop-filter 的值都一模一样。既然样式相同,差异就不可能来自打包。\n' +
      '真正的变量是**主题**:主题存在 `localStorage` 里,而 **localStorage 按 origin 隔离**。' +
      'dev 在 `:5173`、preview 在 `:4173`,是两个 origin —— 在一边点过主题切换,' +
      '另一边完全不知道,于是回退到系统偏好。两套配色对比强烈,看起来就像"样式崩了"。\n' +
      '(实测:`:5173` 存了 `dark`,同时 `:4173` 读出来是 `null` → 回退浅色。)',
    solution:
      '定位方法本身是可复用的:**先比计算样式,再怀疑构建**。\n' +
      '`getComputedStyle` 把两边同一元素的关键属性打出来逐条 diff —— ' +
      '相同就说明是环境差异(主题 / 缓存 / 数据),不同才去查打包。\n' +
      '顺带记一个相邻的坑:`vite preview` 服务的是 `dist/`,**不是源码**,改了代码不重新 build ' +
      '就会看到旧版本。\n' +
      '⚠️ **遗留**:主题差异能解释"观感不同",但没有拿到用户端确认,' +
      '所以这个问题**始终没有最终定论**,先按"环境差异"记录。',
    takeaway:
      '"看起来像样式问题"和"是样式问题"是两回事。' +
      '跨端口/跨环境比对时,先把**能确定的变量**(计算样式)钉死,再去找剩下的变量 —— ' +
      '否则很容易一头扎进打包配置里翻半天,而真正的原因在 localStorage。',
  },
  {
    id: 'paid-time-vs-updated-time',
    title: '「支付时间」不能拿 updated_time 顶替',
    category: 'arch',
    tags: ['数据库设计', '财务口径', '审计'],
    symptom:
      '订单列表要显示"下单时间"和"支付时间"。下单时间有现成的 `created_time`,' +
      '支付时间没这一列 —— 但表里有 `updated_time`(状态流转时会自动刷新),' +
      '看起来正好能当支付时间用。',
    cause:
      '`updated_time` 是"最后一次被改动的时刻",不是"支付时刻"。' +
      '支付之后任何操作(改地址、退款、人工干预、甚至修数据)都会把它顶掉。' +
      '而"什么时候付的钱"是**财务口径** —— 对账、报表、纠纷举证都靠它,' +
      '被后续操作覆盖掉就再也还原不出来了。',
    solution:
      '单独加一列 `paid_time`,只由 `PENDING → PAID` 那一次状态流转写入。' +
      '实现上是在状态守卫 SQL 里用条件写:`<if test="toStatus == 1">, paid_time = NOW()</if>` ' +
      '—— 同一条 SQL 也被取消/超时复用,只有付款那条路径才该落这个时间。',
    takeaway:
      '语义不同的时间字段不能复用。"最后一次修改"和"业务事件发生"是两件事,' +
      '前者是技术元数据,后者是业务事实 —— 业务事实必须独立落库。',
  },
  {
    id: 'cache-consistency',
    title: '缓存写路径:为什么是"先删缓存"而不是"更新缓存"',
    category: 'arch',
    tags: ['Redis', '缓存一致性', '并发'],
    symptom:
      '分类树是读多写少的数据,加 Redis 缓存后要决定写路径怎么做:' +
      '是把新值写进缓存(update),还是把缓存删掉等下次读时重建(delete)?',
    cause:
      '"更新缓存"在并发写下会丢数据:两个请求同时改同一份数据,' +
      'A 先写库后写缓存、B 后写库先写缓存,缓存的最终值就是 A 的旧值。' +
      '而"删除"是幂等的 —— 删两次、漏删(TTL 兜底)都不会写出错误数据。' +
      '删除还有第二个好处:懒加载,只重建真正被访问的 key。',
    solution:
      '写成 delete:而且**删在写库之前** —— 窗口更短。' +
      '不做延迟双删(第二次删要靠延时线程兜,复杂度不划算),' +
      '把残留的竞态窗口写进注释,留给后续接 MQ 订阅 binlog 在事务提交后统一失效。',
    takeaway:
      '缓存一致性没有"完美方案",只有"把窗口缩到多小 + 什么时候能自愈"。' +
      '关键是**把取舍写下来**,而不是假装它不存在。',
  },
  {
    id: 'search-degradation',
    title: '搜索依赖 ES,但 ES 挂了商城不能挂',
    category: 'arch',
    tags: ['高可用', '降级', '缓存'],
    symptom:
      '把搜索建议从 MySQL LIKE 换成 Elasticsearch 之后,新增了一个故障点:' +
      'ES 容器一停,搜索框就报错。而搜索是这个页面最显眼的功能之一。',
    cause:
      '引入了外部依赖,就引入了它的可用性上限。ES 是"搜索增强"而不是"业务真值" ——' +
      '数据的主副本在 MySQL,ES 只是个索引。既然如此,它挂掉不该让整个功能不可用。',
    solution:
      '三层组合,每层职责单一:\n' +
      '`Caching( Fallback( ES, MySQL ) )`\n' +
      '① 最外层 Redis:命中直接返回,热门前缀完全不碰 ES;\n' +
      '② 中间降级层:ES 抛异常就退到 MySQL LIKE,不让异常穿透到用户;\n' +
      '③ 启动期健康检查:连不上 ES 就把开关置为 false,连索引都不建。\n' +
      '三层各自独立,换任意一层不影响其它两层。',
    takeaway:
      '降级不是"加个 try-catch",而是**先想清楚哪个是主、哪个是增强**。' +
      '主数据必须有一个不依赖增强组件的读取路径。',
  },
  {
    id: 'captcha-unbound-from-username',
    title: '把验证码和用户名绑在一起,结果图根本不显示',
    category: 'arch',
    tags: ['验证码', '设计反转', '用户体验'],
    symptom:
      '登录页打开后,验证码的位置一直空着,提示「请先输入用户名」—— ' +
      '要先把用户名敲进去,图才会出来。更别扭的是:输一个字符它就重新拉一张,' +
      '输完用户名往往已经换了三四张图。',
    cause:
      '一开始的绑定是"有意为之",理由也写得挺像回事:\n' +
      '① 防止 A 的验证码被 B 拿去用 —— 于是把 username 编进了 Redis key;\n' +
      '② 用户名变了就该换图 —— 于是前端 `watch` 用户名,debounce 后重新拉。\n' +
      '但这两条都经不起推敲:\n' +
      '**① 挡不住真正的攻击**。攻击者完全可以拿受害者的用户名去申请一张图 —— ' +
      '验证码的安全边界是 **uuid**(申请和提交都要带上、122 位随机),不是用户名。\n' +
      '**② 把加载时机绑死在"用户输入"上**。进页面时用户名为空,`if (!val) return` ' +
      '直接把加载拦掉了,于是图不出来;而用户每敲一个字都触发一次网络请求,纯浪费。\n' +
      '还有一条副作用:中文用户名会被原样编进 Redis key,' +
      '运维最常用的 `KEYS tmlibrary:captcha:*` 打出来是乱码。',
    solution:
      '**把验证码彻底解绑用户名** —— 触发时机只留三个:mount、用户点图、倒计时归零。\n' +
      '前端去掉 `watch(username)` 和 `:username` prop;' +
      '后端 Redis key 从 `captcha:login:{username}:{uuid}:code` 收成 `captcha:login:{uuid}:code`,' +
      '并删掉按用户名批量清理的 pattern 和 `clearCaptchasForUsername`。\n' +
      '顺带发现一处**顺带被修出来的坑**:value 里那个"防御性比对 username"的检查,' +
      '在解绑后会**必然失败** —— 因为拉图时用户名还是空的,提交时却拿着真名去比,' +
      '结果是每次登录都 400。改成"只在 value 里存了用户名时才比",' +
      '保留了这层校验对其它调用方的价值。',
    takeaway:
      '安全设计要问一句"**这到底挡住了什么**"。绑 username 挡不住任何真实攻击,' +
      '却实打实地牺牲了可用性 —— 这种"看起来更安全"的设计最容易通过评审。\n' +
      '还有:**改动一个被多处依赖的约定(key 结构、加载时机)时,要把所有调用点一起过一遍**,' +
      '包括那些"看起来只是防御性"的检查 —— 它们同样会因为约定改变而失效。',
  },

  // ============================================================
  // 技能习得
  // ============================================================
  {
    id: 'xml-comment-trap',
    title: 'XML 注释里不能出现连续两个短横 —— 一个下午的坑',
    category: 'skill',
    tags: ['XML', 'Logback', '细节'],
    symptom:
      '日志配置写完,应用起不来,报 `The string "--" is not permitted within comments`,' +
      '定位到某一行注释。',
    cause:
      'XML 规范规定注释内容里不能出现 `--`。而我恰好在那行注释里写了 ' +
      '`%X{traceId:--}`(当时想解释 traceId 缺失时的占位符写法)—— 注释被提前终止了。' +
      '同一个坑我在 CSS 里也踩过一次:注释里写 `*/` 让样式表语法错误。',
    solution:
      '把注释里的 `--` 换成文字描述("两个短横")。另外写了个小脚本,提交前扫描 ' +
      'XML/CSS 注释里有没有非法序列。',
    takeaway:
      '"注释里写代码示例"是个高频陷阱 —— 每种注释语法都有自己的禁用序列。' +
      '代码示例放在文档里,别放在注释里。',
  },
  {
    id: 'logback-not-bash',
    title: 'Logback 的 ${} 不是 bash',
    category: 'skill',
    tags: ['Logback', '配置', '踩坑'],
    symptom:
      '想把 `tmlibrary.log` 变成 `tmlibrary-error.log`,顺手写了 `${LOG_FILE_PATH%.log}-error.log` ' +
      "(bash 的去后缀语法)。启动报 `Formatting string [.] should not end with '.'`。",
    cause:
      'Logback 的 `${}` 是它自己的属性替换语法,`%` 开头会被当成格式化符。' +
      'bash 的字符串操作在那里面一个都不支持 —— `${VAR%.x}`、`${VAR/a/b}` 都不行。',
    solution:
      '声明一个独立的属性 `LOG_ERROR_FILE_PATH`,别想着从另一个属性派生。' +
      '配置语言不是 shell,该重复就重复。',
    takeaway:
      '每套配置语言都有自己的表达式语法,长得像不代表行为一样。' +
      '看到 `${}` 先确认是谁在解析它。',
  },
  {
    id: 'enterprise-logging',
    title: '从"能打日志"到"企业级日志",中间差的是 traceId',
    category: 'skill',
    tags: ['日志', '可观测性', 'MDC'],
    symptom:
      '日志文件里什么都有,但线上出问题时看不懂:' +
      '"09:38:10 有个 500" —— 同一秒有几十个请求,没法知道哪几行属于同一次调用。',
    cause:
      '日志是**按时间**排列的,而排查需要的是**按请求**聚合。缺的是关联标识。',
    solution:
      '加一个最高优先级的过滤器,每个请求生成一个 traceId 放进 MDC,' +
      '日志格式里用 `%X{traceId}` 输出,响应头也回写一份 ——' +
      '用户报错时能直接报出 ID。配套的还有:异步 appender(业务线程不等磁盘)、' +
      'ERROR 单独落文件(告警只盯一个文件)、屏蔽框架启动噪音。',
    takeaway:
      '日志的"级"体现在:能不能从一行日志还原一次完整调用。' +
      'traceId 是那个把散落日志串起来的东西,没有它,日志再多也是噪音。',
    code: {
      lang: 'java',
      content: `// 每个请求:生成/沿用 traceId → 写 MDC → 回写响应头
MDC.put("traceId", traceId);
resp.setHeader("X-Trace-Id", traceId);
try {
    chain.doFilter(req, resp);
} finally {
    MDC.remove("traceId");   // ★ 线程复用,不清理会张冠李戴
}

// 日志格式
// %d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:--}] [%thread] %logger{40} - %msg`,
    },
  },
  {
    id: 'process-hygiene',
    title: '验证完不关服务,把用户挡住了',
    category: 'skill',
    tags: ['工程习惯', '协作', '端口'],
    symptom:
      '用户启动项目时看到 `Web server failed to start. Port 8080 was already in use` ' +
      '—— 因为我的验证用后端还在后台跑着。',
    cause:
      '把"后台跑着的服务"当成无害的。实际上它占着端口,直接挡住了用户自己的工作。' +
      'WSL2 上只有一个开发环境,不存在"多套并行互不干扰"。' +
      '更隐蔽的是:只 kill 父进程不够,maven 派生的子进程还占着端口。',
    solution:
      '验证完立刻关,并且**确认端口真的空了**(`ss -ltn | grep :8080`),' +
      '而不只是"我执行了 kill"。确实需要留着的时候,主动说清楚"我留着 8080,你用完告诉我"。',
    takeaway:
      '临时资源也要有生命周期。这个坑在同一次会话里犯了两次 ——' +
      '所以最终把它写进了项目记忆,而不是指望下次记得。',
  },
]
