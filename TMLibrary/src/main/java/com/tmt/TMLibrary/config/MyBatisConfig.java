package com.tmt.TMLibrary.config;

import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// 注意:org.springframework.context.annotation.Configuration 已被上面的 import 同名覆盖,
// 这里用全限定名引用 MyBatis 原生 Configuration 以避免歧义(踩坑记录 #5 同款)

/**
 * 手写 MyBatis 配置 —— 绕过 {@code mybatis-spring-boot-starter 3.0.5} 与 Spring Boot 4.x 的版本不兼容。
 *
 * <h3>根因(双重)</h3>
 * <ol>
 *   <li>{@code MybatisAutoConfiguration} 标注的 {@code @AutoConfigureAfter} 引用了
 *       Spring Boot 3.x 的旧包路径 {@code org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration},
 *       在 Spring Boot 4.x 下该类已搬到 {@code org.springframework.boot.jdbc.autoconfigure},导致自动配置静默跳过。</li>
 *   <li>即便绕过自动配置直接调 {@code MybatisProperties.CoreConfiguration.applyTo()},
 *       它内部又会调用 {@code PropertyMapper.alwaysApplyingWhenNonNull()},该方法在
 *       Spring Boot 4.x 已被移除 —— 整个 mybatis-spring-boot-autoconfigure 3.0.5
 *       与 Spring Boot 4.x 不兼容。</li>
 * </ol>
 *
 * <h3>修复策略</h3>
 * 只用 {@link MybatisProperties#getMapperLocations()} 和 {@link MybatisProperties#getTypeAliasesPackage()}
 * 这两个不依赖 4.x 移除 API 的 getter,配置直接由 Spring Boot 注入。
 * {@code mybatis.configuration.*} (map-underscore-to-camel-case 等) 通过手动 new {@link Configuration}
 * + setLogImpl 应用,绕开 {@code applyTo()} 内的 SB 4.x 已删 API。
 *
 * <h3>SQL 日志为什么这里手动设</h3>
 * application.yml 里 {@code mybatis.configuration.log-impl: StdOutImpl} 本意是让 MyBatis 把生成的 SQL
 * 打到控制台。但因为自动配置被跳过、{@code applyTo()} 不能调,这个配置**从 yml 到生效的链路完全断了**。
 * 这里直接 new 原生 {@link Configuration} 并 {@code setLogImpl()},不依赖 starter。
 *
 * BookMapper.xml 已用显式 {@code <resultMap>} 映射列名 → 属性名,不需要全局驼峰转换。
 *
 * <h3>为什么需要 proxyBeanMethods=false</h3>
 * {@code MapperScannerConfigurer} 实现了 {@code BeanDefinitionRegistryPostProcessor},
 * Spring 要求返回此类的方法必须是 {@code static} 或者所在 @Configuration 类标注
 * {@code proxyBeanMethods=false}。这里用后者更简洁。
 *
 * <h3>移除条件</h3>
 * MyBatis 官方适配 Spring Boot 4.x 后(参考其 GitHub issue tracker),可删除本类恢复自动配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MybatisProperties.class)
public class MyBatisConfig {

    /**
     * SQL 日志实现类,默认 StdOutImpl(打印到控制台)。
     * 在 application.yml 里配 {@code mybatis.log-impl} 可覆盖;不写则走这里默认。
     */
    @Value("${mybatis.log-impl:org.apache.ibatis.logging.stdout.StdOutImpl}")
    private String logImplClassName;

    /** @brief log-impl 找不到/未配时回退到 StdOutImpl(测试环境 @Value 偶尔返回 null) */
    private static final String DEFAULT_LOG_IMPL = "org.apache.ibatis.logging.stdout.StdOutImpl";

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisProperties properties) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // resolveMapperLocations 会按 application.yml 里 mybatis.mapper-locations 配置加载 XML
        factoryBean.setMapperLocations(properties.resolveMapperLocations());
        factoryBean.setTypeAliasesPackage(properties.getTypeAliasesPackage());

        // 手动应用 log-impl:new 原生 Configuration 绕过 starter.applyTo() 的 SB 4.x 不兼容
        org.apache.ibatis.session.Configuration ibatisConfig = new org.apache.ibatis.session.Configuration();
        String impl = (logImplClassName == null || logImplClassName.isBlank())
                ? DEFAULT_LOG_IMPL
                : logImplClassName;
        try {
            Class<?> clazz = Class.forName(impl);
            ibatisConfig.setLogImpl((Class<? extends Log>) clazz);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "mybatis.log-impl 指定的类不存在: " + impl, e);
        }
        factoryBean.setConfiguration(ibatisConfig);

        return factoryBean.getObject();
    }

    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.tmt.TMLibrary.mapper");
        return configurer;
    }
}