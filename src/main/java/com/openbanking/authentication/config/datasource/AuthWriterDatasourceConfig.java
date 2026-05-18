package com.openbanking.authentication.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "authWriterEntityManagerFactory",
        transactionManagerRef = "authWriterTransactionManager",
        basePackages = {"com.openbanking.authentication.repository.writer"})
@EntityScan(basePackages = "com.openbanking.authentication.entities")
public class AuthWriterDatasourceConfig {

    @Value(value = "${spring.jpa.hibernate.show-sql}")
    private boolean showSQL;
    @Value(value = "${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    // Auth Writer Database
    @Primary
    @Bean(name = "authWriterDbDataSourceProperties")
    @ConfigurationProperties("spring.datasource.auth-writer-db")
    public DataSourceProperties authWriterDbDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "authWriterDbHikariConfig")
    @ConfigurationProperties("spring.datasource.auth-writer-db.hikari")
    public HikariConfig authWriterDbHikariConfig() {
        return new HikariConfig();
    }

    @Primary
    @Bean(name = "authWriterDb")
    public DataSource authWriterDbDataSource(
            @Qualifier("authWriterDbDataSourceProperties") DataSourceProperties authWriterDbDataSourceProperties,
            @Qualifier("authWriterDbHikariConfig") HikariConfig authWriterDbHikariConfig) {
        authWriterDbHikariConfig.setJdbcUrl(authWriterDbDataSourceProperties.getUrl());
        authWriterDbHikariConfig.setUsername(authWriterDbDataSourceProperties.getUsername());
        authWriterDbHikariConfig.setPassword(authWriterDbDataSourceProperties.getPassword());

        return new HikariDataSource(authWriterDbHikariConfig);
    }

    @Primary
    @Bean(name = "authWriterDbJdbcTemplate")
    public JdbcTemplate authWriterDbJdbcTemplate(@Qualifier("authWriterDb") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "authWriterEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean authWriterEntityManagerFactory(
            @Qualifier("authWriterDb") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPersistenceUnitName("authWriter");
        em.setPackagesToScan("com.openbanking.authentication.entities");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        // JPA Properties for Hibernate (Naming Strategy and Show SQL)
        Map<String, Object> jpaProperties = new HashMap<>();
        jpaProperties.put("hibernate.hbm2ddl.auto", ddlAuto);
        jpaProperties.put("hibernate.show_sql", showSQL);
        jpaProperties.put("hibernate.format_sql", showSQL);
        em.setJpaPropertyMap(jpaProperties);

        return em;
    }

    @Primary
    @Bean(name = "authWriterTransactionManager")
    public PlatformTransactionManager authWriterTransactionManager(
            @Qualifier("authWriterEntityManagerFactory") EntityManagerFactory writerEntityManagerFactory) {
        return new JpaTransactionManager(writerEntityManagerFactory);
    }
}
