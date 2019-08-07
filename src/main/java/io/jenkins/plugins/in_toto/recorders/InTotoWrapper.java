/**
 *
 */
package io.jenkins.plugins.in_toto.recorders;

import io.github.in_toto.models.Link;
import io.github.in_toto.models.Link.LinkBuilder;
import io.github.in_toto.transporters.Transporter;
import io.jenkins.plugins.in_toto.InTotoServiceConfiguration;
import io.github.in_toto.models.Metablock;
import io.github.in_toto.keys.Key;
import io.github.in_toto.keys.RSAKey;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.FilePath;
import hudson.EnvVars;
import hudson.security.ACL;

import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.jenkinsci.Symbol;

import java.io.Reader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import jenkins.tasks.SimpleBuildWrapper;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

/**
 *
 * Jenkins recorder plugin to output signed link metadata for Jenkins pipeline
 * steps.
 *
 * @author SantiagoTorres
 */
public class InTotoWrapper extends SimpleBuildWrapper {

    /**
     * CredentialId for the key to load.
     *
     * If not defined signing will not be performed.
     */
    @DataBoundSetter
    public String privateKeyCredentialId;

    /**
     * Name of the step to execute.
     *
     * If not defined, will default to step
     */
    @DataBoundSetter
    public String stepName;

    /**
     * The host URL/URI where to post the in-toto metdata.
     *
     * Protocol information *must* be included.
     */
    @DataBoundSetter
    public String supplyChainId;

    /**
     * Loaded key used to sign metadata
     */
    public Key key;

    @DataBoundConstructor
    public InTotoWrapper(String privateKeyCredentialId, String stepName, String supplyChainId)
    {

        /* Set a "sensible" step name if not defined */
        if (stepName == null || stepName.length() == 0)
            stepName = "step";
        this.stepName = stepName;

        this.privateKeyCredentialId = privateKeyCredentialId;
        if(privateKeyCredentialId != null && privateKeyCredentialId.length() != 0) {
            try {
                loadKey(new InputStreamReader(getCredentials().getContent(), "UTF-8"));
            } catch (IOException e) {
                throw new RuntimeException("credential with Id '" + privateKeyCredentialId + "' can't be read. ");
            }
        }

        /* The transport property will default to the current CWD, but we can't figure that one
         * just yet
         */
        this.supplyChainId = supplyChainId;
    }

    @Override
    public void setUp(SimpleBuildWrapper.Context context,
                               Run<?,?> build,
                               FilePath workspace,
                               Launcher launcher,
                               TaskListener listener,
                               EnvVars initialEnvironment)
                        throws IOException,
                               InterruptedException {

        listener.getLogger().println("[in-toto] wrapping step ");
        listener.getLogger().println("[in-toto] using step name: " + this.stepName);
        if ( privateKeyCredentialId != null && privateKeyCredentialId.length() != 0 && this.key != null ) {
                listener.getLogger().println("[in-toto] Key fetched from credentialId " + this.privateKeyCredentialId);
            } else {
                throw new RuntimeException("[in-toto] Neither credentialId nor keyPath found for signing key! ");
        }
        
        LinkBuilder linkBuilder = new LinkBuilder(this.stepName);
        linkBuilder.addMaterial(Arrays.asList("")).setBasePath(workspace.getRemote());

        listener.getLogger().println("[in-toto] Dumping metadata... ");

        context.setDisposer(new PostWrap(linkBuilder, this.key, this.supplyChainId));
    }

    private void loadKey(Reader reader) {
        this.key = RSAKey.readPemBuffer(reader);
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
            CredentialsMatchers.withId(this.privateKeyCredentialId)
            );

        if ( fileCredential == null )
            throw new RuntimeException(" Could not find credentials entry with ID '" + privateKeyCredentialId + "' ");

        return fileCredential;
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
    @Symbol("in_toto_wrap")
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(InTotoWrapper.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "in-toto record wrapper";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

    }

    public static class PostWrap extends Disposer {

        private static final long serialVersionUID = 2;
        transient LinkBuilder linkBuilder;
        transient Key key;
        String supplyChainId;
        String stepName;

        public PostWrap(LinkBuilder linkBuilder, Key key, String supplyChainId) {
            super();

            this.linkBuilder = linkBuilder;
            this.key = key;
            
            this.supplyChainId = supplyChainId;
        }

        @Override
        public void tearDown(Run<?,?> build,
                                      FilePath workspace,
                                      Launcher launcher,
                                      TaskListener listener)
                               throws IOException,
                                      InterruptedException {

            this.linkBuilder.addProduct(Arrays.asList(""))
            	.setBasePath(workspace.getRemote());
            Metablock<Link> metablock = new Metablock<Link>(this.linkBuilder.build(), null);
            if (this.key != null) {
            	metablock.sign(key);
            } else {
                listener.getLogger().println("[in-toto] Warning! no key specified. Not signing...");
            }
            Transporter<Link> transport = InTotoServiceConfiguration.get().getTranporter(supplyChainId);
            listener.getLogger().println("[in-toto] Dumping metadata to: " + transport);
            transport.dump(metablock);
        }
    }
}
