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
        entityManagerFactoryRef = "authReaderEntityManagerFactory",
        transactionManagerRef = "authReaderTransactionManager",
        basePackages = {"com.openbanking.authentication.repository.reader"})
@EntityScan(basePackages = "com.openbanking.authentication.entities")
public class AuthReaderDatasourceConfig {

    @Value(value = "${spring.jpa.hibernate.show-sql}")
    private boolean showSQL;
    @Value(value = "${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    // Auth Reader Database
    @Bean(name = "authReaderDbDataSourceProperties")
    @ConfigurationProperties("spring.datasource.auth-reader-db")
    public DataSourceProperties authReaderDbDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "authReaderDbHikariConfig")
    @ConfigurationProperties("spring.datasource.auth-reader-db.hikari")
    public HikariConfig authReaderDbHikariConfig() {
        return new HikariConfig();
    }

    @Bean(name = "authReaderDb")
    public DataSource authReaderDbDataSource(
            @Qualifier("authReaderDbDataSourceProperties") DataSourceProperties authReaderDbDataSourceProperties,
            @Qualifier("authReaderDbHikariConfig") HikariConfig authReaderDbHikariConfig) {
        authReaderDbHikariConfig.setJdbcUrl(authReaderDbDataSourceProperties.getUrl());
        authReaderDbHikariConfig.setUsername(authReaderDbDataSourceProperties.getUsername());
        authReaderDbHikariConfig.setPassword(authReaderDbDataSourceProperties.getPassword());

        return new HikariDataSource(authReaderDbHikariConfig);
    }

    @Bean(name = "authReaderDbJdbcTemplate")
    public JdbcTemplate authReaderDbJdbcTemplate(@Qualifier("authReaderDb") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "authReaderEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean authReaderyEntityManagerFactory(
            @Qualifier("authReaderDb") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPersistenceUnitName("authReader");
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

    @Bean(name = "authReaderTransactionManager")
    public PlatformTransactionManager authReaderTransactionManager(
            @Qualifier("authReaderEntityManagerFactory") EntityManagerFactory readerEntityManagerFactory) {
        return new JpaTransactionManager(readerEntityManagerFactory);
    }
}
