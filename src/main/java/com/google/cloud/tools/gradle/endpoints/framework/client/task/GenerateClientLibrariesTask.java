package com.google.cloud.tools.gradle.endpoints.framework.client.task;

import com.google.api.server.spi.tools.EndpointsTool;
import com.google.api.server.spi.tools.GenClientLibAction;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Endpoints task to download a client library from the endpoints service. */
public class GenerateClientLibrariesTask extends DefaultTask {
  private File clientLibraryDir;
  private List<File> discoveryDocs;
  private File generatedDiscoveryDocsDir;

  @OutputDirectory
  public File getClientLibraryDir() {
    return clientLibraryDir;
  }

  public void setClientLibraryDir(File clientLibraryDir) {
    this.clientLibraryDir = clientLibraryDir;
  }

  @InputFiles
  public List<File> getDiscoveryDocs() {
    return discoveryDocs;
  }

  /**
   * Set discovery docs as a list of directories and files, will find all discovery docs in
   * directories as necessary.
   */
  public void setDiscoveryDocs(List<File> discoveryDocs) {
    List<File> expandedDiscoveryDocs = new ArrayList<>();
    for (File discoveryDoc : discoveryDocs) {
      if (discoveryDoc.isDirectory()) {
        expandedDiscoveryDocs.addAll(findDiscoveryDocsInDirectory(discoveryDoc));
      } else {
        expandedDiscoveryDocs.add(discoveryDoc);
      }
    }
    this.discoveryDocs = expandedDiscoveryDocs;
  }

  @InputDirectory
  public File getGeneratedDiscoveryDocsDir() {
    return generatedDiscoveryDocsDir;
  }

  public void setGeneratedDiscoveryDocs(File generatedDiscoveryDocsDir) {
    this.generatedDiscoveryDocsDir = generatedDiscoveryDocsDir;
  }

  /** Task entry point. */
  @TaskAction
  public void generateClientLibs() throws Exception {
    getProject().delete(clientLibraryDir);
    getProject().mkdir(clientLibraryDir);

    for (File discoveryDoc : discoveryDocs) {
      runEndpointsTools(discoveryDoc);
    }

    for (File discoveryDoc : findDiscoveryDocsInDirectory(generatedDiscoveryDocsDir)) {
      runEndpointsTools(discoveryDoc);
    }
  }

  private void runEndpointsTools(File discoveryDoc) throws Exception {
    List<String> params =
        Lists.newArrayList(
            Arrays.asList(
                GenClientLibAction.NAME,
                "-l",
                "java",
                "-bs",
                "gradle",
                "-o",
                clientLibraryDir.getAbsolutePath()));

    params.add(discoveryDoc.getAbsolutePath());
    new EndpointsTool().execute(params.toArray(new String[0]));
  }

  private static List<File> findDiscoveryDocsInDirectory(File discoveryDocDirectory) {
    Preconditions.checkArgument(discoveryDocDirectory.isDirectory());

    File[] discoveryDocs =
        discoveryDocDirectory.listFiles(
            new FileFilter() {
              @Override
              public boolean accept(File pathname) {
                return pathname.getName().endsWith(".discovery");
              }
            });
    return Arrays.asList(Objects.requireNonNull(discoveryDocs));
  }
}