package v1.utils.generalfuncs;

import v1.utils.config.ConfigProperties;
import rdf.RDF;
import exceptions.ConfigException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class GeneralFunctions {
	
	public static String getAllElementsForItemID(String item, String itemid) throws ConfigException, IOException {
        RDF rdf = new RDF();
        String prefixes = rdf.getPREFIXSPARQL();
        String query = prefixes + "SELECT * WHERE { ";
        query += item + ":" + itemid + " ?p ?o. } ";
        query += "ORDER BY ASC(?p)";
        return query;
    }

    public static String getAllLabelsForVocabulary(String itemid) throws ConfigException, IOException {
        RDF rdf = new RDF();
        String prefixes = rdf.getPREFIXSPARQL();
        String query = prefixes + "SELECT * WHERE { ";
        query += "?s skos:inScheme ?voc . ";
        query += "?s dc:identifier ?id . ";
        query += "FILTER (?voc=ls_voc:" + itemid + ") }";
        return query;
    }

    public static String getSPARQLqueryElementsForRetcatsItem(String itemid) throws ConfigException, IOException {
        RDF rdf = new RDF();
        String prefixes = rdf.getPREFIXSPARQL();
        String query = prefixes + "SELECT ?var ?query ?url WHERE { ";
        query += "?retcat dc:identifier ?identifier. ";
        query += "?retcat ls:retcatsquery ?query. ";
        query += "?retcat ls:retcatsvar ?var. ";
        query += "?retcat dcat:accessURL ?url. ";
        query += "FILTER (?identifier=\"$identifier\") }";
        query = query.replace("$identifier", itemid);
        return query;
    }

    public static String getHierarchyForLabelsOneStep(String itemid) throws ConfigException, IOException {
        RDF rdf = new RDF();
        String prefixes = rdf.getPREFIXSPARQL();
        String query = prefixes + "SELECT ?narrowerTerm ?broaderTerm ?relatedTerm WHERE { ";
        query += "?s a ls:Label. ";
        query += "?s dc:identifier ?identifier. ";
        query += "OPTIONAL { ?s skos:narrower ?narrower. ?narrower ls:thumbnail ?narrowerTerm. }";
        query += "OPTIONAL { ?s skos:broader ?broader. ?broader ls:thumbnail ?broaderTerm. }";
        query += "OPTIONAL { ?s skos:related ?related. ?related ls:thumbnail ?relatedTerm. }";
        query += "FILTER (?identifier=\"$identifier\") }";
        query = query.replace("$identifier", itemid);
        return query;
    }

    public static String getRelationsForLabelsByCreator(String itemid) throws ConfigException, IOException {
        RDF rdf = new RDF();
        String prefixes = rdf.getPREFIXSPARQL();
        String query = prefixes + "SELECT ?nt ?bt ?rt ?nmt ?bmt ?rmt ?cmt ?emt WHERE { ";
        query += "?label a ls:Label. ";
        query += "?label dc:identifier ?identifier. ";
        query += "?label dc:creator ?creator. ";
        query += "OPTIONAL { ?narrower skos:narrower ?label. ?narrower dc:creator ?creator. ?narrower ls:thumbnail ?nt. }";
        query += "OPTIONAL { ?broader skos:broader ?label. ?broader dc:creator ?broader. ?narrower ls:thumbnail ?bt. }";
        query += "OPTIONAL { ?related skos:related ?label. ?related dc:creator ?creator. ?related ls:thumbnail ?rt. }";
        query += "OPTIONAL { ?narrowMatch skos:narrowMatch ?label. ?narrowMatch dc:creator ?creator. ?narrowMatch ls:thumbnail ?nmt. }";
        query += "OPTIONAL { ?broadMatch skos:broadMatch ?label. ?broadMatch dc:creator ?creator. ?broadMatch ls:thumbnail ?bmt. }";
        query += "OPTIONAL { ?relatedMatch skos:relatedMatch ?label. ?relatedMatch dc:creator ?creator. ?relatedMatch ls:thumbnail ?rmt. }";
        query += "OPTIONAL { ?closeMatch skos:closeMatch ?label. ?closeMatch dc:creator ?creator. ?closeMatch ls:thumbnail ?cmt. }";
        query += "OPTIONAL { ?exactMatch skos:exactMatch ?label. ?exactMatch dc:creator ?creator. ?exactMatch ls:thumbnail ?emt. }";
        query += "FILTER (?identifier=\"$identifier\") }";
        query = query.replace("$identifier", itemid);
        return query;
    }

    public static String encodeURIComponent(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }
        return result;
    }
	
	public static String encodeURIUmlaut(String s) {
        s = s.replaceAll("Ä", "%C3%84").replaceAll("ä", "%C3%A4");
		return s;
    }

}
