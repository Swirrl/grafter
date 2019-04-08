package grafter_2.rdf;

import org.eclipse.rdf4j.http.client.SPARQLProtocolSession;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession;


public class SPARQLConnection extends org.eclipse.rdf4j.repository.sparql.SPARQLConnection {

    public SPARQLConnection(SPARQLRepository repository, SPARQLProtocolSession sparqlSession, boolean quadMode) {
        super(repository, sparqlSession, quadMode);
    }

    public SPARQLConnection(SPARQLRepository repository, SPARQLProtocolSession sparqlSession) {
        super(repository, sparqlSession, false);
    }

    @Override public BooleanQuery prepareBooleanQuery(QueryLanguage ql, String query, String base) throws RepositoryException, MalformedQueryException {
        return handleOp(super.prepareBooleanQuery(ql, query, base));
    }

    @Override public GraphQuery prepareGraphQuery(QueryLanguage ql, String query, String base) throws RepositoryException, MalformedQueryException {
        return handleOp(super.prepareGraphQuery(ql, query, base));
    }

    @Override public TupleQuery prepareTupleQuery(QueryLanguage ql, String query, String base) throws RepositoryException, MalformedQueryException {
        return handleOp(super.prepareTupleQuery(ql, query, base));
    }

    @Override public Update prepareUpdate(QueryLanguage ql, String update, String baseURI) throws RepositoryException, MalformedQueryException {
        return handleOp(super.prepareUpdate(ql, update, baseURI));
    }

    private static <O extends Operation> O handleOp(O op) {
        op.setIncludeInferred(false);
        return op;
    }
}
