package com.example.labpay.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

@Slf4j
@Configuration
public class DataSourceDebugConfig {

    @Bean
    public ApplicationRunner dataSourceDebug(DataSource dataSource) {
        return args -> {
            log.info("========== DATASOURCE DEBUG ==========");
            log.info("DataSource class = {}", dataSource.getClass().getName());

            AtomikosDataSourceBean atomikosDataSource = resolveAtomikosDataSource(dataSource);

            if (atomikosDataSource == null) {
                log.warn("DataSource is NOT AtomikosDataSourceBean");
                log.warn("This means XA/Atomikos datasource config may not be applied");
                log.info("======================================");
                return;
            }

            log.info("Atomikos uniqueResourceName = {}", atomikosDataSource.getUniqueResourceName());
            log.info("Atomikos minPoolSize = {}", atomikosDataSource.getMinPoolSize());
            log.info("Atomikos maxPoolSize = {}", atomikosDataSource.getMaxPoolSize());
            log.info("Atomikos borrowConnectionTimeout = {}", atomikosDataSource.getBorrowConnectionTimeout());
            log.info("Atomikos maxIdleTime = {}", atomikosDataSource.getMaxIdleTime());
            log.info("Atomikos maintenanceInterval = {}", atomikosDataSource.getMaintenanceInterval());
            log.info("======================================");
        };
    }

    private AtomikosDataSourceBean resolveAtomikosDataSource(DataSource dataSource) {
        if (dataSource instanceof AtomikosDataSourceBean atomikosDataSource) {
            return atomikosDataSource;
        }

        try {
            if (dataSource.isWrapperFor(AtomikosDataSourceBean.class)) {
                return dataSource.unwrap(AtomikosDataSourceBean.class);
            }
        } catch (SQLException ignored) {
            // DataSource wrapper cannot be unwrapped to AtomikosDataSourceBean
        }

        return null;
    }
}