package de.beispiel.vhv.modell;

import de.beispiel.vhv.modell.alt.VertragAlt;
import de.beispiel.vhv.modell.alt.VorgangAlt;
import de.beispiel.vhv.modell.alt.RisikoAlt;
import de.beispiel.vhv.modell.alt.NachlassZuschlagRisikobezogenAlt;
import de.beispiel.vhv.modell.alt.NachlassZuschlagVertragsbezogenAlt;
import de.beispiel.vhv.modell.alt.BeitragAlt;
import de.beispiel.vhv.modell.alt.ObjektdatenAlt;
import de.beispiel.vhv.modell.alt.WertermittlungAlt;
import de.beispiel.vhv.modell.alt.VorversichererAlt;
import de.beispiel.vhv.modell.alt.NebenversichererAlt;
import de.beispiel.vhv.modell.alt.StandortAlt;
import de.beispiel.vhv.modell.alt.WertsachenAlt;
import de.beispiel.vhv.modell.alt.DeckungenAlt;

/** Entitaeten eines Vertrags und seines Datenstandes; bewusst explizit typisiert. */
public final class QuellDatensatz {
    private final long vertragId;
    private final Datenstand datenstand;
    private VertragAlt vertrag;
    private VorgangAlt vorgang;
    private RisikoAlt risiko;
    private NachlassZuschlagRisikobezogenAlt nachlassZuschlagRisikobezogen;
    private NachlassZuschlagVertragsbezogenAlt nachlassZuschlagVertragsbezogen;
    private BeitragAlt beitrag;
    private ObjektdatenAlt objektdaten;
    private WertermittlungAlt wertermittlung;
    private VorversichererAlt vorversicherer;
    private NebenversichererAlt nebenversicherer;
    private StandortAlt standort;
    private WertsachenAlt wertsachen;
    private DeckungenAlt deckungen;
    public QuellDatensatz(long vertragId, Datenstand datenstand) {
        this.vertragId = vertragId;
        this.datenstand = java.util.Objects.requireNonNull(datenstand);
    }
    public long vertragId() { return vertragId; }
    public Datenstand datenstand() { return datenstand; }
    public VertragAlt getVertrag() { return vertrag; }
    public void setVertrag(VertragAlt value) { this.vertrag = value; }
    public VorgangAlt getVorgang() { return vorgang; }
    public void setVorgang(VorgangAlt value) { this.vorgang = value; }
    public RisikoAlt getRisiko() { return risiko; }
    public void setRisiko(RisikoAlt value) { this.risiko = value; }
    public NachlassZuschlagRisikobezogenAlt getNachlassZuschlagRisikobezogen() { return nachlassZuschlagRisikobezogen; }
    public void setNachlassZuschlagRisikobezogen(NachlassZuschlagRisikobezogenAlt value) { this.nachlassZuschlagRisikobezogen = value; }
    public NachlassZuschlagVertragsbezogenAlt getNachlassZuschlagVertragsbezogen() { return nachlassZuschlagVertragsbezogen; }
    public void setNachlassZuschlagVertragsbezogen(NachlassZuschlagVertragsbezogenAlt value) { this.nachlassZuschlagVertragsbezogen = value; }
    public BeitragAlt getBeitrag() { return beitrag; }
    public void setBeitrag(BeitragAlt value) { this.beitrag = value; }
    public ObjektdatenAlt getObjektdaten() { return objektdaten; }
    public void setObjektdaten(ObjektdatenAlt value) { this.objektdaten = value; }
    public WertermittlungAlt getWertermittlung() { return wertermittlung; }
    public void setWertermittlung(WertermittlungAlt value) { this.wertermittlung = value; }
    public VorversichererAlt getVorversicherer() { return vorversicherer; }
    public void setVorversicherer(VorversichererAlt value) { this.vorversicherer = value; }
    public NebenversichererAlt getNebenversicherer() { return nebenversicherer; }
    public void setNebenversicherer(NebenversichererAlt value) { this.nebenversicherer = value; }
    public StandortAlt getStandort() { return standort; }
    public void setStandort(StandortAlt value) { this.standort = value; }
    public WertsachenAlt getWertsachen() { return wertsachen; }
    public void setWertsachen(WertsachenAlt value) { this.wertsachen = value; }
    public DeckungenAlt getDeckungen() { return deckungen; }
    public void setDeckungen(DeckungenAlt value) { this.deckungen = value; }
}
