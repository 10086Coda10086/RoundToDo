plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.round.todo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.round.todo"
        minSdk = 30 // Wear OS 3.0+ (Android 11)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ★★★ 新增部分：配置签名信息 ★★★
    signingConfigs {
        create("release") {
            // 注意：这里填你之前生成 jks 文件的路径
            // 如果是在项目根目录，直接写文件名；如果在其他地方，要写全路径
            // 为了方便，建议你把 roundtodo.jks 复制到 app 文件夹下
            storeFile = file("../roundtodo.jks")
            storePassword = "123456" // 比如 123456
            keyAlias = "key0"
            keyPassword = "123456" // 比如 123456
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 开启混淆，减小体积
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release") // 使用正式签名
        }
        debug {
            // ★★★ 关键点：让调试版也使用正式签名 ★★★
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // 安卓基础库
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // 微软登录库 (MSAL)
    implementation("com.microsoft.identity.client:msal:5.1.0")

    // 网络请求库 (Retrofit) - 用来从微软下载数据
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")


    implementation("androidx.navigation:navigation-compose:2.7.6")

    // --- Wear OS 核心库 ---
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.wear.compose:compose-navigation:1.3.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")

    // --- 微软登录 & 网络 (保持不变) ---
    implementation("com.microsoft.identity.client:msal:5.1.0") {
        exclude(group = "com.microsoft.device.display")
    }
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 图标
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    implementation("androidx.compose.material:material:1.6.0")

    implementation("androidx.compose.foundation:foundation:1.6.0")
}