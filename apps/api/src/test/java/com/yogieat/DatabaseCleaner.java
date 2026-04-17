package com.yogieat;

import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class DatabaseCleaner {

    private DatabaseCleaner() {
        throw new IllegalStateException("Utility class");
    }

    public static void clear(ApplicationContext applicationContext) {
        var jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
        var transactionTemplate = applicationContext.getBean(TransactionTemplate.class);

        transactionTemplate.execute(
                status -> {
                    deleteAll(jdbcTemplate);
                    return null;
                });
    }

    private static void deleteAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String tableName : findDatabaseTableNames(jdbcTemplate)) {
            deleteDataFromTable(jdbcTemplate, tableName);
            resetAutoIncrementColumn(jdbcTemplate, tableName);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private static void deleteDataFromTable(JdbcTemplate jdbcTemplate, String tableName) {
        String deleteQuery = "DELETE FROM %s".formatted(tableName);
        jdbcTemplate.execute(deleteQuery);
    }

    private static void resetAutoIncrementColumn(JdbcTemplate jdbcTemplate, String tableName) {
        String autoIncrementColumn = findAutoIncrementColumn(jdbcTemplate, tableName);
        String resetQuery =
                "ALTER TABLE %s ALTER COLUMN %s RESTART WITH 1"
                        .formatted(tableName, autoIncrementColumn);
        jdbcTemplate.execute(resetQuery);
    }

    private static String findAutoIncrementColumn(JdbcTemplate jdbcTemplate, String tableName) {
        String query =
                """
			SELECT column_name FROM information_schema.columns
			WHERE table_schema = ? AND table_name = ? AND is_identity = 'YES'
			""";

        List<String> columns =
                jdbcTemplate.query(
                        query, (rs, rowNum) -> rs.getString("column_name"), "PUBLIC", tableName);

        return columns.getFirst();
    }

    private static List<String> findDatabaseTableNames(JdbcTemplate jdbcTemplate) {
        String query =
                """
			SELECT table_name FROM information_schema.tables
			WHERE table_schema = ? AND table_type = 'BASE TABLE'
			ORDER BY table_name
			""";
        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getString("table_name"), "PUBLIC");
    }
}
