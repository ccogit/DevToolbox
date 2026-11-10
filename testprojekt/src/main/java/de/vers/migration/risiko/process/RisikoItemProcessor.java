package de.vers.migration.risiko.process;

import de.vers.migration.common.Zahlweise;
import de.vers.migration.process.BeitragsConverter;
import de.vers.migration.risiko.model.AltRisikoDto;
import de.vers.migration.risiko.model.NeuRisikoEntity;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static de.vers.migration.common.Konstanten.TARIF_PREMIUM;

/**
 * Mappt ein Alt-Risiko auf das Neusystem.
 * <p>
 * Beispiel (dreifache Backticks im Code dürfen den Markdown-Block nicht schließen):
 * ```
 * FE + Oberrisiko G  ->  FEUER / GEBAEUDE
 * ```
 */
@Component
public class RisikoItemProcessor implements ItemProcessor<AltRisikoDto, NeuRisikoEntity> {

    private final RisikoartMapper risikoartMapper;
    private final OberrisikoZuordnung oberrisikoZuordnung;
    private final BeitragsConverter beitragsConverter;

    public RisikoItemProcessor(RisikoartMapper risikoartMapper, OberrisikoZuordnung oberrisikoZuordnung,
                               BeitragsConverter beitragsConverter) {
        this.risikoartMapper = risikoartMapper;
        this.oberrisikoZuordnung = oberrisikoZuordnung;
        this.beitragsConverter = beitragsConverter;
    }

    @Override
    public NeuRisikoEntity process(AltRisikoDto alt) {
        // Ruhende Verträge (Status R) werden gelesen, aber nicht migriert: null = Satz wird gefiltert
        if ("R".equals(alt.getVertrag().getStatus())) {
            return null;
        }
        if (alt.getVersicherungssumme() == null || alt.getVersicherungssumme().signum() <= 0) {
            throw new IllegalArgumentException("Risiko ohne Versicherungssumme: " + alt.getVertrag().getVertragsnummer());
        }

        NeuRisikoEntity neu = new NeuRisikoEntity();
        neu.setVertragsnummer(alt.getVertrag().getVertragsnummer());
        neu.setRisikoId(alt.getVertrag().getVertragsnummer() + "-" + alt.getRisikoart());
        neu.setRisikoTyp(risikoartMapper.mappe(alt.getRisikoart()));
        neu.setOberrisikoTyp(oberrisikoZuordnung.ermittle(alt));
        neu.setLage(alt.getLage());

        Zahlweise zahlweise = Zahlweise.ausAltcode(alt.getVertrag().getZahlweiseCode());
        neu.setZahlweise(zahlweise);
        neu.setRatenbeitrag(beitragsConverter.ratenbeitrag(alt.getBeitrag(), zahlweise));

        // Premium-Tarif: Versicherungssumme um 10 % Vorsorge erhöhen
        BigDecimal vs = alt.getVersicherungssumme();
        if (TARIF_PREMIUM.equals(alt.getVertrag().getTarif())) {
            vs = vs.multiply(new BigDecimal("1.10"));
        }
        neu.setVersicherungssumme(vs);
        return neu;
    }
}
