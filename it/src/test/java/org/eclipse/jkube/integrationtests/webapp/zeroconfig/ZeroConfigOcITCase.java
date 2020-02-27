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
package org.eclipse.jkube.integrationtests.webapp.zeroconfig;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.shared.invoker.InvocationResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.util.Properties;

import static org.eclipse.jkube.integrationtests.Hacks.hackToPreventNullPointerInRegistryServiceCreateAuthConfig;
import static org.eclipse.jkube.integrationtests.Locks.CLUSTER_RESOURCE_INTENSIVE;
import static org.eclipse.jkube.integrationtests.OpenShift.cleanUpCluster;
import static org.eclipse.jkube.integrationtests.OpenShift.giveTheClusterABreak;
import static org.eclipse.jkube.integrationtests.Tags.OPEN_SHIFT;
import static org.eclipse.jkube.integrationtests.assertions.DockerAssertion.assertImageWasRecentlyBuilt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Tag(OPEN_SHIFT)
@TestMethodOrder(OrderAnnotation.class)
class ZeroConfigOcITCase extends ZeroConfig {

  private OpenShiftClient oc;

  @BeforeEach
  void setUp() {
    oc = new DefaultKubernetesClient().adapt(OpenShiftClient.class);
  }

  @AfterEach
  void tearDown() {
    oc.close();
    oc = null;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return oc;
  }

  @Test
  @Order(1)
  @DisplayName("oc:build, in docker mode, should create image")
  void ocBuild() throws Exception {
    // Given
    hackToPreventNullPointerInRegistryServiceCreateAuthConfig("fabric8/tomcat-9:1.2.1");
    final Properties properties = new Properties();
    properties.setProperty("jkube.mode", "kubernetes"); // S2I doesn't support webapp yet
    // When
    final InvocationResult invocationResult = maven("oc:build", properties);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertImageWasRecentlyBuilt("integration-tests", "webapp-zero-config");
  }

  @Test
  @Order(2)
  @DisplayName("oc:resource, should create manifests")
  void ocResource() throws Exception {
    // Given
    final Properties properties = new Properties();
    properties.setProperty("jkube.mode", "kubernetes"); // S2I doesn't support webapp yet
    // When
    final InvocationResult invocationResult = maven("oc:resource", properties);
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    final File metaInfDirectory = new File(
      String.format("../%s/target/classes/META-INF", PROJECT_ZERO_CONFIG));
    assertThat(metaInfDirectory.exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-deploymentconfig.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-route.yml"). exists(), equalTo(true));
    assertThat(new File(metaInfDirectory, "jkube/openshift/webapp-zero-config-service.yml"). exists(), equalTo(true));
  }

  @Test
  @Order(3)
  @ResourceLock(value = CLUSTER_RESOURCE_INTENSIVE, mode = READ_WRITE)
  @DisplayName("oc:apply, should deploy pod and service")
  void ocApply() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:apply");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldApplyResources();
  }

  @Test
  @Order(4)
  @DisplayName("oc:undeploy, should delete all applied resources")
  void ocUndeploy() throws Exception {
    // When
    final InvocationResult invocationResult = maven("oc:undeploy");
    // Then
    assertThat(invocationResult.getExitCode(), Matchers.equalTo(0));
    assertThatShouldDeleteAllAppliedResources(this);
    cleanUpCluster(oc, this);
    giveTheClusterABreak();
  }

}
