package won.matcher.service.common.service.sparql;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.expr.nodevalue.NodeValueBoolean;
import org.apache.jena.sparql.modify.UpdateProcessRemote;
import org.apache.jena.tdb.TDB;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import won.protocol.util.RdfUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Service to access of Sparql enpoint database to save or query linked data.
 *
 * User: hfriedrich
 * Date: 15.04.2015
 */
@Component
public class SparqlService
{
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected String sparqlEndpoint;
  //protected DatasetAccessor accessor;

  public static Dataset deserializeDataset(String serializedResource, Lang format) throws IOException {
    InputStream is = new ByteArrayInputStream(serializedResource.getBytes(StandardCharsets.UTF_8));
    Dataset ds = RdfUtils.toDataset(is, new RDFFormat(format));
    is.close();
    return ds;
  }

  @Autowired
  public SparqlService(@Value("${uri.sparql.endpoint}")  String sparqlEndpoint) {
    this.sparqlEndpoint = sparqlEndpoint;
    //accessor = DatasetAccessorFactory.createHTTP(sparqlEndpoint);
  }

  public String getSparqlEndpoint() {
    return sparqlEndpoint;
  }

  /**
   * Update named graph by first deleting it and afterwards inserting the triples of the new model.
   *
   * @param graph named graph to be updated
   * @param model model that holds triples to set
   */
  public String createUpdateNamedGraphQuery(String graph, Model model) {

    StringWriter sw = new StringWriter();
    RDFDataMgr.write(sw, model, Lang.NTRIPLES);
    String query = "\nCLEAR GRAPH <" + graph + ">;\n" + "\nINSERT DATA { GRAPH <" + graph + "> { " + sw + "}};\n";
    return query;
  }

  /**
   * Update a dataset of names graphs first deleting them and afterwards inserting the triples of the new models.
   *
   * @param ds
   */
  public void updateNamedGraphsOfDataset(Dataset ds) {

    String query = "";

    Iterator<String> graphNames = ds.listNames();
    while (graphNames.hasNext()) {

      log.debug("Save dataset");
      String graphName = graphNames.next();
      Model model = ds.getNamedModel(graphName);
      query += createUpdateNamedGraphQuery(graphName, model);

      // Update can also be done with accessor - use put/add?
      // accessor.add(graphName, model);
    }

    if (query != "") {
      executeUpdateQuery(query);
    }
  }

  public Model retrieveModel(String graphName) {

    String queryTemplate = "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <%s> { ?s ?p ?o } . }";
    String queryString = String.format(queryTemplate, graphName);
    Query query = QueryFactory.create(queryString);
    QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
    Model model = qexec.execConstruct();
    return model;
  }

  public Dataset retrieveDataset(String graphName) {

    DatasetGraph dsg = TDBFactory.createDatasetGraph();
    dsg.getContext().set(TDB.symUnionDefaultGraph, new NodeValueBoolean(true));
    Dataset ds = DatasetFactory.create(dsg);
    Model model = retrieveModel(graphName);
    ds.addNamedModel(graphName, model);
    return ds;
  }

  public Dataset retrieveNeedDataset(String uri) {

    String queryString = "prefix won: <http://purl.org/webofneeds/model#> select distinct ?g where { " +
      "GRAPH ?g { <" + uri + "> a won:Need. ?a ?b ?c. } }";

    Query query = QueryFactory.create(queryString);
    QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
    ResultSet results = qexec.execSelect();

    Dataset ds = DatasetFactory.createGeneral();
    while (results.hasNext()) {

      QuerySolution qs = results.next();
      String graphUri = qs.getResource("g").getURI();
      Model model = retrieveModel(graphUri);
      ds.addNamedModel(graphUri, model);
    }

    return ds;
  }

  /**
   * Execute a SPARQL Update query.
   *
   * @param updateQuery
   */
  public void executeUpdateQuery(String updateQuery) {

    log.debug("Update SPARQL Endpoint: {}", sparqlEndpoint);
    log.debug("Execute query: {}", updateQuery);
    UpdateRequest query = UpdateFactory.create(updateQuery);
    UpdateProcessRemote riStore = (UpdateProcessRemote)
      UpdateExecutionFactory.createRemote(query, sparqlEndpoint);
    riStore.execute();
  }

}
