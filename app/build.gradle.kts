@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

setupApp()

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:rawtypes")
    }
    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }
    namespace = "id.bits.box"
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libgojni.so"
        }
    }
    androidResources {
        generateLocaleConfig = false
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}


dependencies {

    implementation(fileTree("libs"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.work:work-multiprocess:2.11.2")

    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("com.github.jenly1314:zxing-lite:3.5.0")
    implementation("com.blacksquircle.ui:editorkit:2.9.0")
    implementation("com.blacksquircle.ui:language-base:2.9.0")
    implementation("com.blacksquircle.ui:language-json:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.6.2")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    ksp("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
