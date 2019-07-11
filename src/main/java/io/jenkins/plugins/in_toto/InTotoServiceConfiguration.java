package io.jenkins.plugins.in_toto;

import hudson.Extension;
import hudson.util.FormValidation;
import io.github.intoto.service.client.InTotoServiceLinkTransporter;
import jenkins.model.GlobalConfiguration;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;

@Extension
public class InTotoServiceConfiguration extends GlobalConfiguration {
	public final String url = "foo";
	
    /** @return the singleton instance */
    public static InTotoServiceConfiguration get() {
        return GlobalConfiguration.all().get(InTotoServiceConfiguration.class);
    }
    
    private String hostname;

    private int port;
    
    private boolean secure;
    
    @DataBoundConstructor
    public InTotoServiceConfiguration(String hostname, int port, boolean secure) {
		super();
		this.hostname = hostname;
		this.port = port;
		this.secure = secure;
	}

	public InTotoServiceConfiguration() {
        // When Jenkins is restarted, load any saved configuration from disk.
        load();
    }    
    
    public int getPort() {
		return port;
	}


    public void setPort(int port) {
		this.port = port;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
        save();
	}

	public String getUrl() {
		return url;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
        this.hostname = hostname;
        save();
    }

    public FormValidation doCheckHostname(@QueryParameter String value) {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.warning("Please specify a hostname.");
        }
        return FormValidation.ok();
    }
    
	public FormValidation doValidateConnection(@QueryParameter String hostname, @QueryParameter int port,
			@QueryParameter boolean secure) throws IOException {
		String protocol = secure ? "https://" : "http://";
		String url = protocol + hostname + ":" + port + "/api/service/status";
		HttpRequest request = new NetHttpTransport().createRequestFactory().buildGetRequest(new GenericUrl(url));
		HttpResponse response = request.execute();
		System.out.println(response.parseAsString());
		return FormValidation.ok("Your In Toto Service instance [%s] is alive!", url);
	}
	
    public FormValidation doCheckPort(@QueryParameter String value) {
    	if (StringUtils.isEmpty(value)) {
            return FormValidation.warning("Please specify a port.");
        }
    	try {
    		Integer.valueOf(value);
    	} catch (NumberFormatException exc){
    		return FormValidation.warning("[%s] is not a number.", value);
    	}
        if (port <= 0) {
            return FormValidation.warning("0 or negative port isn't allowed.");
        }
        return FormValidation.ok();
    }
	
    public InTotoServiceLinkTransporter getTranporter(String supplyChainId) {
    	if (hostname == null) {
    		return null;
    	}
    	return new InTotoServiceLinkTransporter(supplyChainId, this.hostname, this.port, this.secure);
    }
}
