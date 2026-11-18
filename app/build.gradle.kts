import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// El plugin de Google Services lee app/google-services.json (proyecto de Firebase).
// Se aplica solo si el archivo existe para que el proyecto abra aunque falte la configuracion.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Llaves que no se suben al repositorio: se leen de local.properties.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun localProp(name: String) = localProps.getProperty(name, "")

android {
    namespace = "co.edu.ucompensar.agrosabana"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.edu.ucompensar.agrosabana"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        // ePayco: llave publica y modo pruebas (sandbox). La llave privada nunca va en la app.
        buildConfigField("String", "EPAYCO_PUBLIC_KEY", "\"${localProp("EPAYCO_PUBLIC_KEY")}\"")
        buildConfigField("boolean", "EPAYCO_TEST", localProp("EPAYCO_TEST").ifEmpty { "true" })
        // Google Maps SDK: la llave se inyecta en el manifiesto.
        manifestPlaceholders["mapsApiKey"] = localProp("MAPS_API_KEY")
        // Firebase Storage requiere plan Blaze; en la beta las fotos van comprimidas en Firestore.
        buildConfigField("boolean", "USE_FIREBASE_STORAGE", localProp("USE_FIREBASE_STORAGE").ifEmpty { "false" })
        buildConfigField("boolean", "MAPS_ENABLED", localProp("MAPS_API_KEY").isNotBlank().toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.splashscreen)

    // Backend: Firebase Authentication, Cloud Firestore y Cloud Storage.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.kotlinx.coroutines.play.services)

    // Geolocalizacion y mapas de Google Play Services.
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    // Carga de imagenes remotas (Firebase Storage) con cache.
    implementation(libs.coil)
}
