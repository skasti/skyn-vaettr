import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm") version "2.1.21"
}

group = "no.skasti.skynvaettr"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val reporting by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

configurations[reporting.implementationConfigurationName].extendsFrom(configurations["implementation"])
configurations[reporting.runtimeOnlyConfigurationName].extendsFrom(configurations["runtimeOnly"])

sourceSets.test {
    compileClasspath += reporting.output
    runtimeClasspath += reporting.output
}

dependencies {
    testImplementation(kotlin("test"))
    add(reporting.implementationConfigurationName, "org.knowm.xchart:xchart:3.8.8")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

val renderThermalExpectationReport by tasks.registering(JavaExec::class) {
    group = "reporting"
    description = "Render the thermal expectation example report."
    classpath = reporting.runtimeClasspath
    mainClass.set("no.skasti.skynvaettr.reporting.ThermalExpectationReport")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.dir("reports/examples/thermal-expectation").get().asFile.absolutePath)
}

val renderKitchenLightScenarioReport by tasks.registering(JavaExec::class) {
    group = "reporting"
    description = "Render the model-free kitchen dimmer/light scenario report."
    classpath = reporting.runtimeClasspath
    mainClass.set("no.skasti.skynvaettr.reporting.KitchenLightScenarioReport")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.dir("reports/examples/kitchen-light").get().asFile.absolutePath)
}

tasks.register("renderExampleReports") {
    group = "reporting"
    description = "Render all example reports and graphs under build/reports/examples."
    dependsOn(renderThermalExpectationReport, renderKitchenLightScenarioReport)
}
