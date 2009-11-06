package hudson.plugins.secret;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.security.Permission;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class SecretBuildWrapper extends BuildWrapper {

    public final String var;

    @DataBoundConstructor public SecretBuildWrapper(String var) {
        this.var = var;
    }

    public @Override Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        FilePath secrets = build.getBuiltOn().getRootPath().child("secrets");
        secrets.mkdirs();
        secrets.chmod(/*0700*/448);
        final FilePath secret = secrets.child(UUID.randomUUID().toString());
        secret.unzipFrom(new FileInputStream(new File(build.getProject().getRootDir(), "secret.zip")));
        return new Environment() {
            public @Override void buildEnvVars(Map<String,String> env) {
                env.put(var, secret.getRemote());
            }
            public @Override boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                secret.deleteRecursive();
                return true;
            }
        };
    }

    @Extension public static class Descriptor extends BuildWrapperDescriptor {

        public boolean isApplicable(AbstractProject item) {
            return true;
        }

        public @Override String getDisplayName() {
            return "Build Secret";
        }

        // XXX why doesn't /startUpload/ work automatically? Stapler diagnostics page claims it will...
        public void doStartUpload(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            req.getView(SecretBuildWrapper.class, "startUpload.jelly").forward(req, rsp);
        }

        public void doUpload(StaplerRequest req, StaplerResponse rsp, @QueryParameter String job) throws IOException, ServletException {
            AbstractProject prj = (AbstractProject) Hudson.getInstance().getItem(job);
            prj.checkPermission(Permission.CONFIGURE);
            FileItem file = req.getFileItem("secret.file");
            if (file == null) {
                throw new ServletException("no file upload");
            }
            byte[] data = file.get();
            if (data.length < 4 || data[0] != 'P' || data[1] != 'K' || data[2] != 3 || data[3] != 4) {
                // XXX more polite error page would be preferable
                throw new ServletException("not a ZIP file");
            }
            File secretZip = new File(prj.getRootDir(), "secret.zip");
            OutputStream os = new FileOutputStream(secretZip);
            try {
                os.write(data);
            } finally {
                os.close();
            }
            try {
                Hudson.getInstance().createPath(secretZip.getAbsolutePath()).chmod(/*0600*/384);
            } catch (InterruptedException x) {
                throw (IOException) new IOException(x.toString()).initCause(x);
            }
            rsp.setContentType("text/html");
            rsp.getWriter().println("Uploaded secret ZIP of length " + data.length + ".");
        }

    }

}
