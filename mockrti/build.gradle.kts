/*
 * CUIS Test - Mock RTI, sub-app simulator, and integration tests.
 */

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":cuis-proto"))
    testImplementation(project(":cuis-server"))
    testImplementation(project(":api:HLA1516-2025"))
    testImplementation(libs.junit)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.grpc.netty.shaded)
    testImplementation(libs.grpc.protobuf)
    testImplementation(libs.grpc.stub)
    testImplementation(libs.grpc.testing)
    testImplementation(libs.javax.annotation.api)
}

sourceSets {
    test {
        java {
            setSrcDirs(listOf("src"))
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    testLogging {
        events("passed", "failed")
    }
}

tasks.register<JavaExec>("runDemo") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "se.pitch.oss.fedpro.cuis.test.demo.CuisDemo"
    standardInput = System.`in`
}

// Bare CUIS server on a fixed port, with no scripted scenario, for driving
// from grpcui / grpcurl as if grpcui were a Web Sub-App. Override the port
// with -Pport=NNNNN (default 50051).
tasks.register<JavaExec>("runGrpcuiServer") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "se.pitch.oss.fedpro.cuis.test.demo.CuisGrpcuiServer"
    (project.findProperty("port") as String?)?.let { args(it) }
}
