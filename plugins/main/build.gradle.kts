// ==============================================================================
// CONFIGURAÇÃO COMPLETA DE plugins/main/build.gradle.kts PARA dev-2 + GlassUI
// ==============================================================================
// Mantém todas as dependências originais + adiciona Compose, fragment-ktx e ViewModel.
// Compatível com o Kotlin realmente usado pelo build (1.9.22): Compose Compiler 1.5.10

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.main"

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // === DEPENDÊNCIAS ORIGINAIS DO dev-2 (INTOCADAS) ===
    implementation(project(":shared:impl"))
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))

    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:insulin"))
    testImplementation(project(":shared:tests"))

    api(Libs.AndroidX.appCompat)
    api(Libs.Google.Android.material)

    // Actions
    api(Libs.AndroidX.gridLayout)

    // SmsCommunicator
    api(Libs.javaOtp)
    api(Libs.qrGen)

    // Overview
    api(Libs.Google.Android.flexbox)

    // Food
    api(Libs.AndroidX.Work.runtimeKtx)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)

    // === NOVAS DEPENDÊNCIAS (GlassUI + Compose) ===

    // Fragment KTX (commit {}, viewModels())
    implementation(Libs.AndroidX.fragment)

    // Lifecycle ViewModel KTX (ViewModel base class)
    implementation(Libs.AndroidX.lifecycleViewmodel)

    // Compose BOM (gerencia versões de todas as libs Compose)
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
}
