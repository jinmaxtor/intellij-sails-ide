package com.jaumard.sails;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.browsers.StartBrowserSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jaumard.sails.bundle.SailsJSBundle;
import com.jaumard.sails.icons.SailsJSIcons;
import com.jaumard.sails.settings.SailsJSConfig;
import com.jaumard.sails.utils.SailsJSCommandLine;
import com.jaumard.sails.utils.SailsJSUtil;
import com.jetbrains.nodejs.run.NodeJSRunConfiguration;
import com.jetbrains.nodejs.run.NodeJSRunConfigurationType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Created by jaumard on 04/04/2015.
 */
public class SailsJSProjectGenerator extends WebProjectTemplate<SailsJSProjectGenerator.SailsJSProjectSettings> implements DirectoryProjectGenerator<SailsJSProjectGenerator.SailsJSProjectSettings>
{
    private static final Logger LOG = Logger.getInstance(SailsJSProjectGenerator.class);

    public SailsJSProjectGenerator()
    {

    }

    @Nls
    @NotNull
    @Override
    public String getName()
    {
        return SailsJSBundle.message("sails.conf.name");
    }

    @Override
    public String getDescription()
    {
        return "<html>This project is an application skeleton for a typical <a href=\"http://www.sailsjs.org\">Sails</a> or <a href=\"http://treeline.io\">Treeline</a> server app.<br></html>";
    }

    @Override
    public javax.swing.Icon getIcon()
    {
        return SailsJSIcons.SailsJS;
    }

    @Override
    public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                                @NotNull final SailsJSProjectSettings settings, @NotNull Module module
    )
    {
        try
        {

            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable()
            {
                @Override
                public void run()
                {

                    try
                    {
                        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                        indicator.setText("Creating...");
                        File tempProject = createTemp();
                        SailsJSCommandLine commandLine = new SailsJSCommandLine(settings.getExecutable(), tempProject.getPath());

                        if (!commandLine.isCorrectExecutable())
                        {
                            showErrorMessage("Incorrect path");
                            return;
                        }
                        settings.setName(project.getName());

                        commandLine.createNewProject(settings.name());

                        if (settings.getPpCSS().equals(SailsJSProjectSettings.PPCSS_SASS))
                        {
                            File sassFolder = new File(tempProject.getPath() + "/" + baseDir.getName() + "/assets/sass/");
                            sassFolder.mkdirs();
                            File lessFile = new File(tempProject.getPath() + "/" + baseDir.getName() + "/assets/styles/importer.less");
                            if (lessFile.exists())
                            {
                                lessFile.delete();
                            }

                            InputStream sassFile = getClass().getResourceAsStream("/js/sass.js");
                            SailsJSUtil.copyFileFromAssets(sassFile, tempProject.getPath() + "/" + baseDir.getName() + "/tasks/config/sass.js");
                            InputStream cAssetFile = getClass().getResourceAsStream("/js/compileAssets.js");
                            SailsJSUtil.copyFileFromAssets(cAssetFile, tempProject.getPath() + "/" + baseDir.getName() + "/tasks/register/compileAssets.js");
                            InputStream sAssetFile = getClass().getResourceAsStream("/js/syncAssets.js");
                            SailsJSUtil.copyFileFromAssets(sAssetFile, tempProject.getPath() + "/" + baseDir.getName() + "/tasks/register/syncAssets.js");
                            modifyPackage(tempProject.getPath() + "/" + baseDir.getName() + "/package.json");
                        }

                        File[] array = tempProject.listFiles();

                        if (array != null && array.length != 0)
                        {
                            File from = ContainerUtil.getFirstItem(ContainerUtil.newArrayList(array));
                            assert from != null;
                            FileUtil.copyDir(from, new File(baseDir.getPath()), new FileFilter()
                            {
                                @Override
                                public boolean accept(File pathname)
                                {
                                    if (pathname.getName().equals("node_modules"))
                                    {
                                        return false;
                                    }
                                    return true;
                                }
                            });
                            deleteTemp(tempProject);
                            SailsJSCommandLine nodeCommandLine = new SailsJSCommandLine(SailsJSConfig.getInstance().getNpmExecutable(), baseDir.getPath());
                            try
                            {
                                nodeCommandLine.npmInstall();
                            }
                            catch (Exception e)
                            {
                                LOG.info("npm install fail, need to be done manually");
                            }

                        }
                        else
                        {
                            showErrorMessage("Cannot find files in the directory " + tempProject.getAbsolutePath());
                        }
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            }, "Creating Sails project", false, project);

            ApplicationManager.getApplication().runWriteAction(new Runnable()
            {
                @Override
                public void run()
                {
                    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);

                    propertiesComponent.setValue(SailsJSConfig.SAILSJS_WORK_DIRECTORY, project.getBasePath());
                    SailsJSConfig state = SailsJSConfig.getInstance().getState();
                    if (!StringUtil.equals(settings.getExecutable(), state.getExecutablePath()))
                    {
                        SailsJSConfig.getInstance().loadState(new SailsJSConfig());
                    }
                    VfsUtil.markDirty(false, true, project.getBaseDir());
                    createRunConfiguration(project, settings);

                    baseDir.refresh(true, true, new Runnable()
                    {
                        @Override
                        public void run()
                        {

                        }
                    });
                }
            });
        }
        catch (Exception e)
        {
            showErrorMessage(e.getMessage());
        }
    }

    private void modifyPackage(String path)
    {
        BufferedReader br = null;
        BufferedWriter bw = null;
        try
        {
            br = new BufferedReader(new FileReader(path));
            bw = new BufferedWriter(new FileWriter(path + ".tmp"));
            String line;
            while ((line = br.readLine()) != null)
            {
                if (line.contains("grunt-contrib-less"))
                {
                    line = "    \"grunt-contrib-sass\" : \"^0.9.2\",";
                }
                bw.write(line + "\n");
            }
        }
        catch (Exception e)
        {
            return;
        }
        finally
        {
            try
            {
                if (br != null)
                {
                    br.close();
                }
            }
            catch (IOException e)
            {
                //
            }
            try
            {
                if (bw != null)
                {
                    bw.close();
                }
            }
            catch (IOException e)
            {
                //
            }
        }
        // Once everything is complete, delete old file..
        File oldFile = new File(path);
        oldFile.delete();

        // And rename tmp file's name to old file name
        File newFile = new File(path + ".tmp");
        newFile.renameTo(oldFile);
    }

    private void createRunConfiguration(final Project project, SailsJSProjectSettings settings)
    {
        final Ref<VirtualFile> appFileRef = Ref.create();

        final VirtualFile baseDir = project.getBaseDir();

        final String name = "Launch server";
        ApplicationManager.getApplication().runReadAction(new Runnable()
        {
            @Override
            public void run()
            {
                RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
                for (RunConfiguration configuration : runManager.getAllConfigurationsList())
                {
                    if (name.equals(configuration.getName()))
                    {
                        return;
                    }
                }
                appFileRef.set(VfsUtil.findRelativeFile(baseDir, "app.js"));
            }
        });
        if (appFileRef.isNull())
        {
            return;
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable()
        {
            @Override
            public void run()
            {
                ConfigurationFactory configurationFactory = NodeJSRunConfigurationType.getInstance().getDefaultConfigurationFactory();
                RunnerAndConfigurationSettings rac = RunManager.getInstance(project).createRunConfiguration("", configurationFactory);
                NodeJSRunConfiguration runConfig = ObjectUtils.tryCast(rac.getConfiguration(), NodeJSRunConfiguration.class);
                if (runConfig == null)
                {
                    return;
                }
                VirtualFile appFile = appFileRef.get();

                runConfig.setName(name);
                runConfig.setWorkingDirectory(baseDir.getPath());
                runConfig.setInputPath(VfsUtilCore.getRelativePath(appFile, baseDir, File.separatorChar));
                //runConfig.getEnvs().put("DEBUG", baseDir.getName() + ":*");

                StartBrowserSettings browserSettings = new StartBrowserSettings();
                browserSettings.setUrl("http://localhost:1337/");
                runConfig.setStartBrowserSettings(browserSettings);

                RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
                runManager.addConfiguration(rac, true);
                runManager.setSelectedConfiguration(rac);


            }
        });
    }

    protected File createTemp() throws IOException
    {
        return FileUtil.createTempDirectory("intellij-sails-generator", null, false);
    }

    protected void deleteTemp(File tempProject)
    {
        if (!SystemInfo.isWindows)//FIXME On windows, delete temp dir break sails installation !!
        {
            if (!SailsJSUtil.deleteDir(tempProject))
            {
                LOG.warn("Cannot delete " + tempProject);
            }
            else
            {
                LOG.info("Successfully deleted " + tempProject);
            }
        }
    }

    @NotNull
    @Override
    public GeneratorPeer<SailsJSProjectSettings> createPeer()
    {
        return new SailsJSProjectPeer();
    }

    static public class SailsJSProjectSettings
    {
        public static final String PPCSS_SASS = "SASS";
        public static final String PPCSS_LESS = "LESS";
        private String name = "example";
        private String npmExecutable = null;
        private String ppCSS = null;
        private String executable;

        public SailsJSProjectSettings()
        {
        }

        public String getNpmExecutable()
        {
            return npmExecutable;
        }

        public void setNpmExecutable(String npmExecutable)
        {
            this.npmExecutable = npmExecutable;
        }

        public void setExecutable(String executable)
        {
            this.executable = executable;
        }

        public String getExecutable()
        {
            return executable;
        }

        public String name()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getPpCSS()
        {
            return ppCSS;
        }

        public void setPpCSS(String ppCSS)
        {
            this.ppCSS = ppCSS;
        }
    }

    private static void showErrorMessage(@NotNull String message)
    {
        String fullMessage = "Error creating Sails App. " + message;
        String title = "Create Sails Project";
        Notifications.Bus.notify(
                new Notification("Sails Generator", title, fullMessage, NotificationType.ERROR)
        );
    }

    /*
    @NotNull
    @Override
    protected String getDisplayName()
    {
        return "SailsJS";
    }

    @NotNull
    @Override
    public String getGithubUserName()
    {
        return "balderdashy";
    }

    @NotNull
    @Override
    public String getGithubRepositoryName()
    {
        return "sails";
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return "<html>This project is an application skeleton for a typical <a href=\"https://sails.org\">SailsJS</a> server app.<br>" +
                "Don't forget to install dependencies by running<pre>npm install</pre></html>";
    }

    @Override
    public Icon getIcon()
    {
        return SailsJSIcons.SailsJS;
    }

    @Nullable
    @Override
    public String getPrimaryZipArchiveUrlForDownload(@NotNull GithubTagInfo tag)
    {
        return null;
    }
    */
}
