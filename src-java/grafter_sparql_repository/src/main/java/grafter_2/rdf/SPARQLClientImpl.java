package grafter_2.rdf;

import org.apache.http.client.HttpClient;
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession;
import org.eclipse.rdf4j.http.client.SharedHttpClientSessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SPARQLClientImpl extends SharedHttpClientSessionManager {

    private static final ExecutorService QUERY_EXECUTOR = Executors.newCachedThreadPool();

    public SPARQLClientImpl(HttpClient httpClient) {
        this.setHttpClient(httpClient);
    }

    @Override public SPARQLProtocolSession createSPARQLProtocolSession(String queryEndpointUrl, String updateEndpointUrl) {
        SPARQLSession session = new SPARQLSession(queryEndpointUrl, updateEndpointUrl, this.getHttpClient(), QUERY_EXECUTOR);

        return session;
    }
}
