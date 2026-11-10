package de.vers.neu.objekt.po;

import de.vers.neu.common.AbstractPO;

public class ObjektPO extends AbstractPO {

    private String plz;

    @Override
    public String tabelle() { return "ALT_OBJEKT"; }

    public String getPlz() { return plz; }
    public void setPlz(String plz) { this.plz = plz; }
}
