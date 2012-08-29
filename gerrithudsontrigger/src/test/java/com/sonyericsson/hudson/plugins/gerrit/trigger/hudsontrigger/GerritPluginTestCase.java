package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.maven.MavenEmbedder;
import hudson.remoting.Which;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.Recipe.Runner;

public abstract class GerritPluginTestCase extends HudsonTestCase {

	public GerritPluginTestCase() {
	}

	public GerritPluginTestCase(String name) {
		super(name);
	}

	protected void recipeLoadCurrentPlugin() throws Exception {
		final Enumeration<URL> e = getClass().getClassLoader().getResources(
				"the.hpl");
		if (!e.hasMoreElements()) {
			return; // nope
		}

		recipes.add(new Runner() {
			@Override
			public void decorateHome(HudsonTestCase testCase, File home)
					throws Exception {
				while (e.hasMoreElements()) {
					final URL hpl = e.nextElement();

					// make the plugin itself available
					Manifest m = new Manifest(hpl.openStream());
					String shortName = m.getMainAttributes().getValue(
							"Short-Name");
					if (shortName == null) {
						throw new Error(hpl
								+ " doesn't have the Short-Name attribute");
					}
					FileUtils.copyURLToFile(hpl, new File(home, "plugins/"
							+ shortName + ".hpl"));

					// make dependency plugins available
					// TODO: probably better to read POM, but where to read
					// from?
					// TODO: this doesn't handle transitive dependencies

					// Tom: plugins are now searched on the classpath first.
					// They should be available on
					// the compile or test classpath. As a backup, we do a
					// best-effort lookup in the Maven repository
					// For transitive dependencies, we could evaluate
					// Plugin-Dependencies transitively.

					String dependencies = m.getMainAttributes().getValue(
							"Plugin-Dependencies");
					if (dependencies != null) {
						MavenEmbedder embedder = new MavenEmbedder(getClass()
								.getClassLoader(), null);
						for (String dep : dependencies.split(",")) {
							String[] tokens = dep.split(":");
							String artifactId = tokens[0];
							String version = tokens[1];
							File dependencyJar = null;
							// need to search multiple group IDs
							// TODO: extend manifest to include
							// groupID:artifactID:version
							Exception resolutionError = null;
							for (String groupId : new String[] {
									"org.jvnet.hudson.plugins",
									"org.jvnet.hudson.main" }) {

								// first try to find it on the classpath.
								// this takes advantage of Maven POM located in
								// POM
								URL dependencyPomResource = getClass()
										.getResource(
												"/META-INF/maven/" + groupId
														+ "/" + artifactId
														+ "/pom.xml");
								if (dependencyPomResource != null) {
									// found it
									dependencyJar = Which
											.jarFile(dependencyPomResource);
									break;
								} else {
									Artifact a;
									a = embedder.createArtifact(groupId,
											artifactId, version, "compile"/*
																		 * doesn't
																		 * matter
																		 */,
											"hpi");
									try {
										embedder.resolve(
												a,
												Arrays.asList(embedder
														.createRepository(
																"http://maven.glassfish.org/content/groups/public/",
																"repo")),
												embedder.getLocalRepository());
										dependencyJar = a.getFile();
									} catch (AbstractArtifactResolutionException x) {
										// could be a wrong groupId
										resolutionError = x;
									}
								}
							}
							if (dependencyJar == null) {
								throw new Exception(
										"Failed to resolve plugin: " + dep,
										resolutionError);
							}

							File dst = new File(home, "plugins/" + artifactId
									+ ".hpi");
							if (!dst.exists()
									|| dst.lastModified() != dependencyJar
											.lastModified()) {
								FileUtils.copyFile(dependencyJar, dst);
							}
						}
					}
				}
			}
		});
	}

}
