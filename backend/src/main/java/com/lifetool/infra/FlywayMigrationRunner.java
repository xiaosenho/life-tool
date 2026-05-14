package com.lifetool.infra;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FlywayMigrationRunner implements ApplicationRunner {

    private final boolean migrationEnabled;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;

    public FlywayMigrationRunner(
            @Value("${lifetool.database.migration-enabled:false}") boolean migrationEnabled,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword) {
        this.migrationEnabled = migrationEnabled;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled || datasourceUrl == null || datasourceUrl.isBlank()) {
            return;
        }

        Flyway.configure()
                .dataSource(datasourceUrl, datasourceUsername, datasourcePassword)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
