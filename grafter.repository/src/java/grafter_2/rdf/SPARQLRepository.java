package grafter_2.rdf;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.rdf4j.http.client.HttpClientSessionManager;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;

public class SPARQLRepository extends org.eclipse.rdf4j.repository.sparql.SPARQLRepository {

    private HttpClientSessionManager httpClientManager;
    private Integer maxConcurrentHttpConnections;
    private boolean quadMode = false;

    public SPARQLRepository(String queryEndpoint) { super(queryEndpoint); }
    public SPARQLRepository(String queryEndpoint, String updateEndpoint) {
        super(queryEndpoint, updateEndpoint);
    }

     @Override public RepositoryConnection getConnection() throws RepositoryException {
         return new SPARQLConnection(this, createSPARQLProtocolSession(), quadMode);
     }

     public synchronized HttpClientSessionManager getHttpClientSessionManager() {
         if (this.httpClientManager == null) {
             HttpClient httpClient = newHttpClient();
             this.httpClientManager = new SPARQLClientImpl(httpClient);
         }
         return this.httpClientManager;
     }

    /**
     * Activate quad mode for this {@link SPARQLRepository}, i.e. for retrieval of statements also retrieve
     * the graph.
     * <p>
     * Note: the setting is only applied in newly created {@link SPARQLConnection}s as the setting is an
     * immutable configuration of a connection instance.
     *
     * @param flag
     *        flag to enable or disable the quad mode
     * @see SPARQLConnection#getStatements(org.eclipse.rdf4j.model.Resource, org.eclipse.rdf4j.model.URI,
     *      org.eclipse.rdf4j.model.Value, boolean, org.eclipse.rdf4j.model.Resource...)
     */
    public void enableQuadMode(boolean flag) {
        this.quadMode = flag;
    }

    @Override public synchronized void setHttpClientSessionManager(HttpClientSessionManager httpMan) {
        this.httpClientManager = httpMan;
    }

    /**
     * Gets the maximum number of concurrent TCP connections created per route
     * in the HTTP client used by this repository.
     * @return The maximum number of concurrent connections
     */
    public synchronized Integer getMaxConcurrentHttpConnections() {
        return this.maxConcurrentHttpConnections;
    }

    /**
     * Sets the number of concurrent TCP connections allowed per route for the
     * HTTP client used by this repository
     * @param maxConcurrentHttpConnections The maximum number of connections
     */
    public synchronized void setMaxConcurrentHttpConnections(Integer maxConcurrentHttpConnections) {
        this.maxConcurrentHttpConnections = maxConcurrentHttpConnections;

        //number of max concurrent connections might have changed so reset client so it will be re-created
        //on next usage with new maximum
        this.setHttpClientSessionManager(null);
    }

    // Fix for: https://github.com/eclipse/rdf4j/issues/367
    private synchronized HttpClient newHttpClient() {
        // the 'system' HTTP client uses the http.maxConnections system property
        // to define the size of its connection pool (5 is the default if
        // unset). Temporarily set this property to maxConcurrentHttpConnections
        // if specified for this repository.
        // TODO: Construct a builder which specifies all relevant settings
        // directly
        final String maxConnKey = "http.maxConnections";
        final String existingMax = System.getProperty(maxConnKey, "5");
        try {
            Integer maxConns = this.getMaxConcurrentHttpConnections();
            if (maxConns != null) {
                System.setProperty(maxConnKey, maxConns.toString());
            }
            return HttpClients.createSystem();
        }
        finally {
            System.setProperty(maxConnKey, existingMax);
        }
    }
}
