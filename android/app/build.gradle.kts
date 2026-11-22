import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from CI secrets (see .github/workflows/android.yml). Without them the
// release build falls back to the debug key, so local builds still work.
val ksFile = System.getenv("ATLAS_KEYSTORE")?.let { file(it) }
val ksPass = System.getenv("ATLAS_KEYSTORE_PASSWORD")

android {
  namespace = "com.accountvault.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.accountvault.app"
    minSdk = 26
    targetSdk = 36
    versionCode = (System.getenv("ATLAS_BUILD") ?: "1").toInt()
    versionName = System.getenv("ATLAS_VERSION") ?: "dev"
  }

  signingConfigs {
    if (ksFile != null && ksFile.exists() && ksPass != null) {
      create("release") {
        storeFile = ksFile
        storePassword = ksPass
        keyAlias = "atlas"
        keyPassword = ksPass
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { jvmToolchain(17) }

dependencies {
  val bom = platform("androidx.compose:compose-bom:2025.06.00")
  implementation(bom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.biometric:biometric:1.2.0-alpha05")
  implementation("androidx.core:core-ktx:1.16.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------------------------
// Preset app icons: reuse the repo's /icons folder (shared with web + iOS) instead of a copy.
abstract class CopyIcons : DefaultTask() {
  @get:InputDirectory abstract val source: DirectoryProperty
  @get:OutputDirectory abstract val outputDir: DirectoryProperty
  @TaskAction fun run() {
    val out = outputDir.get().asFile.resolve("icons")
    out.deleteRecursively(); out.mkdirs()
    source.get().asFile.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.copyTo(out.resolve(it.name), true) }
  }
}

// Launcher icon: ONE source image, android/icon-src/icon-1024.png (square, full-bleed artwork).
// Swap that file to change the logo; mipmaps are generated from it at build time.
abstract class GenLauncherIcons : DefaultTask() {
  @get:InputFile abstract val source: RegularFileProperty
  @get:OutputDirectory abstract val outputDir: DirectoryProperty
  @TaskAction fun run() {
    val src = ImageIO.read(source.get().asFile)
    val root = outputDir.get().asFile
    root.deleteRecursively()
    fun scaled(img: BufferedImage, n: Int): BufferedImage {
      // step down for quality
      var cur = img
      while (cur.width / 2 >= n) {
        val h = BufferedImage(cur.width / 2, cur.height / 2, BufferedImage.TYPE_INT_ARGB)
        val g = h.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(cur, 0, 0, h.width, h.height, null); g.dispose(); cur = h
      }
      val o = BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB)
      val g = o.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.drawImage(cur, 0, 0, n, n, null); g.dispose()
      return o
    }
    val dens = mapOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)
    for ((d, k) in dens) {
      val dir = root.resolve("mipmap-$d"); dir.mkdirs()
      // adaptive background layer: 108dp, artwork drawn at 86dp in the middle and its edges
      // stretched outwards, so the launcher mask (~72dp) shows the whole glyph
      val full = (108 * k).toInt(); val inner = (86 * k).toInt(); val off = (full - inner) / 2
      val art = scaled(src, inner)
      val bg = BufferedImage(full, full, BufferedImage.TYPE_INT_ARGB)
      for (y in 0 until full) for (x in 0 until full) {
        val sx = (x - off).coerceIn(0, inner - 1); val sy = (y - off).coerceIn(0, inner - 1)
        bg.setRGB(x, y, art.getRGB(sx, sy) or (0xff shl 24))
      }
      ImageIO.write(bg, "png", dir.resolve("ic_launcher_art.png"))
      // plain square for in-app use (toast) and legacy launchers
      ImageIO.write(scaled(src, (48 * k).toInt()), "png", dir.resolve("ic_launcher_flat.png"))
    }
    val any = root.resolve("mipmap-anydpi-v26"); any.mkdirs()
    val xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@mipmap/ic_launcher_art"/>
  <foreground android:drawable="@android:color/transparent"/>
</adaptive-icon>
"""
    any.resolve("ic_launcher.xml").writeText(xml)
    any.resolve("ic_launcher_round.xml").writeText(xml)
  }
}

val copyIcons = tasks.register<CopyIcons>("copyPresetIcons") {
  source.set(rootProject.layout.projectDirectory.dir("../icons"))
}
val genLauncher = tasks.register<GenLauncherIcons>("genLauncherIcons") {
  source.set(rootProject.layout.projectDirectory.file("icon-src/icon-1024.png"))
}

androidComponents {
  onVariants { v ->
    v.sources.assets?.addGeneratedSourceDirectory(copyIcons, CopyIcons::outputDir)
    v.sources.res?.addGeneratedSourceDirectory(genLauncher, GenLauncherIcons::outputDir)
  }
}
