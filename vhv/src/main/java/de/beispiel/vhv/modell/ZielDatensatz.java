package de.beispiel.vhv.modell;

import de.beispiel.vhv.modell.ziel.VertragZiel;
import de.beispiel.vhv.modell.ziel.VorgangZiel;
import de.beispiel.vhv.modell.ziel.RisikoZiel;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagRisikobezogenZiel;
import de.beispiel.vhv.modell.ziel.NachlassZuschlagVertragsbezogenZiel;
import de.beispiel.vhv.modell.ziel.BeitragZiel;
import de.beispiel.vhv.modell.ziel.ObjektdatenZiel;
import de.beispiel.vhv.modell.ziel.WertermittlungZiel;
import de.beispiel.vhv.modell.ziel.VorversichererZiel;
import de.beispiel.vhv.modell.ziel.NebenversichererZiel;
import de.beispiel.vhv.modell.ziel.StandortZiel;
import de.beispiel.vhv.modell.ziel.WertsachenZiel;
import de.beispiel.vhv.modell.ziel.DeckungenZiel;

/** Entitaeten eines Vertrags und seines Datenstandes; bewusst explizit typisiert. */
public final class ZielDatensatz {
    private final long vertragId;
    private final Datenstand datenstand;
    private VertragZiel vertrag;
    private VorgangZiel vorgang;
    private RisikoZiel risiko;
    private NachlassZuschlagRisikobezogenZiel nachlassZuschlagRisikobezogen;
    private NachlassZuschlagVertragsbezogenZiel nachlassZuschlagVertragsbezogen;
    private BeitragZiel beitrag;
    private ObjektdatenZiel objektdaten;
    private WertermittlungZiel wertermittlung;
    private VorversichererZiel vorversicherer;
    private NebenversichererZiel nebenversicherer;
    private StandortZiel standort;
    private WertsachenZiel wertsachen;
    private DeckungenZiel deckungen;
    public ZielDatensatz(long vertragId, Datenstand datenstand) {
        this.vertragId = vertragId;
        this.datenstand = java.util.Objects.requireNonNull(datenstand);
    }
    public long vertragId() { return vertragId; }
    public Datenstand datenstand() { return datenstand; }
    public VertragZiel getVertrag() { return vertrag; }
    public void setVertrag(VertragZiel value) { this.vertrag = value; }
    public VorgangZiel getVorgang() { return vorgang; }
    public void setVorgang(VorgangZiel value) { this.vorgang = value; }
    public RisikoZiel getRisiko() { return risiko; }
    public void setRisiko(RisikoZiel value) { this.risiko = value; }
    public NachlassZuschlagRisikobezogenZiel getNachlassZuschlagRisikobezogen() { return nachlassZuschlagRisikobezogen; }
    public void setNachlassZuschlagRisikobezogen(NachlassZuschlagRisikobezogenZiel value) { this.nachlassZuschlagRisikobezogen = value; }
    public NachlassZuschlagVertragsbezogenZiel getNachlassZuschlagVertragsbezogen() { return nachlassZuschlagVertragsbezogen; }
    public void setNachlassZuschlagVertragsbezogen(NachlassZuschlagVertragsbezogenZiel value) { this.nachlassZuschlagVertragsbezogen = value; }
    public BeitragZiel getBeitrag() { return beitrag; }
    public void setBeitrag(BeitragZiel value) { this.beitrag = value; }
    public ObjektdatenZiel getObjektdaten() { return objektdaten; }
    public void setObjektdaten(ObjektdatenZiel value) { this.objektdaten = value; }
    public WertermittlungZiel getWertermittlung() { return wertermittlung; }
    public void setWertermittlung(WertermittlungZiel value) { this.wertermittlung = value; }
    public VorversichererZiel getVorversicherer() { return vorversicherer; }
    public void setVorversicherer(VorversichererZiel value) { this.vorversicherer = value; }
    public NebenversichererZiel getNebenversicherer() { return nebenversicherer; }
    public void setNebenversicherer(NebenversichererZiel value) { this.nebenversicherer = value; }
    public StandortZiel getStandort() { return standort; }
    public void setStandort(StandortZiel value) { this.standort = value; }
    public WertsachenZiel getWertsachen() { return wertsachen; }
    public void setWertsachen(WertsachenZiel value) { this.wertsachen = value; }
    public DeckungenZiel getDeckungen() { return deckungen; }
    public void setDeckungen(DeckungenZiel value) { this.deckungen = value; }
}
