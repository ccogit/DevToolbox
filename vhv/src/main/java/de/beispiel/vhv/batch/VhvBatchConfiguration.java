package de.beispiel.vhv.batch;

import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import de.beispiel.vhv.modell.*;
import de.beispiel.vhv.read.*;
import de.beispiel.vhv.process.MigrationsProcessor;
import de.beispiel.vhv.write.SchreibDelegator;

/** Start ueber Jobparameter datenstand=BESTAND|SCHWEBE|ALLE (Default ALLE). */
@Configuration
public class VhvBatchConfiguration {
    @Bean
    @StepScope
    public ItemStreamReader<QuellDatensatz> vhvReader(
            DataSource dataSource, AnreicherungsDelegator anreicherer,
            @Value("#{jobParameters['datenstand']}") String datenstand) {
        String stand = datenstand == null || datenstand.isBlank()
                ? "ALLE" : datenstand.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ALLE".equals(stand) && !"BESTAND".equals(stand) && !"SCHWEBE".equals(stand)) {
            throw new IllegalArgumentException("Ungueltiger datenstand: " + datenstand);
        }
        JdbcCursorItemReader<QuellDatensatz> cursor = new JdbcCursorItemReader<>();
        cursor.setName("vhvVertragsSchluesselReader");
        cursor.setDataSource(dataSource);
        cursor.setSql("SELECT VERTRAG_ID, DATENSTAND FROM ALT_VERTRAG "
                + "WHERE (? = 'ALLE' OR DATENSTAND = ?) ORDER BY VERTRAG_ID, DATENSTAND");
        cursor.setPreparedStatementSetter(ps -> {
            ps.setString(1, stand);
            ps.setString(2, stand);
        });
        cursor.setRowMapper((rs, nr) -> new QuellDatensatz(
                rs.getLong("VERTRAG_ID"), Datenstand.valueOf(rs.getString("DATENSTAND"))));
        return new AnreichernderReader(cursor, anreicherer);
    }
    @Bean
    public Step vhvMigrationsSchritt(
            JobRepository jobRepository, PlatformTransactionManager transactionManager,
            ItemStreamReader<QuellDatensatz> vhvReader,
            MigrationsProcessor processor, SchreibDelegator schreiber) {
        return new StepBuilder("vhvMigrationsSchritt", jobRepository)
                .<QuellDatensatz, ZielDatensatz>chunk(10, transactionManager)
                .reader(vhvReader).processor(processor).writer(schreiber).build();
    }
    @Bean
    public Job vhvMigrationJob(JobRepository jobRepository, Step vhvMigrationsSchritt) {
        return new JobBuilder("vhvMigrationJob", jobRepository).start(vhvMigrationsSchritt).build();
    }
}
