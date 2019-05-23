/**
 *
 */
package io.jenkins.plugins.intotorecorder.transport;

import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import io.github.in_toto.models.Link;

// import com.coreos.jetcd.Client;
// import com.coreos.jetcd.data.ByteSequence;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;



public class Etcd extends Transport {

    URI uri;

    public Etcd(URI uri) {
    	// change protocol etcd -> http
    	try {
			this.uri = new URI("http", null, uri.getHost(), uri.getPort(), null, null, null);
		} catch (URISyntaxException e) {
			// already checked in Transport
		}
    }

    public void submit(Link link) {
        /* FIXME: this is how we *should* handle connectivity but Jetcd is failing
         * I'll defer this to a further checkpoint in the jetcd implementation
         * probably fails because it uses v3 while most servers are v2
        Client client = Client.builder()
                .endpoints(this.uri.toString()).build();
        client.getKVClient().put(
              ByteSequence.fromString(link.getName()),
              ByteSequence.fromString(link.toString())
        );
            WARNING: for now I'll add an unauthenticated curl-like http client
            to perform the operation instead...
            FIXME ^ FIXME ^ FIXME ^
        */
        try {
            HttpRequest request = new NetHttpTransport()
                .createRequestFactory()
                .buildPutRequest(new GenericUrl(this.uri.toString() + 
                            "/v2/keys/" + link.getFullName()),
                    ByteArrayContent.fromString("application/x-www-form-urlencoded",
                        "value=" + link.dumpString()));

            request.execute();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't serialize link: " +
                    link.getFullName() + ".Error was: " + e.toString());
        }
    }

	@Override
	public String toString() {
		return "Etcd [uri=" + uri + "]";
	}
    
}
