import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
    id("io.izzel.taboolib") version "2.0.22"
    kotlin("jvm") version "1.8.22"
}

taboolib {
    subproject = true
    env {
        // 安装模块
        install(Basic, Bukkit, BukkitHook, BukkitNMSUtil)
    }
    version {
        taboolib = "6.2.3"
        //coroutines = "1.10.1"
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    mavenLocal()
}

dependencies {
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    /** 发光工具 **/
    compileOnly("net.kyori:adventure-api:4.19.0")
    compileOnly("com.github.retrooper:packetevents-spigot:2.7.0")
    /** adyeshach  **/
    compileOnly("ink.ptms:adyeshach:2.0.4")
    /** MythicMobs **/
    compileOnly("ink.ptms:um:1.1.5")
    /** NMS 工具 **/
    compileOnly("ink.ptms:nms-all:1.0.0")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    repositories {
        maven("http://sacredcraft.cn:8081/repository/releases") {
            isAllowInsecureProtocol = true
            credentials {
                username = project.findProperty("taboolibUsername").toString()
                password = project.findProperty("taboolibPassword").toString()
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
        mavenLocal()
    }
    publications {
        create<MavenPublication>("maven") {
            from(components.findByName("java"))
//            artifactId = "Arim"
//            groupId = "top.maplex.arim"
            version = project.version.toString()
            println("Publishing version $version")
            println("Publishing artifactId $artifactId")
            println("Publishing groupId $groupId")

        }
    }
}
