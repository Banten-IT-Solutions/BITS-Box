import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Base64
import java.util.Properties

private val Project.android get() = extensions.getByName<ApplicationExtension>("android")
private val Project.androidComponents get() = extensions.getByName<ApplicationAndroidComponentsExtension>("androidComponents")

private lateinit var metadata: Properties
private lateinit var localProperties: Properties

fun Project.requireMetadata(): Properties {
    if (!::metadata.isInitialized) {
        metadata = Properties().apply {
            load(rootProject.file("bitsbox.properties").inputStream())
        }
    }
    return metadata
}

fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()
        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {
            localProperties.load(Base64.getDecoder().decode(base64).inputStream())
        } else if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(rootProject.file("local.properties").inputStream())
        }
    }
    return localProperties
}

fun Project.setupCommon() {
    android.apply {
        buildToolsVersion = "37.0.0"
        ndkVersion = "29.0.14206865"
        compileSdk = 37
        defaultConfig {
            minSdk = 29
            targetSdk = 37
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
            }
            getByName("debug") {
                isMinifyEnabled = false
                applicationIdSuffix = ".debug"
                isDebuggable = true
                isJniDebuggable = true
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        lint {
            showAll = true
            checkAllWarnings = true
            checkReleaseBuilds = true
            warningsAsErrors = true
        }
        packaging {
            resources.excludes.addAll(
                listOf(
                    "**/*.kotlin_*",
                    "/META-INF/*.version",
                    "/META-INF/native/**",
                    "/META-INF/native-image/**",
                    "/META-INF/INDEX.LIST",
                    "DebugProbesKt.bin",
                    "com/**",
                    "org/**",
                    "**/*.java",
                    "**/*.proto",
                    "okhttp3/**"
                )
            )
            jniLibs {
                keepDebugSymbols.add("**/libgojni.so")
            }
        }
    }
    extensions.getByName<KotlinAndroidProjectExtension>("kotlin").compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

fun Project.setupAppCommon() {
    setupCommon()

    val lp = requireLocalProperties()
    val keystorePwd = lp.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = lp.getProperty("ALIAS_NAME") ?: System.getenv("ALIAS_NAME")
    val pwd = lp.getProperty("ALIAS_PASS") ?: System.getenv("ALIAS_PASS")

    android.apply {
        if (keystorePwd != null) {
            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("release.keystore")
                    storePassword = keystorePwd
                    keyAlias = alias
                    keyPassword = pwd
                }
            }
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                getByName("release").signingConfig = key
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupApp() {
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME")
    val verName = requireMetadata().getProperty("VERSION_NAME")
    val verCode = (requireMetadata().getProperty("VERSION_CODE").toInt()) * 5
    android.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
            buildConfigField("String", "PRE_VERSION_NAME", "\"\"")
        }
    }
    setupAppCommon()

    android.apply {
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro")
                )
            }
        }

        // ABI splits for APKs only — must be disabled for AAB (bundle) per https://issuetracker.google.com/402800800
        val isBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
        splits.abi {
            reset()
            isEnable = !isBundle
            isUniversalApk = false
            include("armeabi-v7a")
            include("arm64-v8a")
        }

        flavorDimensions.add("vendor")
        productFlavors {
            create("oss")
            create("play")
            create("preview") {
                buildConfigField(
                    "String",
                    "PRE_VERSION_NAME",
                    "\"${requireMetadata().getProperty("PRE_VERSION_NAME")}\""
                )
            }
        }

        sourceSets.getByName("main").apply {
            jniLibs.srcDir("executableSo")
        }
    }

    // Output file name via new androidComponents API (replaces BaseVariantOutputImpl)
    androidComponents.onVariants { variant ->
        val variantName = variant.name
        val isPreview = variantName.contains("preview", ignoreCase = true)
        variant.outputs.forEach { output ->
            val original = output.outputFileName.get() ?: return@forEach
            val version = if (isPreview) {
                requireMetadata().getProperty("PRE_VERSION_NAME")
            } else {
                verName
            }
            val prefix = "BITSBox-$version"
            var newName = original.replace("app", prefix)
            newName = newName.replace("-release", "").replace("-oss", "").replace("-preview", "")
            output.outputFileName.set(newName)
        }
    }
}
