plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.meta.spatial.plugin)
}

android {
  namespace = "org.irikansas.researchoffice"
  compileSdk = 34

  defaultConfig {
    applicationId = "org.irikansas.researchoffice"
    minSdk = 34
    targetSdk = 34
    versionCode = 3
    versionName = "2.0.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }

  packaging {
    resources.excludes.add("META-INF/LICENSE")
    resources.excludes.add("META-INF/NOTICE")
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.browser:browser:1.8.0")
  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.vr)
  implementation(libs.meta.spatial.sdk.isdk)
}
