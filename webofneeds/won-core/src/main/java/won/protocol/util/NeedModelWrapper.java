package won.protocol.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.rdf.model.impl.StatementImpl;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathParser;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import won.protocol.exception.DataIntegrityException;
import won.protocol.exception.IncorrectPropertyCountException;
import won.protocol.message.WonMessageBuilder;
import won.protocol.model.NeedContentPropertyType;
import won.protocol.model.NeedGraphType;
import won.protocol.model.NeedState;
import won.protocol.vocabulary.WON;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class wraps the need models (need and sysinfo graphs in a need dataset).
 * It provides abstraction for the need structure of is/seeks content nodes that are part of the need model.
 * It can be used to load and query an existing need dataset (or models).
 * Furthermore it can be used to create a need model by adding triples.
 * <p>
 * Created by hfriedrich on 16.03.2017.
 */
public class NeedModelWrapper {
    protected Model needModel;
    protected Model sysInfoModel;

    /**
     * Create a new need model (incluing sysinfo)
     *
     * @param needUri need uri to create the need models for
     */
    public NeedModelWrapper(String needUri) {

        needModel = ModelFactory.createDefaultModel();
        DefaultPrefixUtils.setDefaultPrefixes(needModel);
        needModel.createResource(needUri, WON.NEED);
        sysInfoModel = ModelFactory.createDefaultModel();
        DefaultPrefixUtils.setDefaultPrefixes(sysInfoModel);
        sysInfoModel.createResource(needUri, WON.NEED);
    }

    /**
     * Load a need dataset and extract the need and sysinfo models from it
     *
     * @param ds need dataset to load
     */
    public NeedModelWrapper(Dataset ds) {

        Iterator<String> iter = ds.listNames();
        while (iter.hasNext()) {
            String m = iter.next();
            if (m.endsWith("#need") || m.contains(WonMessageBuilder.CONTENT_URI_SUFFIX)) {
                needModel = ds.getNamedModel(m);
                needModel.setNsPrefixes(ds.getDefaultModel().getNsPrefixMap());
            } else if (m.endsWith("#sysinfo")) {
                sysInfoModel = ds.getNamedModel(m);
                sysInfoModel.setNsPrefixes(ds.getDefaultModel().getNsPrefixMap());
            }
        }

        if ((sysInfoModel == null) && (needModel != null)) {
            this.sysInfoModel = ModelFactory.createDefaultModel();
            DefaultPrefixUtils.setDefaultPrefixes(this.sysInfoModel);
            this.sysInfoModel.createResource(getNeedUri(), WON.NEED);
        }

        if ((needModel == null) && (sysInfoModel != null)) {
            this.needModel = ModelFactory.createDefaultModel();
            DefaultPrefixUtils.setDefaultPrefixes(this.needModel);
            this.needModel.createResource(getNeedNode(NeedGraphType.SYSINFO).getURI(), WON.NEED);
        }

        checkModels();
    }

    /**
     * Load the need and sysinfo models, if one of these models is null then initialize the other one as default model
     *
     * @param needModel
     * @param sysInfoModel
     */
    public NeedModelWrapper(Model needModel, Model sysInfoModel) {

        this.needModel = needModel;
        this.sysInfoModel = sysInfoModel;

        if ((sysInfoModel == null) && (needModel != null)) {
            this.sysInfoModel = ModelFactory.createDefaultModel();
            DefaultPrefixUtils.setDefaultPrefixes(this.sysInfoModel);
            this.sysInfoModel.createResource(getNeedUri(), WON.NEED);
        }

        if ((needModel == null) && (sysInfoModel != null)) {
            this.needModel = ModelFactory.createDefaultModel();
            DefaultPrefixUtils.setDefaultPrefixes(this.needModel);
            this.needModel.createResource(getNeedNode(NeedGraphType.SYSINFO).getURI(), WON.NEED);
        }

        checkModels();
    }

    /**
     * Indicates if the wrapped data looks like need data.
     * @return
     */
    public boolean isANeed(){
        return getNeedNode(NeedGraphType.NEED) != null;
    }


    private void checkModels() {
        try {
            getNeedNode(NeedGraphType.NEED);
            getNeedNode(NeedGraphType.SYSINFO);
        } catch (NullPointerException e1) {
            throw new DataIntegrityException("at least one graph of need or sysinfo must exist in dataset", e1);
        } catch (IncorrectPropertyCountException e2) {
            throw new DataIntegrityException("need and sysinfo models must be a won:Need");
        }
    }

    /**
     * get the need or sysinfo model
     *
     * @param graph type specifies the need or sysinfo model to return
     * @return need or sysinfo model
     */
    public Model getNeedModel(NeedGraphType graph) {

        if (graph.equals(NeedGraphType.NEED)) {
            return needModel;
        } else {
            return sysInfoModel;
        }
    }

    /**
     * get the node of the need of either the need model or the sysinfo model
     *
     * @param graph type specifies the need or sysinfo need node to return
     * @return need or sysinfo need node
     */
    public Resource getNeedNode(NeedGraphType graph) {

        if (graph.equals(NeedGraphType.NEED)) {
            return RdfUtils.findOneSubjectResource(needModel, RDF.type, WON.NEED);
        } else {
            return RdfUtils.findOneSubjectResource(sysInfoModel, RDF.type, WON.NEED);
        }
    }

    public String getNeedUri() {
        return getNeedNode(NeedGraphType.NEED).getURI();
    }

    public void addFlag(Resource flag) {
        getNeedNode(NeedGraphType.NEED).addProperty(WON.HAS_FLAG, flag);
    }

    public boolean hasFlag(Resource flag) {
        return getNeedNode(NeedGraphType.NEED).hasProperty(WON.HAS_FLAG, flag);
    }

    public void addFacetUri(String facetUri) {

        Resource facet = needModel.createResource(facetUri);
        getNeedNode(NeedGraphType.NEED).addProperty(WON.HAS_FACET, facet);
    }

    public Collection<String> getFacetUris() {

        Collection<String> facetUris = new LinkedList<>();
        NodeIterator iter = needModel.listObjectsOfProperty(getNeedNode(NeedGraphType.NEED), WON.HAS_FACET);
        while (iter.hasNext()) {
            facetUris.add(iter.next().asResource().getURI());
        }
        return facetUris;
    }

    public void setNeedState(NeedState state) {

        Resource stateRes = NeedState.ACTIVE.equals(state) ? WON.NEED_STATE_ACTIVE : WON.NEED_STATE_INACTIVE;
        Resource need = getNeedNode(NeedGraphType.SYSINFO);
        need.removeAll(WON.IS_IN_STATE);
        need.addProperty(WON.IS_IN_STATE, stateRes);
    }

    public NeedState getNeedState() {
        sysInfoModel.enterCriticalSection(true);
        RDFNode state = RdfUtils.findOnePropertyFromResource(sysInfoModel, getNeedNode(NeedGraphType.SYSINFO), WON.IS_IN_STATE);
        sysInfoModel.leaveCriticalSection();
        if (state.equals(WON.NEED_STATE_ACTIVE)) {
            return NeedState.ACTIVE;
        } else {
            return NeedState.INACTIVE;
        }
    }

    public ZonedDateTime getCreationDate() {

        String dateString = RdfUtils.findOnePropertyFromResource(
                sysInfoModel, getNeedNode(NeedGraphType.SYSINFO), DCTerms.created).asLiteral().getString();
        return ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME);
    }

    public void setConnectionContainerUri(String containerUri) {
        Resource container = sysInfoModel.createResource(containerUri);
        Resource need = getNeedNode(NeedGraphType.SYSINFO);
        need.removeAll(WON.HAS_CONNECTIONS);
        need.addProperty(WON.HAS_CONNECTIONS, container);
    }

    public String getConnectionContainerUri() {
        return RdfUtils.findOnePropertyFromResource(
                sysInfoModel, getNeedNode(NeedGraphType.SYSINFO), WON.HAS_CONNECTIONS).asResource().getURI();
    }

    public void setWonNodeUri(String nodeUri) {

        Resource node = sysInfoModel.createResource(nodeUri);
        Resource need = getNeedNode(NeedGraphType.SYSINFO);
        need.removeAll(WON.HAS_WON_NODE);
        need.addProperty(WON.HAS_WON_NODE, node);
    }

    public String getWonNodeUri() {
        return RdfUtils.findOnePropertyFromResource(
                sysInfoModel, getNeedNode(NeedGraphType.SYSINFO), WON.HAS_WON_NODE).asResource().getURI();
    }

    /**
     * create a content node below the need node of the need model.
     *
     * @param type specifies which property (e.g. IS, SEEKS, ...) is used to connect the need node with the content node
     * @param uri  uri of the content node, if null then create blank node
     * @return content node created
     */
    public Resource createContentNode(NeedContentPropertyType type, String uri) {

        if (NeedContentPropertyType.ALL.equals(type)) {
            throw new IllegalArgumentException("NeedContentPropertyType.ALL not defined for this method");
        }

        Resource contentNode = (uri != null) ? needModel.createResource(uri) : needModel.createResource();
        addContentPropertyToNeedNode(type, contentNode);
        return contentNode;
    }

    private void addContentPropertyToNeedNode(NeedContentPropertyType type, RDFNode contentNode) {

        Resource needNode = getNeedNode(NeedGraphType.NEED);
        if (NeedContentPropertyType.IS.equals(type)) {
            needNode.addProperty(WON.IS, contentNode);
        } else if (NeedContentPropertyType.SEEKS.equals(type)) {
            needNode.addProperty(WON.SEEKS, contentNode);
        } else if (NeedContentPropertyType.SEEKS_SEEKS.equals(type)) {
            Resource intermediate = needModel.createResource();
            needNode.addProperty(WON.SEEKS, intermediate);
            intermediate.addProperty(WON.SEEKS, contentNode);
        } else if (NeedContentPropertyType.IS_AND_SEEKS.equals(type)) {
            needNode.addProperty(WON.IS, contentNode);
            needNode.addProperty(WON.SEEKS, contentNode);
        }
    }

    public NeedContentPropertyType getContentPropertyType(Resource contentNode) {

        boolean is = getContentNodes(NeedContentPropertyType.IS).size() > 0;
        boolean seeks = getContentNodes(NeedContentPropertyType.SEEKS).size() > 0;
        boolean seeksSeeks = getContentNodes(NeedContentPropertyType.SEEKS_SEEKS).size() > 0;

        if (is && seeks && seeksSeeks) {
            return NeedContentPropertyType.ALL;
        } else if (is && seeks) {
            return NeedContentPropertyType.IS_AND_SEEKS;
        } else if (is) {
            return NeedContentPropertyType.IS;
        } else if (seeks) {
            return NeedContentPropertyType.SEEKS;
        } else if (seeksSeeks) {
            return NeedContentPropertyType.SEEKS_SEEKS;
        }

        return null;
    }

    /**
     * get all content nodes of a specified type
     *
     * @param type specifies which content nodes to return (IS, SEEKS, ALL, ...)
     * @return content nodes
     */
    public Collection<Resource> getContentNodes(NeedContentPropertyType type) {

        Collection<Resource> contentNodes = new LinkedList<>();
        String queryClause = null;
        String isClause = "{ ?needNode a won:Need. ?needNode won:is ?contentNode. }";
        String isAndSeeksClause = "{ ?needNode a won:Need. ?needNode won:is ?contentNode. ?needNode won:seeks ?contentNode. }";
        String seeksClause = "{ ?needNode a won:Need. ?needNode won:seeks ?contentNode. FILTER NOT EXISTS { ?needNode won:seeks/won:seeks ?contentNode. } }";
        String seeksSeeksClause = "{ ?needNode a won:Need. ?needNode won:seeks/won:seeks ?contentNode. }";

        switch (type) {
            case IS:
                queryClause = isClause;
                break;
            case SEEKS:
                queryClause = seeksClause;
                break;
            case IS_AND_SEEKS:
                queryClause = isAndSeeksClause;
                break;
            case SEEKS_SEEKS:
                queryClause = seeksSeeksClause;
                break;
            case ALL:
                queryClause = isClause + "UNION \n" + seeksClause + "UNION \n" + seeksSeeksClause;
        }

        String queryString = "prefix won: <http://purl.org/webofneeds/model#> \n" +
                "SELECT DISTINCT ?contentNode WHERE { \n" + queryClause + "\n }";

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, needModel);
        ResultSet rs = qexec.execSelect();

        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            if (qs.contains("contentNode")) {
                contentNodes.add(qs.get("contentNode").asResource());
            }
        }

        return contentNodes;
    }

    public void setContentPropertyStringValue(NeedContentPropertyType type, Property p, String value) {

        Collection<Resource> nodes = getContentNodes(type);
        for (Resource node : nodes) {
            node.removeAll(p);
            node.addLiteral(p, value);
        }
    }

    public void addContentPropertyStringValue(NeedContentPropertyType type, Property p, String value) {

        Collection<Resource> nodes = getContentNodes(type);
        for (Resource node : nodes) {
            node.addLiteral(p, value);
        }
    }

    public String getContentPropertyStringValue(Resource contentNode, Property p) {

        RDFNode node = RdfUtils.findOnePropertyFromResource(needModel, contentNode, p);
        if (node != null && node.isLiteral()) {
            return node.asLiteral().getString();
        }

        return null;
    }

    public String getContentPropertyStringValue(NeedContentPropertyType type, Property p) {
        return getContentPropertyObject(type, p).asLiteral().getString();
    }

    public String getContentPropertyStringValue(NeedContentPropertyType type, String propertyPath) {
        Node node = getContentPropertyObject(type, propertyPath);
        return node != null ? node.getLiteralLexicalForm() : null;
    }

    public Collection<String> getContentPropertyStringValues(Resource contentNode, Property p, String language) {

        Collection<String> values = new LinkedList<>();
        NodeIterator nodeIterator = needModel.listObjectsOfProperty(contentNode, p);
        while (nodeIterator.hasNext()) {
            Literal literalValue = nodeIterator.next().asLiteral();
            if (language == null || language.equals(literalValue.getLanguage())) {
                values.add(literalValue.getString());
            }
        }

        return values;
    }

    public Collection<String> getContentPropertyStringValues(NeedContentPropertyType type, Property p, String language) {

        Collection<String> values = new LinkedList<>();
        Collection<Resource> nodes = getContentNodes(type);
        for (Resource node : nodes) {
            Collection valuesOfContentNode = getContentPropertyStringValues(node, p, language);
            values.addAll(valuesOfContentNode);
        }
        return values;
    }

    /**
     * Returns one of the possibly many specified values. The specified preferred languages will be tried first in the specified order.
     * @param contentNode
     * @return the string value or null if nothing is found
     */
    public String getSomeContentPropertyStringValue(Resource contentNode, Property p){
        return getSomeContentPropertyStringValue(contentNode, p, null);
    }

    /**
     * Returns one of the possibly many specified values. The specified preferred languages will be tried first in the specified order.
     * @param contentNode
     * @param preferredLanguages String array of a non-empty language tag as defined by https://tools.ietf.org/html/bcp47. The language tag must be well-formed according to section 2.2.9 of https://tools.ietf.org/html/bcp47.
     * @return the string value or null if nothing is found
     */
    public String getSomeContentPropertyStringValue(Resource contentNode, Property p, String... preferredLanguages){
        Collection<String> values = null;
        if(preferredLanguages != null){
            for (int i = 0; i < preferredLanguages.length; i++){
                values = getContentPropertyStringValues(contentNode, p, preferredLanguages[i]);
                if (values != null && values.size() > 0) return values.iterator().next();
            }
        }
        values = getContentPropertyStringValues(contentNode, p, null);
        if (values != null && values.size() > 0) return values.iterator().next();
        return null;
    }

    /**
     * Returns one of the possibly many specified values. The specified preferred languages will be tried first in the specified order.
     * @param preferredLanguages String array of a non-empty language tag as defined by https://tools.ietf.org/html/bcp47. The language tag must be well-formed according to section 2.2.9 of https://tools.ietf.org/html/bcp47.
     * @return the string value or null if nothing is found
     */
    public String getSomeContentPropertyStringValue(NeedContentPropertyType type, Property p, String... preferredLanguages){
        Collection<Resource> nodes = getContentNodes(type);
        if(preferredLanguages != null) {
            for (int i = 0; i < preferredLanguages.length; i++) {
                for (Resource node : nodes) {
                    String valueOfContentNode = getSomeContentPropertyStringValue(node, p, preferredLanguages[i]);
                    if (valueOfContentNode != null) return valueOfContentNode;
                }
            }
        }
        for (Resource node : nodes) {
            String valueOfContentNode = getSomeContentPropertyStringValue(node, p);
            if (valueOfContentNode != null) return valueOfContentNode;
        }
        return null;
    }


    private RDFNode getContentPropertyObject(NeedContentPropertyType type, Property p) {

        Collection<Resource> nodes = getContentNodes(type);
        RDFNode object = null;
        for (Resource node : nodes) {
            NodeIterator nodeIterator = needModel.listObjectsOfProperty(node, p);
            if (nodeIterator.hasNext()) {
                if (object != null) {
                    throw new IncorrectPropertyCountException("expected exactly one occurrence of property " + p.getURI(), 1, 2);
                }
                object = nodeIterator.next();
            }
        }

        if (object == null) {
            throw new IncorrectPropertyCountException("expected exactly one occurrence of property " + p.getURI(), 1, 0);
        }

        return object;
    }

    private Node getContentPropertyObject(NeedContentPropertyType type, String propertyPath) {

        Path path = PathParser.parse(propertyPath, DefaultPrefixUtils.getDefaultPrefixes());
        Collection<Resource> nodes = getContentNodes(type);

        if (nodes.size() != 1) {
            throw new IncorrectPropertyCountException("expected exactly one occurrence of object for property path " +
                    propertyPath, 1, nodes.size());
        }

        Node node = nodes.iterator().next().asNode();
        return RdfUtils.getNodeForPropertyPath(needModel, node, path);
    }

    private boolean isSplittableNode(RDFNode node) {
        return node.isResource() &&
                (node.isAnon() ||
                        (   node.asResource().getURI().startsWith(getNeedUri()) &&
                                (! node.asResource().getURI().equals(getNeedUri()))
                        ));
    }


    private Resource copyNode(Resource node) {
        if (node.isAnon()) return node.getModel().createResource();
        int i = 0;
        String uri = node.getURI() + RandomStringUtils.randomAlphanumeric(4);
        String newUri = uri+"_"+ i;
        while (node.getModel().containsResource(new ResourceImpl(newUri))){
            i++;
            newUri = uri+"_"+i;
        }
        return node.getModel().getResource(newUri);
    }

    /**
     * Returns a copy of the model in which no node reachable from the need node has multiple incoming edges
     * (unless the graph contains a circle, see below). This is achieved by making copies of all nodes that have multiple
     * incoming edges, such that each copy and the original get one of the incoming edges. The outgoing
     * edges of the original are replicated in the copies.
     *
     * Nodes that were newly introduced by this algorithm are never split.
     *
     * In that special case that the graph contains a circle, the resulting graph still contains a circle, and
     * possibly one or more nodes with more than one incoming edge.
     *
     * @return
     */
    public Model normalizeNeedModel() {
        Model copy = RdfUtils.cloneModel(needModel);
        Set<RDFNode> blacklist = new HashSet<>();
        RDFNode needNode = copy.getResource(getNeedUri().toString());
        //System.out.println("model before modification:");
        //RDFDataMgr.write(System.out, copy, Lang.TRIG);
        recursiveCopyWhereMultipleInEdges(needNode);
        //System.out.println("model after modifcation:");
        //RDFDataMgr.write(System.out, copy, Lang.TRIG);
        return copy;

    }

    private void recursiveCopyWhereMultipleInEdges(RDFNode node) {
        ModelModification modelModification = new ModelModification();
        recursiveCopyWhereMultipleInEdges(node, modelModification, new HashSet<>());
        modelModification.modify(node.getModel());
    }

    /**
     * If the specified node that has multiple incoming edges that have already been visited (in depth-first order, i.e.
     * on the way from the root to this node, if this node is not the root), the node is 'split', i.e. one copy is made
     * per such incoming edge. No copies are made for incoming edges from nodes that are discovered further down the tree.
     *
     * When a copy of the node is made, the subgraph reachable from the node is copied as well.
     *
     * This process is done when coming back from a depth-first recursion, i.e. smaller subgraphs are copied
     * before larger subgraphs.
     *
     * @param node
     * @param modelModification
     * @param visited
     */
    private void recursiveCopyWhereMultipleInEdges(RDFNode node, ModelModification modelModification, Collection<RDFNode> visited) {
        //a non-resource is trivially ok
        if (!node.isResource()) return;
        if (visited.contains(node)) return;
        visited.add(node);
        List<Statement> outgoingEdges = node.getModel().listStatements(node.asResource(), null, (RDFNode) null).toList();
        for(Statement stmt: outgoingEdges ){
            recursiveCopyWhereMultipleInEdges(stmt.getObject(), modelModification, visited);
        }

        if (outgoingEdges.size() > 0) {
            Set<Resource> reachableFromNode = findReachableResources(node);
            List<Statement> incomingEdges = node.getModel().listStatements(null, null, node).toList();
            incomingEdges = incomingEdges.stream().filter(stmt ->
                    ! reachableFromNode.contains(stmt.getSubject())).collect(Collectors.toList());
            if (incomingEdges.size() > 1 && isSplittableNode(node)) {
                for (Statement stmt : incomingEdges) {
                    RDFNode copy = recursiveCopy(node, modelModification);
                    Statement newEdge = new StatementImpl(stmt.getSubject(), stmt.getPredicate(), copy);
                    modelModification.add(newEdge);
                    modelModification.remove(stmt);
                    //RDFDataMgr.write(System.out, modelModification.copyAndModify(node.getModel()), Lang.TRIG);
                }
                modelModification.remove(outgoingEdges);
            }
        }
    }

    private boolean isReachableFrom(RDFNode src, RDFNode target){
        return isReachableFrom(src, target, new HashSet<>());
    }

    private boolean isReachableFrom(RDFNode src, RDFNode target, Collection<RDFNode> visited){
        if (src.equals(target)) return true;
        if (!src.isResource()) return false;
        if (visited.contains(src)) return false;
        visited.add(src);
        StmtIterator it = src.getModel().listStatements(src.asResource(), null, (RDFNode) null);
        while(it.hasNext()){
            Statement stmt = it.nextStatement();
            if (isReachableFrom(src, stmt.getObject(), visited)){
                return true;
            }
        }
        return false;
    }

    private Set<Resource> findReachableResources(RDFNode src){
        Set<Resource> reachable = new HashSet<>();
        findReachableResources(src, reachable);
        return reachable;
    }

    private void findReachableResources(RDFNode src, Set<Resource> found){
        if (!src.isResource()) return;
        if (found.contains(src)) return;
        found.add(src.asResource());
        StmtIterator it = src.getModel().listStatements(src.asResource(), null, (RDFNode) null);
        while(it.hasNext()){
            Statement stmt = it.nextStatement();
            findReachableResources(src, found);
        }
    }

    private RDFNode recursiveCopy(RDFNode node, ModelModification modelModification){
        return recursiveCopy(node, modelModification, null,null, new HashSet<>());
    }

    private RDFNode recursiveCopy(RDFNode node, ModelModification modelModification, RDFNode toReplace, RDFNode replacement, Collection<RDFNode> visited){
        if (node.equals(toReplace)) return replacement;
        if (!node.isResource()) return node;
        if (visited.contains(node)) return copyNode(node.asResource());
        visited.add(node);
        RDFNode nodeInCopy;
        if (isSplittableNode(node)) {
            nodeInCopy = copyNode(node.asResource());
            visited.add(nodeInCopy);
        } else {
            return node;
        }
        if (toReplace == null && replacement == null){
            toReplace = node;
            replacement = nodeInCopy;
        }
        List<Statement> outgoingEdges = node.getModel().listStatements(node.asResource(), null, (RDFNode) null).toList();
        for(Statement stmt: outgoingEdges ){
            RDFNode newObject = recursiveCopy(stmt.getObject(), modelModification, toReplace, replacement, visited);
            modelModification.add(new StatementImpl(nodeInCopy.asResource(), stmt.getPredicate(), newObject));
            modelModification.remove(stmt);
            //RDFDataMgr.write(System.out, modelModification.copyAndModify(node.getModel()), Lang.TRIG);
        }
        return nodeInCopy;
    }

    private class ModelModification{
        private List<Statement> statementsToAdd;
        private List<Statement> statementsToRemove;

        public ModelModification() {
            this.statementsToAdd = new LinkedList<>();
            this.statementsToRemove = new LinkedList<>();
        }

        public Collection<Statement> getStatementsToAdd() {
            return Collections.unmodifiableCollection(statementsToAdd);
        }

        public Collection<Statement> getStatementsToRemove() {
            return Collections.unmodifiableCollection(statementsToRemove);
        }

        public void add (Statement stmt){
            this.statementsToAdd.add(stmt);
        }

        public void add(Collection<Statement> statements) {
            this.statementsToAdd.addAll(statements);
        }

        public void remove(Statement stmt){
            this.statementsToRemove.add(stmt);
        }

        public void remove(Collection<Statement> statements){
            this.statementsToRemove.addAll(statements);
        }

        public void mergeModificationsFrom(ModelModification other){
            this.statementsToRemove.addAll(other.statementsToRemove);
            this.statementsToAdd.addAll(other.statementsToAdd);
        }

        public Model copyAndModify(Model model) {
            Model ret = RdfUtils.cloneModel(model);
            modify(ret);
            return ret;
        }

        public void modify(Model model){
            model.add(this.statementsToAdd);
            model.remove(this.statementsToRemove);
        }
    }

}
