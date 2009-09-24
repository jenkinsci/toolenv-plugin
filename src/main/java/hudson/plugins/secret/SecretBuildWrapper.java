package hudson.plugins.secret;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class SecretBuildWrapper extends BuildWrapper {

    public final String var;

    @DataBoundConstructor public SecretBuildWrapper(String var) {
        this.var = var;
    }

    public @Override Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final FilePath secret = build.getBuiltOn().getRootPath().child("secrets").child(UUID.randomUUID().toString());
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

        public @Override BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                Object _projectName = req.getSubmittedForm().get("name");
                if (_projectName instanceof String) {
                    AbstractProject prj = (AbstractProject) Hudson.getInstance().getItem((String) _projectName);
                    FileItem file = req.getFileItem("secret.file");
                    if (file != null) {
                        byte[] data = file.get();
                        if (data.length > 0) {
                            if (data.length < 4 || data[0] != 'P' || data[1] != 'K' || data[2] != 3 || data[3] != 4) {
                                throw new FormException("Not a ZIP file", "secret.file");
                            }
                            OutputStream os = new FileOutputStream(new File(prj.getRootDir(), "secret.zip"));
                            try {
                                os.write(data);
                            } finally {
                                os.close();
                            }
                        } else {
                            // apparently if the file is omitted, we get a zero-length file, so this is normal
                        }
                    }
                }
            } catch (Exception x) {
                throw new FormException(x, "secret.file");
            }
            return super.newInstance(req, formData);
        }

    }

}
