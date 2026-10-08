plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

group = "id.beruang"
version = "0.1.0-SNAPSHOT"

description = "BearBot Body - NMS physical body prototype for Paper 1.21.8"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven {
        url = uri("${rootProject.projectDir}/.gradle/local-maven")
    }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}
