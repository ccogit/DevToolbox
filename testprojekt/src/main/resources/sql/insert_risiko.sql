-- Datei mit BOM und Windows-Zeilenenden: das BOM darf nicht in der Ausgabe landen
INSERT INTO RISIKO (RISIKO_ID, VERTRAG_NR, RISIKO_TYP, OBERRISIKO_TYP, VS, RATENBEITRAG, ZAHLWEISE)
VALUES (:risikoId, :vertragsnummer, :risikoTyp, :oberrisikoTyp, :vs, :ratenbeitrag, :zahlweise);
