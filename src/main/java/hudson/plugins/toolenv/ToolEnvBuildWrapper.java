package hudson.plugins.toolenv;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.slaves.NodeSpecific;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.kohsuke.stapler.DataBoundConstructor;

public class ToolEnvBuildWrapper extends BuildWrapper {

    public final String vars;

    @DataBoundConstructor public ToolEnvBuildWrapper(String vars) {
        this.vars = vars;
    }

    @SuppressWarnings("rawtypes")
    public @Override Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {
            public @Override void buildEnvVars(Map<String,String> env) {
                for (String var : vars.split(",")) {
                    if (var.length() == 0) {
                        continue;
                    }
                    ToolInstallation tool = toolsByVar().get(var);
                    if (tool == null) {
                        listener.error("No tool found matching " + var);
                        continue;
                    }
                    if (tool instanceof NodeSpecific) {
                        try {
                            tool = (ToolInstallation) ((NodeSpecific<?>) tool).forNode(Computer.currentComputer().getNode(), listener);
                        } catch (Exception x) {
                            x.printStackTrace(listener.error("Could not install " + var));
                            continue;
                        }
                    }
                    if (tool instanceof EnvironmentSpecific) {
                        EnvVars e = new EnvVars(env);
                        tool = (ToolInstallation) ((EnvironmentSpecific<?>) tool).forEnvironment(e);
                    }
                    String home = tool.getHome();
                    listener.getLogger().println("Setting " + var + "=" + home);
                    env.put(var, home);
                }
            }
        };
    }

    private static Map<String,ToolInstallation> toolsByVar() {
        Map<String,ToolInstallation> r = new TreeMap<String,ToolInstallation>();
        for (ToolDescriptor<?> desc : ToolInstallation.all()) {
            for (ToolInstallation inst : desc.getInstallations()) {
                r.put(inst.getName().replaceAll("[^a-zA-Z0-9_]+", "_").toUpperCase(Locale.ENGLISH) + "_HOME", inst);
            }
        }
        return r;
    }

    public static Collection<String> availableVariableNames() {
        return toolsByVar().keySet();
    }

    @Extension public static class Descriptor extends BuildWrapperDescriptor {

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(AbstractProject item) {
            return true;
        }

        public @Override String getDisplayName() {
            return "Tool Environment";
        }

    }

}
