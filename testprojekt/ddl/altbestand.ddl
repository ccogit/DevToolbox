-- DDL des Altbestands (DB2 for z/OS), Testdaten für die DevToolbox
-- Enthält absichtlich: eine nie gelesene Spalte (ALT_RISIKO.SB_BETRAG), eine fremde Tabelle (ALT_KUNDE),
-- LABEL ON / COMMENT ON in beiden Schreibweisen und einen Fremdschlüssel per ALTER TABLE.

CREATE TABLE ALTB.ALT_VERTRAG
     (VNR             CHAR(12)       NOT NULL,
      PRODUKT         CHAR(6)        NOT NULL,
      TARIF           CHAR(6)        NOT NULL WITH DEFAULT,
      ZW              CHAR(1)        NOT NULL WITH DEFAULT 'J',
      BEGINN          DATE           NOT NULL,
      STATUS          CHAR(1)        NOT NULL,
      PRIMARY KEY (VNR))
   IN DBALT01.TSVERT
   CCSID EBCDIC;

CREATE TABLE ALTB.ALT_RISIKO
     (VNR             CHAR(12)       NOT NULL,
      RISART          CHAR(2)        NOT NULL,
      OBERRIS         CHAR(1),
      BAK             CHAR(4),
      VS              DECIMAL(15, 2),
      JAHRESBEITRAG   DECIMAL(11, 2) NOT NULL WITH DEFAULT 0,
      SB_BETRAG       DECIMAL(9, 2),
      GUELTIG_BIS     DATE,
      CONSTRAINT PK_ALT_RISIKO PRIMARY KEY (VNR, RISART))
   IN DBALT01.TSRIS;

CREATE TABLE ALTB.ALT_OBJEKT
     (VNR             CHAR(12)       NOT NULL,
      STR             VARCHAR(60),
      PLZ             CHAR(5),
      ORT             VARCHAR(60),
      WOHNFLAECHE     DECIMAL(7, 2),
      PRIMARY KEY (VNR))
   IN DBALT01.TSOBJ;

CREATE TABLE ALTB.FAKTOREN
     (NAME            VARCHAR(30)    NOT NULL PRIMARY KEY,
      WERT            DECIMAL(9, 4)  NOT NULL)
   IN DBALT01.TSPARM;

-- fremdes Thema: darf im Attributverzeichnis nicht erscheinen
CREATE TABLE ALTB.ALT_KUNDE
     (KNR             CHAR(10)       NOT NULL PRIMARY KEY,
      NAME            VARCHAR(80))
   IN DBALT01.TSKUN;

ALTER TABLE ALTB.ALT_RISIKO
  ADD CONSTRAINT FK_RIS_VERT FOREIGN KEY (VNR) REFERENCES ALTB.ALT_VERTRAG (VNR) ON DELETE CASCADE;

LABEL ON TABLE ALTB.ALT_RISIKO IS 'Risiken je Vertrag';
COMMENT ON TABLE ALTB.ALT_RISIKO IS 'Ein Satz je versicherter Gefahr; historisiert über GUELTIG_BIS';

LABEL ON ALTB.ALT_RISIKO
     (RISART        IS 'Risikoart',
      OBERRIS       IS 'Oberrisiko',
      BAK           IS 'Bauartklasse',
      VS            IS 'Versicherungssumme',
      SB_BETRAG     IS 'Selbstbeteiligung');

COMMENT ON COLUMN ALTB.ALT_RISIKO.RISART IS 'FE=Feuer, LW=Leitungswasser, ST=Sturm/Hagel, EL=Elementar, GL=Glas';
COMMENT ON COLUMN ALTB.ALT_RISIKO.OBERRIS IS 'G=Gebäude, H=Hausrat; vor 2005 nicht gepflegt';
COMMENT ON COLUMN ALTB.ALT_RISIKO.SB_BETRAG IS 'Wird in der Altmigration nicht übernommen (?)';
COMMENT ON COLUMN ALTB.ALT_OBJEKT.WOHNFLAECHE IS 'in m²; Basis der Summenermittlung bei fehlender VS';
