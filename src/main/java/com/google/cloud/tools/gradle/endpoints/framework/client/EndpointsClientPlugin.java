package com.google.cloud.tools.gradle.endpoints.framework.client;

import com.google.cloud.tools.gradle.endpoints.framework.client.task.ExtractDiscoveryDocZipsTask;
import com.google.cloud.tools.gradle.endpoints.framework.client.task.GenerateClientLibrariesTask;
import com.google.cloud.tools.gradle.endpoints.framework.client.task.GenerateClientLibrarySourceTask;
import com.google.cloud.tools.gradle.endpoints.framework.server.EndpointsServerPlugin;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Plugin definition for Endpoints Clients. All tasks from this plugin are internal, it will
 * automatically generate source into build/endpointsGenSrc (see {@link EndpointsClientExtension})
 * based on the user's configuration.
 *
 * <p>Configuration of source discovery docs is from two ways:
 *
 * <p>1. specify the location of the discovery doc with the extension.
 *
 * <pre>{@code
 * endpointsClient {
 *   discoveryDocs = [file(path/to/xyz.discovery)]
 * }
 * }</pre>
 *
 * <p>2. depend directly on another project that has the endpoints server plugin.
 *
 * <pre>{@code
 * dependencies {
 *   endpointsServer project(path: ":server",
 *       configuration: {@value EndpointsServerPlugin#ARTIFACT_CONFIGURATION});
 * }
 * }</pre>
 *
 * <p>Independent of what mechanism above is used, the user must still explicitly add a dependency
 * on the google api client library.
 *
 * <pre>{@code
 * dependencies {
 *   compile "com.google.api-client:google-api-client:+"
 * }
 * }</pre>
 */
public class EndpointsClientPlugin implements Plugin<Project> {

  public static final String GENERATE_CLIENT_LIBRARY_TASK = "_endpointsClientLibs";
  public static final String GENERATE_CLIENT_LIBRARY_SRC_TASK = "_endpointsClientGenSrc";
  public static final String EXTRACT_SERVER_DISCOVERY_DOCS_TASK = "_extractServerDiscoveryDocs";

  public static final String ENDPOINTS_CLIENT_EXTENSION = "endpointsClient";
  public static final String ENDPOINTS_SERVER_CONFIGURATION = "endpointsServer";

  private Project project;
  private EndpointsClientExtension extension;

  /** Plugin entry point. */
  public void apply(Project project) {
    this.project = project;
    createExtension();
    createConfiguration();
    createExtractServerDiscoveryDocsTask();
    createGenerateClientLibTask();
    createGenerateClientLibSrcTask();
  }

  private void createExtension() {
    extension =
        project
            .getExtensions()
            .create(ENDPOINTS_CLIENT_EXTENSION, EndpointsClientExtension.class, project);
  }

  private void createConfiguration() {
    project
        .getConfigurations()
        .create(ENDPOINTS_SERVER_CONFIGURATION)
        .setDescription(
            "endpointsServer project(path: ':xyz', configuration: '"
                + EndpointsServerPlugin.ARTIFACT_CONFIGURATION
                + "')")
        .setVisible(false);
  }

  // extract discovery docs from "endpointsServer" configurations
  private void createExtractServerDiscoveryDocsTask() {
    project
        .getTasks()
        .create(
            EXTRACT_SERVER_DISCOVERY_DOCS_TASK,
            ExtractDiscoveryDocZipsTask.class,
            new Action<ExtractDiscoveryDocZipsTask>() {
              @Override
              public void execute(final ExtractDiscoveryDocZipsTask extractDiscoveryDocs) {
                extractDiscoveryDocs.setDescription("_internal");
                // iterate through the configuration and get all artifacts (discovery doc zips)
                project.afterEvaluate(
                    new Action<Project>() {
                      @Override
                      public void execute(Project project) {
                        Collection<File> files =
                            project
                                .getConfigurations()
                                .getByName(ENDPOINTS_SERVER_CONFIGURATION)
                                .getFiles();
                        extractDiscoveryDocs.setDiscoveryDocZips(files);
                        extractDiscoveryDocs.setDiscoveryDocsDir(
                            extension.getGenDiscoveryDocsDir());
                      }
                    });
              }
            });

    // make sure we depend on the server configuration build tasks, so those get run
    // before we run our task to get the discovery docs, for some reason with the android plugin,
    // this needs to be done outside the task configuration block above.
    project.afterEvaluate(
        new Action<Project>() {
          @Override
          public void execute(Project project) {
            project
                .getTasks()
                .getByName(EXTRACT_SERVER_DISCOVERY_DOCS_TASK)
                .dependsOn(
                    project
                        .getConfigurations()
                        .getByName(ENDPOINTS_SERVER_CONFIGURATION)
                        .getBuildDependencies());
          }
        });
  }

  private void createGenerateClientLibTask() {
    project
        .getTasks()
        .create(
            GENERATE_CLIENT_LIBRARY_TASK,
            GenerateClientLibrariesTask.class,
            new Action<GenerateClientLibrariesTask>() {
              @Override
              public void execute(final GenerateClientLibrariesTask genClientLibs) {
                genClientLibs.setDescription("_internal");
                genClientLibs.dependsOn(EXTRACT_SERVER_DISCOVERY_DOCS_TASK);

                project.afterEvaluate(
                    new Action<Project>() {
                      @Override
                      public void execute(Project project) {
                        genClientLibs.setClientLibraryDir(extension.getClientLibDir());
                        genClientLibs.setDiscoveryDocs(extension.getDiscoveryDocs());
                        genClientLibs.setGeneratedDiscoveryDocs(extension.getGenDiscoveryDocsDir());
                      }
                    });
              }
            });
  }

  private void createGenerateClientLibSrcTask() {
    project
        .getTasks()
        .create(
            GENERATE_CLIENT_LIBRARY_SRC_TASK,
            GenerateClientLibrarySourceTask.class,
            new Action<GenerateClientLibrarySourceTask>() {
              @Override
              public void execute(final GenerateClientLibrarySourceTask genClientLibSrc) {
                genClientLibSrc.setDescription("_internal");
                genClientLibSrc.dependsOn(GENERATE_CLIENT_LIBRARY_TASK);

                project.afterEvaluate(
                    new Action<Project>() {
                      @Override
                      public void execute(Project project) {
                        genClientLibSrc.setClientLibDir(extension.getClientLibDir());
                        genClientLibSrc.setGeneratedSrcDir(extension.getGenSrcDir());
                      }
                    });
              }
            });

    if (project.getExtensions().findByName("android") != null) {
      // special handling for android is done in groovy by the android specific plugin
      project.apply(
          ImmutableMap.of("plugin", "com.google.cloud.tools.endpoints-framework-android-client"));
    } else {
      // this is for standard java applications
      // since we are generating sources add the gen-src directory to the main java sourceset
      project
          .getTasks()
          .withType(
              AbstractCompile.class,
              new Action<AbstractCompile>() {
                @Override
                public void execute(AbstractCompile compile) {
                  compile.dependsOn(GENERATE_CLIENT_LIBRARY_SRC_TASK);
                }
              });
      JavaPluginConvention java = project.getConvention().getPlugin(JavaPluginConvention.class);
      SourceSetContainer sourceSets = java.getSourceSets();
      SourceSet mainSrc = sourceSets.getByName("main");
      mainSrc.getJava().srcDir(extension.getGenSrcDir());
    }
  }
}
