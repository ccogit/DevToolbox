package de.beispiel.vhv.read;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import de.beispiel.vhv.modell.QuellDatensatz;

/** Read-Anreicherer laufen explizit vor dem Process-Schritt. */
public final class AnreichernderReader implements ItemStreamReader<QuellDatensatz> {
    private final JdbcCursorItemReader<QuellDatensatz> delegate;
    private final AnreicherungsDelegator anreicherer;
    public AnreichernderReader(JdbcCursorItemReader<QuellDatensatz> delegate,
                               AnreicherungsDelegator anreicherer) {
        this.delegate = delegate;
        this.anreicherer = anreicherer;
    }
    @Override public QuellDatensatz read() throws Exception {
        QuellDatensatz zeile = delegate.read();
        if (zeile != null) anreicherer.anreichern(zeile);
        return zeile;
    }
    @Override public void open(ExecutionContext context) { delegate.open(context); }
    @Override public void update(ExecutionContext context) { delegate.update(context); }
    @Override public void close() { delegate.close(); }
}
