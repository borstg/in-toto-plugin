package io.jenkins.plugins.in_toto;

import hudson.Extension;
import hudson.util.FormValidation;
import io.github.intoto.service.client.InTotoServiceLinkTransporter;
import jenkins.model.GlobalConfiguration;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
    
    public FormValidation doCheckPort(@QueryParameter int value) {
        if (value >= 0) {
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
