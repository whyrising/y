plugins {
    id("java")
    id("kotlin-multiplatform")
    id("java-library")
}

repositories {
    mavenCentral()
}

val ideaActive = System.getProperty("idea.active") == "true"

kotlin {
    targets {
        jvm {
            compilations.all {
                kotlinOptions {
                    jvmTarget = Libs.jvmTargetVersion
                }
            }
        }

        if (ideaActive)
            macosX64("nativeCommon")
        else {
            linuxX64()
            mingwX64()

            macosX64()
            tvos()
            watchos()
            iosX64()
            iosArm64()
            iosArm32()
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs +
                    "-Xopt-in=kotlin.RequiresOptIn"
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Libs.Kotest.assertions)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:1.4.20")
                implementation("org.jetbrains.kotlin:kotlin-test-junit5:1.4.20")
                implementation("org.junit.jupiter:junit-jupiter:5.7.0")
                implementation(Libs.Kotest.runner)
                implementation(Libs.Kotest.propertyTest)
            }
        }

        if (ideaActive) {
            val nativeCommonMain by getting {
                dependsOn(commonMain)
            }

            val nativeCommonTest by getting
        } else {
            val nativeCommonMain by creating {
                dependsOn(commonMain)
            }

            val nativeCommonTest by creating

            val linuxX64Main by sourceSets.getting
            val mingwX64Main by sourceSets.getting

            val tvosArm64Main by sourceSets.getting
            val tvosX64Main by sourceSets.getting

            val watchosArm32Main by sourceSets.getting
            val watchosArm64Main by sourceSets.getting
            val watchosX86Main by sourceSets.getting

            val macosX64Main by sourceSets.getting

            val iosArm32Main by sourceSets.getting
            val iosArm64Main by sourceSets.getting
            val iosX64Main by sourceSets.getting

            configure(
                listOf(
                    linuxX64Main,
                    mingwX64Main,
                    tvosArm64Main,
                    tvosX64Main,
                    watchosArm32Main,
                    watchosArm64Main,
                    watchosX86Main,
                    macosX64Main,
                    iosX64Main,
                    iosArm64Main,
                    iosArm32Main
                )
            ) { dependsOn(nativeCommonMain) }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

apply(from = "../publish-y.gradle.kts")
