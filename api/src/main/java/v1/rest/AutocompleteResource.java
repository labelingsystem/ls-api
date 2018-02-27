package v1.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rdf.RDF;
import org.linkedgeodesy.rdf4jext.rdf4j.RDF4J_20;
import exceptions.AutocompleteLengthException;
import exceptions.Logging;
import v1.utils.config.ConfigProperties;
import java.net.URLDecoder;
import java.util.List;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.eclipse.rdf4j.query.BindingSet;

@Path("/autocomplete")
public class AutocompleteResource {

	@GET
	@Path("/label")
	@Produces("application/json;charset=UTF-8")
	public Response getSuggestionsForLabels(@QueryParam("query") String requestquery) {
		try {
			String substing = requestquery.toLowerCase();
			substing = URLDecoder.decode(substing, "UTF-8");
			int suggestions = 20;
			int minLength = 1;
			if (substing.length() <= minLength) {
				throw new AutocompleteLengthException();
			} else {
				RDF rdf = new RDF();
				String query = rdf.getPREFIXSPARQL();
				query += "SELECT * WHERE { "
						+ "?s a ls:Label . "
						+ "?s skos:prefLabel ?acquery . "
						+ "FILTER(regex(?acquery, '" + substing + "', 'i'))"
						+ "} "
						+ "ORDER BY ASC(?acquery)"
						+ "LIMIT " + suggestions;
				List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
				List<String> suggestion_uri = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "s");
				List<String> suggestion_string = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "acquery");
				JSONObject jsonobj_query = new JSONObject();
				JSONArray jsonarray_suggestions = new JSONArray();
				for (int i = 0; i < suggestion_uri.size(); i++) {
					JSONObject jsonobj_suggestion = new JSONObject();
					String[] sugstr = suggestion_string.get(i).split("@");
					if (sugstr.length > 1) {
						jsonobj_suggestion.put("value", sugstr[0].replace("\"", ""));
						jsonobj_suggestion.put("lang", sugstr[1].replace("\"", ""));
					} else {
						jsonobj_suggestion.put("value", suggestion_string.get(i));
					}
					jsonobj_suggestion.put("data", suggestion_uri.get(i));
					jsonarray_suggestions.add(jsonobj_suggestion);
				}
				jsonobj_query.put("suggestions", jsonarray_suggestions);
				jsonobj_query.put("query", substing);
				return Response.ok(jsonobj_query).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AutocompleteResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	@GET
	@Path("/label/{filter}/{value}")
	@Produces("application/json;charset=UTF-8")
	public Response getSuggestionsForLabelsFilter(@QueryParam("query") String requestquery, @PathParam("filter") String filter, @PathParam("value") String value) {
		try {
			String substing = requestquery.toLowerCase();
			substing = URLDecoder.decode(substing, "UTF-8");
			int suggestions = 20;
			int minLength = 1;
			if (substing.length() <= minLength) {
				throw new AutocompleteLengthException();
			} else {
				RDF rdf = new RDF();
				String query = rdf.getPREFIXSPARQL();
				query += "SELECT * WHERE { "
						+ "?s a ls:Label . "
						+ "?s skos:prefLabel ?acquery . "
						+ "?s dc:creator ?creator . "
						+ "?s skos:inScheme ?vocabulary . "
						+ "FILTER(regex(?acquery, '" + substing + "', 'i'))";
				if (filter.equals("creator")) {
					query += "FILTER(?creator=\"" + value + "\")";
				} else if (filter.equals("vocabulary")) {
					query += "FILTER(?vocabulary=<" + rdf.getPrefixItem("ls_voc:" + value) + ">)";
				}
				query += "} "
						+ "ORDER BY ASC(?acquery)"
						+ "LIMIT " + suggestions;

				List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
				List<String> suggestion_uri = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "s");
				List<String> suggestion_string = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "acquery");
				JSONObject jsonobj_query = new JSONObject();
				JSONArray jsonarray_suggestions = new JSONArray();
				for (int i = 0; i < suggestion_uri.size(); i++) {
					JSONObject jsonobj_suggestion = new JSONObject();
					String[] sugstr = suggestion_string.get(i).split("@");
					if (sugstr.length > 1) {
						jsonobj_suggestion.put("value", sugstr[0].replace("\"", ""));
						jsonobj_suggestion.put("lang", sugstr[1].replace("\"", ""));
					} else {
						jsonobj_suggestion.put("value", suggestion_string.get(i));
					}
					jsonobj_suggestion.put("data", suggestion_uri.get(i));
					jsonarray_suggestions.add(jsonobj_suggestion);
				}
				jsonobj_query.put("suggestions", jsonarray_suggestions);
				jsonobj_query.put("query", substing);
				return Response.ok(jsonobj_query).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AutocompleteResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	@GET
	@Path("/agent")
	@Produces("application/json;charset=UTF-8")
	public Response getSuggestionsForAgents(@QueryParam("query") String requestquery) {
		try {
			String substing = requestquery.toLowerCase();
			substing = URLDecoder.decode(substing, "UTF-8");
			int suggestions = 10;
			int minLength = 1;
			if (substing.length() <= minLength) {
				throw new AutocompleteLengthException();
			} else {
				RDF rdf = new RDF();
				String query = rdf.getPREFIXSPARQL();
				query += "SELECT * WHERE { "
						+ "?s a ls:Agent . "
						+ "?s dc:identifier ?acquery . "
						+ "FILTER(regex(?acquery, '" + substing + "', 'i'))";
				query += "} "
						+ "ORDER BY ASC(?acquery) "
						+ "LIMIT " + suggestions;
				List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
				List<String> suggestion_uri = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "s");
				List<String> suggestion_string = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "acquery");
				JSONObject jsonobj_query = new JSONObject();
				JSONArray jsonarray_suggestions = new JSONArray();
				for (int i = 0; i < suggestion_uri.size(); i++) {
					JSONObject jsonobj_suggestion = new JSONObject();
					String[] sugstr = suggestion_string.get(i).split("@");
					if (sugstr.length > 1) {
						jsonobj_suggestion.put("value", sugstr[0].replace("\"", ""));
						jsonobj_suggestion.put("lang", sugstr[1].replace("\"", ""));
					} else {
						jsonobj_suggestion.put("value", suggestion_string.get(i));
					}
					jsonobj_suggestion.put("data", suggestion_uri.get(i));
					jsonarray_suggestions.add(jsonobj_suggestion);
				}
				jsonobj_query.put("suggestions", jsonarray_suggestions);
				jsonobj_query.put("query", substing);
				return Response.ok(jsonobj_query).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AutocompleteResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	@GET
	@Path("/vocabulary")
	@Produces("application/json;charset=UTF-8")
	public Response getSuggestionsForVocabs(@QueryParam("query") String requestquery) {
		try {
			String substing = requestquery.toLowerCase();
			substing = URLDecoder.decode(substing, "UTF-8");
			int suggestions = 10;
			int minLength = 1;
			if (substing.length() <= minLength) {
				throw new AutocompleteLengthException();
			} else {
				RDF rdf = new RDF();
				String query = rdf.getPREFIXSPARQL();
				query += "SELECT * WHERE { "
						+ "?s a ls:Vocabulary . "
						+ "?s dc:title ?acquery . "
						+ "FILTER(regex(?acquery, '" + substing + "', 'i'))";
				query += "} "
						+ "ORDER BY ASC(?acquery) "
						+ "LIMIT " + suggestions;
				List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
				List<String> suggestion_uri = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "s");
				List<String> suggestion_string = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "acquery");
				JSONObject jsonobj_query = new JSONObject();
				JSONArray jsonarray_suggestions = new JSONArray();
				for (int i = 0; i < suggestion_uri.size(); i++) {
					JSONObject jsonobj_suggestion = new JSONObject();
					String[] sugstr = suggestion_string.get(i).split("@");
					if (sugstr.length > 1) {
						jsonobj_suggestion.put("value", sugstr[0].replace("\"", ""));
						jsonobj_suggestion.put("lang", sugstr[1].replace("\"", ""));
					} else {
						jsonobj_suggestion.put("value", suggestion_string.get(i));
					}
					jsonobj_suggestion.put("data", suggestion_uri.get(i));
					jsonarray_suggestions.add(jsonobj_suggestion);
				}
				jsonobj_query.put("suggestions", jsonarray_suggestions);
				jsonobj_query.put("query", substing);
				return Response.ok(jsonobj_query).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AutocompleteResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

}
