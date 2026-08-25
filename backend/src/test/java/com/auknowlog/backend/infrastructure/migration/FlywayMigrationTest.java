package com.auknowlog.backend.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationTest {

    @Test
    void createsQuestionHistorySchemaAndEnforcesExactDuplicateProtection() throws Exception {
        String url = "jdbc:h2:mem:auknowlog_migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                // V3는 PostgreSQL pgvector 확장을 사용한다. Docker 기반 통합 테스트에서 검증한다.
                .target("2")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO question_history (topic, question_text, question_hash, options, correct_answer, explanation)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, "Java");
                statement.setString(2, "JVM의 역할은 무엇인가요?");
                statement.setString(3, "a".repeat(64));
                statement.setString(4, "[\"A\",\"B\"]");
                statement.setString(5, "A");
                statement.setString(6, "JVM은 바이트코드를 실행합니다.");
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            try (ResultSet resultSet = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM question_history WHERE topic = 'Java'")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (PreparedStatement duplicate = connection.prepareStatement("""
                    INSERT INTO question_history (topic, question_text, question_hash)
                    VALUES (?, ?, ?)
                    """)) {
                duplicate.setString(1, "Java");
                duplicate.setString(2, "표현만 바꾼 동일 문제");
                duplicate.setString(3, "a".repeat(64));

                assertThatThrownBy(duplicate::executeUpdate)
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
