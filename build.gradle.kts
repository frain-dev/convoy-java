plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "io.github.frain-dev"
version = "0.2.0"
description = "Convoy SDK for Java: webhook signature verification and an OpenAPI-generated API client."

repositories {
    mavenCentral()
}

java {
    // Broad runtime compatibility for the SDK.
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Generated API client (com.getconvoy.{api,client,models}); versions must
    // match what OpenAPI Generator (pinned in scripts/generate.sh) targets.
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.1")
    implementation("org.openapitools:jackson-databind-nullable:0.2.10")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "convoy"
            from(components["java"])
            pom {
                name.set("Convoy")
                description.set(project.description)
                url.set("https://github.com/frain-dev/convoy-java")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("Convoy")
                        email.set("info@getconvoy.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/frain-dev/convoy-java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/frain-dev/convoy-java.git")
                    url.set("https://github.com/frain-dev/convoy-java")
                }
            }
        }
    }
    repositories {
        maven {
            name = "central"
            // Sonatype Central Portal deploy endpoint. Publishing requires
            // MAVEN_CENTRAL_USERNAME/PASSWORD (Portal token) and GPG signing.
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_CENTRAL_USERNAME")
                password = System.getenv("MAVEN_CENTRAL_PASSWORD")
            }
        }
    }
}

signing {
    // Only sign when a key is provided (CI publish), so local builds/tests do
    // not require GPG material.
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (signingKey != null && !signingKey.isEmpty()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
