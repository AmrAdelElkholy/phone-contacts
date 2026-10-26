import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.parcelize)
}

android {
    namespace = "org.fossify.commons"

    compileSdk = libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = libs.versions.app.build.minimumSDK.get().toInt()
        vectorDrawables.useSupportLibrary = true
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("consumer-rules.pro")
        }
    }


    buildFeatures {
        viewBinding = true
        compose = true
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
        compilerOptions.freeCompilerArgs.set(
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-Xcontext-receivers"
            )
        )
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        lintConfig = rootProject.file("lint.xml")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }
}



dependencies {
    implementation(commonsLibs.kotlinx.serialization.json)
    api(commonsLibs.kotlin.immutable.collections)
    implementation(commonsLibs.androidx.constraintlayout)
    implementation(commonsLibs.androidx.documentfile)
    implementation(commonsLibs.androidx.swiperefreshlayout)
    implementation(commonsLibs.androidx.exifinterface)
    implementation(commonsLibs.androidx.biometric.ktx)
    implementation(commonsLibs.androidx.lifecycle.process)
    implementation(commonsLibs.androidx.startup)
    implementation(commonsLibs.ez.vcard)

    implementation(commonsLibs.bundles.lifecycle)
    implementation(commonsLibs.bundles.compose)
    implementation(commonsLibs.compose.view.binding)
    debugImplementation(commonsLibs.bundles.compose.preview)

    api(commonsLibs.joda.time)
    api(commonsLibs.recyclerView.fastScroller)
    api(commonsLibs.reprint)
    api(commonsLibs.rtl.viewpager)
    api(commonsLibs.patternLockView)
    api(commonsLibs.androidx.core.ktx)
    api(commonsLibs.androidx.appcompat)
    api(commonsLibs.material)
    api(commonsLibs.gson)

    api(commonsLibs.glide)
    ksp(commonsLibs.glide.compiler)

    api(commonsLibs.bundles.room)
    ksp(commonsLibs.androidx.room.compiler)
}
