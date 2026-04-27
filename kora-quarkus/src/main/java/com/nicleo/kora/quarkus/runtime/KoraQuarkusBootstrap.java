package com.nicleo.kora.quarkus.runtime;

import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.quarkus.Sql;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

@Startup
@ApplicationScoped
public class KoraQuarkusBootstrap {
    private final KoraSupportInstaller supportInstaller;
    private final Instance<SqlExecutor> sqlExecutor;

    public KoraQuarkusBootstrap(KoraSupportInstaller supportInstaller, Instance<SqlExecutor> sqlExecutor) {
        this.supportInstaller = supportInstaller;
        this.sqlExecutor = sqlExecutor;
    }

    @PostConstruct
    void init() {
        supportInstaller.installGeneratedSupport();
        if (sqlExecutor.isResolvable()) {
            Sql.bind(sqlExecutor.get());
        }
    }

    @PreDestroy
    void destroy() {
        Sql.clear();
    }
}
