package v1.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rdf.RDF;
import rdf.RDF4J_20;
import exceptions.ConfigException;
import exceptions.Logging;
import exceptions.RdfException;
import exceptions.ResourceNotAvailableException;
import exceptions.UniqueIdentifierException;
import v1.utils.transformer.Transformer;
import v1.utils.config.ConfigProperties;
import v1.utils.generalfuncs.GeneralFunctions;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.jdom.JDOMException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.eclipse.rdf4j.query.BindingSet;
import org.json.simple.parser.JSONParser;
import v1.utils.db.SQlite;
import v1.utils.validatejson.ValidateJSONObject;

/**
 * REST API for Vocabularies
 *
 * @author Florian Thiery M.Sc.
 * @author i3mainz - Institute for Spatial Information and Surveying Technology
 * @version 27.06.2016
 */
@Path("/agents")
public class AgentsResource {

	@GET
	@Produces({"application/json;charset=UTF-8", "application/xml;charset=UTF-8", "application/rdf+xml;charset=UTF-8", "text/turtle;charset=UTF-8", "text/n3;charset=UTF-8", "application/ld+json;charset=UTF-8", "application/rdf+json;charset=UTF-8"})
	public Response getAgents(
			@HeaderParam("Accept") String acceptHeader,
			@HeaderParam("Accept-Encoding") String acceptEncoding,
			@QueryParam("pretty") boolean pretty,
			@QueryParam("sort") String sort)
			throws IOException, JDOMException, ConfigException, ParserConfigurationException, TransformerException {
		try {
			String OUTSTRING = "";
			// QUERY STRING
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String query = rdf.getPREFIXSPARQL();
			query += "SELECT * WHERE { "
					+ "?s ?p ?o . "
					+ "?s a ls:Agent . "
					+ "?s dc:identifier ?identifier . "
					+ "OPTIONAL { ?s foaf:accountName ?name . } " // because of sorting
					+ "OPTIONAL { ?s foaf:firstName ?firstName . } " // because of sorting
					+ "OPTIONAL { ?s foaf:lastName ?lastName . } " // because of sorting
					+ " } ";
			// SORTING
			List<String> sortList = new ArrayList<String>();
			if (sort != null) {
				String sortArray[] = sort.split(",");
				for (String element : sortArray) {
					if (sort != null) {
						String sortDirection = element.substring(0, 1);
						if (sortDirection.equals("+")) {
							sortDirection = "ASC";
						} else {
							sortDirection = "DESC";
						}
						element = element.substring(1);
						sortList.add(sortDirection + "(?" + element + ") ");
					}
				}
				query += "ORDER BY ";
				for (String element : sortList) {
					query += element;
				}
			}
			// QUERY TRIPLESTORE
			long ctm_start = System.currentTimeMillis();
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> s = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "s");
			List<String> p = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> o = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			System.out.println(System.currentTimeMillis() - ctm_start);
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < s.size(); i++) {
				rdf.setModelTriple(s.get(i), p.get(i), o.get(i));
			}
			JSONArray outArray = new JSONArray();
			if (acceptHeader.contains("application/json") || acceptHeader.contains("text/html")) {
				JSONObject jsonObject = (JSONObject) new JSONParser().parse(rdf.getModel("RDF/JSON"));
				Set keys = jsonObject.keySet();
				Iterator a = keys.iterator();
				while (a.hasNext()) {
					String key = (String) a.next();
					JSONObject tmpObject = (JSONObject) jsonObject.get(key);
					JSONArray idArray = (JSONArray) tmpObject.get(rdf.getPrefixItem("dc:identifier"));
					JSONObject idObject = (JSONObject) idArray.get(0);
					String h = (String) idObject.get("value");
					JSONObject tmpObject2 = new JSONObject();
					tmpObject2.put(key, tmpObject);
					String hh = tmpObject2.toString();
					JSONObject tmp = Transformer.agent_GET(hh, h);
					// get database properties and add to triplestore data
					JSONObject user = SQlite.getUserInfo(h);
					tmp.put("role", user.get("role"));
					tmp.put("status", user.get("status"));
					outArray.add(tmp);
				}
			}
			System.out.println(System.currentTimeMillis() - ctm_start);
			if (acceptHeader.contains("application/json")) {
				if (pretty) {
					JsonParser parser = new JsonParser();
					JsonObject json = parser.parse(outArray.toString()).getAsJsonObject();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					return Response.ok(gson.toJson(json)).build();
				} else {
					OUTSTRING = outArray.toString();
					if (acceptEncoding.contains("gzip")) {
						// set outputstream
						final String OUTSTRING_FINAL = OUTSTRING;
						StreamingOutput stream;
						stream = new StreamingOutput() {
							@Override
							public void write(OutputStream output) throws IOException, WebApplicationException {
								try {
									output = GZIP(OUTSTRING_FINAL, output);
								} catch (Exception e) {
									System.out.println(e.toString());
								}
							}
						};
						return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
					} else {
						return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
					}
				}
			} else if (acceptHeader.contains("application/rdf+json")) {
				return Response.ok(rdf.getModel("RDF/JSON")).header("Content-Type", "application/json;charset=UTF-8").build();
			} else if (acceptHeader.contains("text/html")) {
				if (pretty) {
					JsonParser parser = new JsonParser();
					JsonObject json = parser.parse(outArray.toString()).getAsJsonObject();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
				} else {
					OUTSTRING = outArray.toString();
					if (acceptEncoding.contains("gzip")) {
						// set outputstream
						final String OUTSTRING_FINAL = OUTSTRING;
						StreamingOutput stream;
						stream = new StreamingOutput() {
							@Override
							public void write(OutputStream output) throws IOException, WebApplicationException {
								try {
									output = GZIP(OUTSTRING_FINAL, output);
								} catch (Exception e) {
									System.out.println(e.toString());
								}
							}
						};
						return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
					} else {
						return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
					}
				}
			} else if (acceptHeader.contains("application/xml")) {
				return Response.ok(rdf.getModel("RDF/XML")).build();
			} else if (acceptHeader.contains("application/rdf+xml")) {
				return Response.ok(rdf.getModel("RDF/XML")).build();
			} else if (acceptHeader.contains("text/turtle")) {
				return Response.ok(rdf.getModel("Turtle")).build();
			} else if (acceptHeader.contains("text/n3")) {
				return Response.ok(rdf.getModel("N-Triples")).build();
			} else if (acceptHeader.contains("application/ld+json")) {
				return Response.ok(rdf.getModel("JSON-LD")).build();
			} else if (pretty) {
				JsonParser parser = new JsonParser();
				JsonObject json = parser.parse(outArray.toString()).getAsJsonObject();
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				OUTSTRING = outArray.toString();
				if (acceptEncoding.contains("gzip")) {
					// set outputstream
					final String OUTSTRING_FINAL = OUTSTRING;
					StreamingOutput stream;
					stream = new StreamingOutput() {
						@Override
						public void write(OutputStream output) throws IOException, WebApplicationException {
							try {
								output = GZIP(OUTSTRING_FINAL, output);
							} catch (Exception e) {
								System.out.println(e.toString());
							}
						}
					};
					return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
				} else {
					return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
				}
			}
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				JSONArray outArray = new JSONArray();
				return Response.ok(outArray).header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}")
	@Produces({"application/json;charset=UTF-8", "application/xml;charset=UTF-8", "application/rdf+xml;charset=UTF-8", "text/turtle;charset=UTF-8", "text/n3;charset=UTF-8", "application/ld+json;charset=UTF-8", "application/rdf+json;charset=UTF-8"})
	public Response getAgent(@PathParam("agent") String agent, @HeaderParam("Accept") String acceptHeader, @QueryParam("pretty") boolean pretty, @HeaderParam("Accept-Encoding") String acceptEncoding) throws IOException, JDOMException, RdfException, ParserConfigurationException, TransformerException {
		try {
			String OUTSTRING = "";
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			if (acceptHeader.contains("application/json")) {
				JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), agent);
				// get database properties and add to triplestore data
				JSONObject user = SQlite.getUserInfo(agent);
				agentObject.put("role", user.get("role"));
				agentObject.put("status", user.get("status"));
				String out = agentObject.toJSONString();
				if (pretty) {
					JsonParser parser = new JsonParser();
					JsonObject json = parser.parse(out).getAsJsonObject();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					return Response.ok(gson.toJson(json)).build();
				} else {
					OUTSTRING = out.toString();
					if (acceptEncoding.contains("gzip")) {
						// set outputstream
						final String OUTSTRING_FINAL = OUTSTRING;
						StreamingOutput stream;
						stream = new StreamingOutput() {
							@Override
							public void write(OutputStream output) throws IOException, WebApplicationException {
								try {
									output = GZIP(OUTSTRING_FINAL, output);
								} catch (Exception e) {
									System.out.println(e.toString());
								}
							}
						};
						return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
					} else {
						return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
					}
				}
			} else if (acceptHeader.contains("application/rdf+json")) {
				return Response.ok(rdf.getModel("RDF/JSON")).header("Content-Type", "application/json;charset=UTF-8").build();
			} else if (acceptHeader.contains("text/html")) {
				JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), agent);
				// get database properties and add to triplestore data
				JSONObject user = SQlite.getUserInfo(agent);
				agentObject.put("role", user.get("role"));
				agentObject.put("status", user.get("status"));
				String out = agentObject.toJSONString();
				if (pretty) {
					JsonParser parser = new JsonParser();
					JsonObject json = parser.parse(out).getAsJsonObject();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
				} else {
					OUTSTRING = out.toString();
					if (acceptEncoding.contains("gzip")) {
						// set outputstream
						final String OUTSTRING_FINAL = OUTSTRING;
						StreamingOutput stream;
						stream = new StreamingOutput() {
							@Override
							public void write(OutputStream output) throws IOException, WebApplicationException {
								try {
									output = GZIP(OUTSTRING_FINAL, output);
								} catch (Exception e) {
									System.out.println(e.toString());
								}
							}
						};
						return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
					} else {
						return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
					}
				}
			} else if (acceptHeader.contains("application/xml")) {
				return Response.ok(rdf.getModel("RDF/XML")).build();
			} else if (acceptHeader.contains("application/rdf+xml")) {
				return Response.ok(rdf.getModel("RDF/XML")).build();
			} else if (acceptHeader.contains("text/turtle")) {
				return Response.ok(rdf.getModel("Turtle")).build();
			} else if (acceptHeader.contains("text/n3")) {
				return Response.ok(rdf.getModel("N-Triples")).build();
			} else if (acceptHeader.contains("application/ld+json")) {
				return Response.ok(rdf.getModel("JSON-LD")).build();
			} else {
				JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), agent);
				// get database properties and add to triplestore data
				JSONObject user = SQlite.getUserInfo(agent);
				agentObject.put("role", user.get("role"));
				agentObject.put("status", user.get("status"));
				String out = agentObject.toJSONString();
				if (pretty) {
					JsonParser parser = new JsonParser();
					JsonObject json = parser.parse(out).getAsJsonObject();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
				} else {
					OUTSTRING = out.toString();
					if (acceptEncoding.contains("gzip")) {
						// set outputstream
						final String OUTSTRING_FINAL = OUTSTRING;
						StreamingOutput stream;
						stream = new StreamingOutput() {
							@Override
							public void write(OutputStream output) throws IOException, WebApplicationException {
								try {
									output = GZIP(OUTSTRING_FINAL, output);
								} catch (Exception e) {
									System.out.println(e.toString());
								}
							}
						};
						return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
					} else {
						return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
					}
				}
			}
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.json")
	@Produces("application/json;charset=UTF-8")
	public Response getAgent_JSON(@PathParam("agent") String agent, @QueryParam("pretty") boolean pretty, @HeaderParam("Accept-Encoding") String acceptEncoding) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			String OUTSTRING = "";
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), agent);
			// get database properties and add to triplestore data
			JSONObject user = SQlite.getUserInfo(agent);
			agentObject.put("role", user.get("role"));
			agentObject.put("status", user.get("status"));
			String out = agentObject.toJSONString();
			if (pretty) {
				JsonParser parser = new JsonParser();
				JsonObject json = parser.parse(out).getAsJsonObject();
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				OUTSTRING = out.toString();
				if (acceptEncoding.contains("gzip")) {
					// set outputstream
					final String OUTSTRING_FINAL = OUTSTRING;
					StreamingOutput stream;
					stream = new StreamingOutput() {
						@Override
						public void write(OutputStream output) throws IOException, WebApplicationException {
							try {
								output = GZIP(OUTSTRING_FINAL, output);
							} catch (Exception e) {
								System.out.println(e.toString());
							}
						}
					};
					return Response.ok(stream).header("Content-Type", "application/json;charset=UTF-8").header("Content-Encoding", "gzip").build();
				} else {
					return Response.ok(OUTSTRING).header("Content-Type", "application/json;charset=UTF-8").build();
				}
			}
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.xml")
	@Produces("application/xml;charset=UTF-8")
	public Response getAgent_XML(@PathParam("agent") String agent) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			String RDFoutput = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + rdf.getModel("RDF/XML");
			return Response.ok(RDFoutput).build();
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.rdf")
	@Produces("application/rdf+xml;charset=UTF-8")
	public Response getAgentRDF_XML(@PathParam("agent") String agent) throws IOException, JDOMException, RdfException, ParserConfigurationException, TransformerException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			String RDFoutput = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + rdf.getModel("RDF/XML");
			return Response.ok(RDFoutput).build();
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.ttl")
	@Produces("text/turtle;charset=UTF-8")
	public Response getAgentRDF_Turtle(@PathParam("agent") String agent) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			return Response.ok(rdf.getModel("Turtle")).build();
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.n3")
	@Produces("text/n3;charset=UTF-8")
	public Response getAgentRDF_N3(@PathParam("agent") String agent) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			return Response.ok(rdf.getModel("N-Triples")).build();
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.jsonrdf")
	@Produces("application/json;charset=UTF-8")
	public Response getAgentRDF_JSONRDF(@PathParam("agent") String agent, @QueryParam("pretty") boolean pretty) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			String out = rdf.getModel("RDF/JSON");
			if (pretty) {
				JsonParser parser = new JsonParser();
				JsonObject json = parser.parse(out).getAsJsonObject();
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.ok(out).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@GET
	@Path("/{agent}.jsonld")
	@Produces("application/ld+json;charset=UTF-8")
	public Response getAgentRDF_JSONLD(@PathParam("agent") String agent, @QueryParam("pretty") boolean pretty) throws IOException, JDOMException, TransformerException, ParserConfigurationException {
		try {
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String item = "ls_age";
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			String out = rdf.getModel("JSON-LD");
			if (pretty) {
				JsonParser parser = new JsonParser();
				JsonObject json = parser.parse(out).getAsJsonObject();
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				return Response.ok(gson.toJson(json)).header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.ok(out).header("Content-Type", "application/json;charset=UTF-8").build();
			}
		} catch (Exception e) {
			if (e.toString().contains("ResourceNotAvailableException")) {
				return Response.status(Response.Status.NOT_FOUND).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			} else {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
						.header("Content-Type", "application/json;charset=UTF-8").build();
			}
		}
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
	public Response postAgent(String json) throws IOException, JDOMException, ConfigException, ParserConfigurationException, TransformerException {
		try {
			// get variables
			String item = "ls_age";
			// validate
			ValidateJSONObject.validateAgent(json);
			// parse name
			JSONObject jsonObject = (JSONObject) new JSONParser().parse(json);
			String itemID = (String) jsonObject.get("id");
			String pwd = (String) jsonObject.get("pwd");
			// create triples
			json = Transformer.agent_POST(json, itemID);
			String triples = createAgentSPARQLUPDATE(item, itemID);
			// input triples
			RDF4J_20.inputRDFfromRDFJSONString(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), json);
			RDF4J_20.SPARQLupdate(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), triples);
			// get result als json
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String query = GeneralFunctions.getAllElementsForItemID(item, itemID);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + itemID, predicates.get(i), objects.get(i));
			}
			JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), itemID);
			// add entry in sqlite
			SQlite.insertUser(itemID, pwd);
			// get database properties and add to triplestore data
			JSONObject user = SQlite.getUserInfo(itemID);
			agentObject.put("role", user.get("role"));
			agentObject.put("status", user.get("status"));
			String out = agentObject.toJSONString();
			return Response.status(Response.Status.CREATED).entity(out).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	@PUT
	@Path("/{agent}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
	public Response updateAgent(@PathParam("agent") String agent, String json)
			throws IOException, JDOMException, RdfException, ParserConfigurationException, TransformerException {
		try {
			String item = "ls_age";
			// validate
			ValidateJSONObject.validateAgent(json);
			// check if resource exists
			String queryExist = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> resultExist = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), queryExist);
			if (resultExist.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			// insert data
			JSONObject agentObjectOriginal = (JSONObject) new JSONParser().parse(json);
			String status = (String) agentObjectOriginal.get("status");
			json = Transformer.agent_POST(json, agent);
			RDF4J_20.SPARQLupdate(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), putAgentSPARQLUPDATE(agent));
			RDF4J_20.inputRDFfromRDFJSONString(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), json);
			// get result als json
			RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
			String query = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> result = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), query);
			List<String> predicates = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "p");
			List<String> objects = RDF4J_20.getValuesFromBindingSet_ORDEREDLIST(result, "o");
			if (result.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			for (int i = 0; i < predicates.size(); i++) {
				rdf.setModelTriple(item + ":" + agent, predicates.get(i), objects.get(i));
			}
			JSONObject agentObject = Transformer.agent_GET(rdf.getModel("RDF/JSON"), agent);
			// change status in sqlite
			if (status.equals("active")) {
				SQlite.activateUser(agent);
			} else {
				SQlite.deactivateUser(agent);
			}
			// get database properties and add to triplestore data
			JSONObject user = SQlite.getUserInfo(agent);
			agentObject.put("role", user.get("role"));
			agentObject.put("status", user.get("status"));
			String out = agentObject.toJSONString();
			return Response.status(Response.Status.CREATED).entity(out).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	@DELETE
	@Path("/{agent}")
	@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
	public Response deleteAgent(@PathParam("agent") String agent) throws IOException, JDOMException, RdfException, ParserConfigurationException, TransformerException {
		try {
			String item = "ls_age";
			// check if resource exists
			String queryExist = GeneralFunctions.getAllElementsForItemID(item, agent);
			List<BindingSet> resultExist = RDF4J_20.SPARQLquery(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), queryExist);
			if (resultExist.size() < 1) {
				throw new ResourceNotAvailableException();
			}
			// insert data
			RDF4J_20.SPARQLupdate(ConfigProperties.getPropertyParam("repository"), ConfigProperties.getPropertyParam("ts_server"), deleteAgentSPARQLUPDATE(agent));
			// get result als json
			String out = Transformer.empty_JSON("agent").toJSONString();
			return Response.status(Response.Status.CREATED).entity(out).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Logging.getMessageJSON(e, "v1.rest.AgentsResource"))
					.header("Content-Type", "application/json;charset=UTF-8").build();
		}
	}

	private static String createAgentSPARQLUPDATE(String item, String itemid) throws ConfigException, IOException, UniqueIdentifierException {
		RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
		String prefixes = rdf.getPREFIXSPARQL();
		String triples = prefixes + "INSERT DATA { ";
		triples += item + ":" + itemid + " a ls:Agent . ";
		triples += item + ":" + itemid + " a foaf:Agent . ";
		triples += item + ":" + itemid + " dc:identifier \"" + itemid + "\"" + " . ";
		triples += " }";
		return triples;
	}

	private static String putAgentSPARQLUPDATE(String id) throws IOException {
		RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
		String prefixes = rdf.getPREFIXSPARQL();
		String update = prefixes
				+ "DELETE { ?agent ?p ?o. } "
				+ "WHERE { "
				+ "?agent ?p ?o. "
				+ "?agent dc:identifier ?identifier. "
				+ "FILTER (?identifier=\"$identifier\") "
				+ "FILTER (?p IN (foaf:firstName,foaf:lastName,foaf:title,dct:publisher,dct:isPartOf)) "
				+ "}";
		update = update.replace("$identifier", id);
		return update;
	}

	private static String deleteAgentSPARQLUPDATE(String id) throws IOException {
		RDF rdf = new RDF(ConfigProperties.getPropertyParam("host"));
		String prefixes = rdf.getPREFIXSPARQL();
		String update = prefixes
				+ "DELETE { ?agent ?p ?o. } "
				+ "WHERE { "
				+ "?agent ?p ?o. "
				+ "?agent dc:identifier ?identifier. "
				+ "FILTER (?identifier=\"$identifier\") "
				+ "}";
		update = update.replace("$identifier", id);
		return update;
	}

	private static OutputStream GZIP(String input, OutputStream baos) throws IOException {
		try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
			gzos.write(input.getBytes("UTF-8"));
		} catch (Exception e) {
			System.out.println(e.toString());
		}
		return baos;
	}

}
