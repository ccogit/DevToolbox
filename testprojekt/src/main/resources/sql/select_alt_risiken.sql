-- Selektion der zu migrierenden Risiken (Produkt WG2010, aktive und ruhende Verträge)
SELECT v.VNR, v.TARIF, v.ZW, v.BEGINN, v.STATUS,
       r.RISART, r.OBERRIS, r.BAK, r.VS, r.JAHRESBEITRAG,
       o.STR, o.PLZ, o.ORT
FROM   ALT_VERTRAG v
JOIN   ALT_RISIKO r ON r.VNR = v.VNR
JOIN   ALT_OBJEKT o ON o.VNR = v.VNR
WHERE  v.PRODUKT = 'WG2010'
AND    v.STATUS IN ('A', 'R')
AND    r.GUELTIG_BIS IS NULL
ORDER  BY v.VNR, r.RISART
