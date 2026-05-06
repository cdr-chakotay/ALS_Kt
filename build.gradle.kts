import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("com.google.protobuf") version "0.9.6"
    id("com.vanniktech.maven.publish") version "0.36.0"

}

// Fix to version 11.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

// The protobuf plugin expects .proto files under src/main/proto by default.
// We keep them alongside the Kotlin sources instead, so we redirect it here.
sourceSets{
    main {
        proto {
            srcDir("src/main/kotlin/protobuf")
        }
    }
}

// Protobuf code generation:
//   1. protoc downloads and runs the protobuf compiler for the version pinned below.
//   2. generateProtoTasks registers the "kotlin" builtin, which makes protoc emit
//      Kotlin-idiomatic DSL wrappers on top of the standard Java classes.
//      The generated sources land in build/generated/source/proto/main/kotlin/
//      and are automatically added to the main source set.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.34.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("kotlin")
            }
        }
    }
}

dependencies{
    implementation("com.google.protobuf:protobuf-kotlin:4.34.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    archiveVersion.set("")
}

tasks.register<Jar>("cliJar") {
    group = "distribution"
    description = "Packages the ALS CLI together with all runtime dependencies."
    archiveBaseName.set("als_kt-cli")
    archiveVersion.set("")  // no version suffix
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "org.kufl.als_kt.ALSCli"
    }

    from(sourceSets.main.get().output)
    from(
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    )
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "als_kt", version.toString())

    pom {
        name = "ALS_Kt"
        description = "A library for querying Apple Location Services (ALS) for cell tower locations."
        inceptionYear = "2026"
        url = "https://github.com/cdr-chakotay/ALS_Kt"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/mit"
                distribution = "https://opensource.org/licenses/mit"
            }
        }
        developers {
            developer {
                id = "cdr-chakotay"
                name = "Florian Kuenzig"
                url = "https://github.com/cdr-chakotay"
                email = "60937022+cdr-chakotay@users.noreply.github.com"
                organization = "kufl.org"
                organizationUrl = "kufl.org"
            }
        }
        scm {
            url = "https://github.com/cdr-chakotay/ALS_Kt"
            connection = "scm:git:git://github.com/cdr-chakotay/ALS_Kt.git"
            developerConnection = "scm:git:ssh://git@github.com/cdr-chakotay/ALS_Kt.git"
        }
    }
}


