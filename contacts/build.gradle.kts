import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.library)
}

android {
    namespace = "org.fossify.contacts"
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        buildConfigField("String", "VERSION_NAME", "\"${project.property("VERSION_NAME")}\"")
        buildConfigField("int", "VERSION_CODE", project.property("VERSION_CODE").toString())
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
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
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.autofit.text.view)
    implementation(libs.ezvcard)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.libphonenumber)
}
