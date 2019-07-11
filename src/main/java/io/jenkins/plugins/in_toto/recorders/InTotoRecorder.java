/**
 *
 */
package io.jenkins.plugins.in_toto.recorders;

import io.github.in_toto.models.Link;
import io.github.in_toto.models.Link.LinkBuilder;
import io.jenkins.plugins.in_toto.InTotoServiceConfiguration;
import io.github.in_toto.models.Metablock;
import io.github.intoto.service.client.InTotoServiceLinkTransporter;
import io.github.in_toto.keys.Key;
import io.github.in_toto.keys.RSAKey;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.FilePath;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;
import hudson.security.ACL;

import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.io.Reader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

/**
 *
 * Jenkins recorder plugin to output signed link metadata for Jenkins pipeline
 * steps.
 *
 * @author SantiagoTorres
 */
public class InTotoRecorder extends Recorder {

    /**
     * Credential id with private key to load.
     *
     * If not defined signing will not be performed.
     */
    private String privateKeyCredentialId;

    /**
     * Name of the step to execute.
     *
     * If not defined, will default to step
     */
    private String stepName;

    /**
     * Link metadata used to record this step
     *
     */
    private LinkBuilder linkBuilder;

    /**
     * Loaded key used to sign metadata
     */
    private Key key;

    /**
     * The current working directory (to be recorded as context).
     *
     */
    private FilePath cwd;
    
    private InTotoServiceLinkTransporter transport;

    @DataBoundConstructor
    public InTotoRecorder(String supplyChainId, String privateKeyCredentialId, String stepName)
    {

        this.stepName = stepName;

        /* notice how we can't do the same for the key, as that'd be a security
         * hazard */

        this.privateKeyCredentialId = privateKeyCredentialId;

        if (privateKeyCredentialId != null && privateKeyCredentialId.length() != 0 ) {
            try {
                loadKey(new InputStreamReader(getCredentials().getContent(), "UTF-8"));
            } catch (IOException e) {
                throw new RuntimeException("privateKey '" + privateKeyCredentialId + "' can't be read. ");
            }
        }
        
        transport = InTotoServiceConfiguration.get().getTranporter(supplyChainId);
    }

    @Override
    public boolean prebuild(AbstractBuild<?,?> build, BuildListener listener)  {

        this.cwd = build.getWorkspace();
        String  cwdStr;
        if (this.cwd != null) {
            cwdStr = this.cwd.getRemote();
        } else {
            throw new RuntimeException("[in-toto] Cannot get the build workspace");
        }

        listener.getLogger().println("[in-toto] Recording state before build " + cwdStr);
        listener.getLogger().println("[in-toto] using step name: " + stepName);

        this.linkBuilder = new LinkBuilder(this.stepName).addMaterial(Arrays.asList(cwdStr));
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    	
    	listener.getLogger().println("[in-toto] Recording state after build ");

        Metablock<Link> metablock = new Metablock<Link>(this.linkBuilder.addProduct(Arrays.asList(this.cwd.getRemote())).build(), null);
        
        if ( this.privateKeyCredentialId != null && this.privateKeyCredentialId.length() != 0 ) {
            listener.getLogger().println("[in-toto] Signing with credentials '"
                    + this.privateKeyCredentialId + "' " + " and keyid: " + this.key.computeKeyId());
            metablock.sign(this.key);
        } else {
            listener.getLogger().println("[in-toto] Warning! no key specified. Not signing...");
        }

        if (transport == null) {
            listener.getLogger().println("[in-toto] No transport specified (or transport not supported)");
            throw new RuntimeException("In Toto Service isn't configured");
        } else {
            listener.getLogger().println("[in-toto] Dumping metadata to: " + transport);
        }
        
        transport.dump(metablock);
        return true;
    }

    private void loadKey(Reader reader) {
        this.key = RSAKey.readPemBuffer(reader);
    }

    public String getprivateKeyCredentialId() {
        return this.privateKeyCredentialId;
    }

    public String getStepName() {
        return this.stepName;
    }

    protected final FileCredentials getCredentials() throws IOException {
        FileCredentials fileCredential = CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                    FileCredentials.class,
                    Jenkins.get(),
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()
            ),
            CredentialsMatchers.withId(privateKeyCredentialId)
            );

        if ( fileCredential == null )
            throw new RuntimeException(" Could not find credentials entry with ID '" + privateKeyCredentialId + "' ");

        return fileCredential;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link InTotoRecorder}. Used as a singleton. The class is
     * marked as public so that it can be accessed from views.
     *
     *
     * See
     * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {return "in-toto provenance plugin";}

        /**
         * populating the private key credentialId drop-down list
         */
        public ListBoxModel doFillPrivateKeyCredentialIdItems(@AncestorInPath Item item, @QueryParameter String privateKeyCredentialId) {

            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(privateKeyCredentialId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(privateKeyCredentialId);
                }
            }
            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM,
                    Jenkins.get(),
                    FileCredentials.class)
                    .includeCurrentValue(privateKeyCredentialId);
        }

        /**
         * validating the credentialId
         */
        public FormValidation doCheckprivateKeyCredentialId(@AncestorInPath Item item, @QueryParameter String value) {
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            return FormValidation.ok();
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
         return BuildStepMonitor.NONE;
    }
}
