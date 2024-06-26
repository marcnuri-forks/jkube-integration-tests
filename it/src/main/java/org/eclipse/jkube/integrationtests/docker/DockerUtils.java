/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.integrationtests.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jkube.integrationtests.cli.CliUtils;
import org.eclipse.jkube.integrationtests.cli.CliUtils.CliResult;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Could be done using Docker Client included in docker-maven-plugin (or any other).
 * Current approach (use of CLI) is preferred as it's completely independent from FMP.
 */
public class DockerUtils {

  private DockerUtils() {
  }

  public static List<DockerImage> dockerImages() throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(
        "docker -l error images --format=\"{{.Repository}}\\t{{.Tag}}\\t{{.ID}}\\t{{.CreatedSince}}\"");
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Docker: %s", result.getOutput()));
    }
    return Stream.of(result.getOutput().replace("\r", "").split("\n"))
        .map(cliImageLine -> cliImageLine.split("\t"))
        .map(parsedImageLine ->
            new DockerImage(parsedImageLine[0], parsedImageLine[1], parsedImageLine[2],
                parsedImageLine[3]))
        .collect(Collectors.toList());
  }

  public static List<String> listImageFiles(String imageName, String baseDir) throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(String.format(
      "docker run --rm -t --entrypoint \"/bin/sh\" %s -c 'find %s -print'",
      imageName,
      Optional.ofNullable(baseDir).orElse("/")
    ));
    return Arrays.asList(result.getOutput().replace("\r", "").split("\n"));
  }

  public static void pull(String image) throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(String.format(
      "docker pull %s", image
    ));
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Docker image was not pulled: %s", result.getOutput()));
    }
  }

  public static List<String> getImageHistory(String imageName) throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(String.format("docker history %s", imageName));
    return Arrays.asList(result.getOutput().replace("\r", "").split("\n"));
  }

  public static void loadTar(File dockerBuildTar) throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(String.format(
      "docker load -i %s", dockerBuildTar.getAbsolutePath()
    ));
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Docker image was not loaded: %s", result.getOutput()));
    }
  }

  public static List<String> listDockerVolumeNames() throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand("docker volume ls --format=\"{{.Name}}\"");
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Error in listing docker volumes: %s", result.getOutput()));
    }
    return Arrays.asList(result.getOutput().split("\r?\n"));
  }

  public static Map<String, String> getLabels(String id) throws IOException, InterruptedException {
    final CliResult result = CliUtils.runCommand(String.format("docker inspect -f \"{{json .Config.Labels}}\" %s", id));
    if (result.getExitCode() != 0) {
      throw new IOException(String.format("Error in getting labels: %s", result.getOutput()));
    }
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(result.getOutput(), Map.class);
  }

  public static final class DockerImage {

    private final String repository;
    private final String tag;
    private final String id;
    private final String createdSince;

    private DockerImage(String repository, String tag, String id, String createdSince) {
      this.repository = repository;
      this.tag = tag;
      this.id = id;
      this.createdSince = createdSince;
    }

    public String getRepository() {
      return repository;
    }

    public String getTag() {
      return tag;
    }

    public String getId() {
      return id;
    }

    public String getCreatedSince() {
      return createdSince;
    }
  }
}
