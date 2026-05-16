package com.lifetool.infra;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FlywayMigrationRunner implements ApplicationRunner {

    private final boolean migrationEnabled;
    private final String datasourceUrl;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public FlywayMigrationRunner(
            @Value("${lifetool.database.migration-enabled:false}") boolean migrationEnabled,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            ObjectProvider<DataSource> dataSourceProvider) {
        this.migrationEnabled = migrationEnabled;
        this.datasourceUrl = datasourceUrl;
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled || datasourceUrl == null || datasourceUrl.isBlank()) {
            return;
        }
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return;
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
