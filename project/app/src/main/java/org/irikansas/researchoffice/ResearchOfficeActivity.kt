package org.irikansas.researchoffice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkFeature
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.LayoutXMLPanelRegistration
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature

class ResearchOfficeActivity : AppSystemActivity() {
  private lateinit var addressField: EditText
  private lateinit var statusText: TextView
  private lateinit var browserModeText: TextView
  private var ambiencePlayer: MediaPlayer? = null
  private var ambienceEnabled = true

  private val chromePackages =
    listOf(
      "com.android.chrome",
      "com.chrome.beta",
      "com.chrome.dev",
      "com.chrome.canary",
    )

  override fun registerFeatures(): List<SpatialFeature> =
    listOf(
      VRFeature(this),
      IsdkFeature(this, spatial, systemManager),
    )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startAmbience()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = Vector3(0.18f, 0.18f, 0.18f),
      sunColor = Vector3(2.4f, 2.45f, 2.5f),
      sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
      environmentIntensity = 0.24f,
    )
    scene.setViewOrigin(0.0f, 0.0f, 0.0f, 180.0f)

    Entity.create(
      listOf(
        Mesh("mesh://skybox".toUri(), hittable = MeshCollision.NoCollision),
        Material().apply {
          baseTextureAndroidResourceId = R.drawable.iri_office_360
          unlit = true
        },
        Transform(Pose(Vector3(0f, 0f, 0f))),
      )
    )

    Entity.createPanelEntity(
      R.id.research_browser_panel,
      Transform(Pose(Vector3(0f, 1.15f, -1.85f), Quaternion(0f, 180f, 0f))),
    )
  }

  @OptIn(SpatialSDKExperimentalAPI::class)
  override fun registerPanels(): List<PanelRegistration> =
    listOf(
      LayoutXMLPanelRegistration(
        R.id.research_browser_panel,
        layoutIdCreator = { _ -> R.layout.research_browser_panel },
        settingsCreator = { _ ->
          UIPanelSettings(
            shape = QuadShapeOptions(width = 2.9f, height = 1.72f),
            style = PanelStyleOptions(themeResourceId = R.style.PanelAppTheme),
            display = DpPerMeterDisplayOptions(),
          )
        },
        panelSetupWithRootView = { rootView, _, _ -> setupWorkPanel(rootView) },
      )
    )

  private fun setupWorkPanel(root: View) {
    addressField = root.findViewById(R.id.address_field)
    statusText = root.findViewById(R.id.status_text)
    browserModeText = root.findViewById(R.id.browser_mode_text)

    root.findViewById<Button>(R.id.go_button).setOnClickListener { openAddressFieldInChrome() }
    root.findViewById<Button>(R.id.chrome_button).setOnClickListener {
      openInChrome("https://www.google.com/")
    }
    root.findViewById<Button>(R.id.iri_button).setOnClickListener {
      openInChrome("https://iri.gen.ks.us/")
    }
    root.findViewById<Button>(R.id.ringgold_button).setOnClickListener {
      openInChrome("https://www.ringgold.com/")
    }
    root.findViewById<Button>(R.id.scholar_button).setOnClickListener {
      openInChrome("https://scholar.google.com/")
    }
    root.findViewById<Button>(R.id.jstor_button).setOnClickListener {
      openInChrome("https://www.jstor.org/")
    }
    root.findViewById<Button>(R.id.cambridge_button).setOnClickListener {
      openInChrome("https://www.cambridge.org/core/")
    }
    root.findViewById<Button>(R.id.orcid_button).setOnClickListener {
      openInChrome("https://orcid.org/")
    }
    root.findViewById<Button>(R.id.microsoft_button).setOnClickListener {
      openInChrome("https://login.microsoftonline.com/")
    }
    root.findViewById<Button>(R.id.ambience_button).setOnClickListener { button ->
      toggleAmbience(button as Button)
    }

    addressField.setOnEditorActionListener { _, actionId, event ->
      val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
      if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
        openAddressFieldInChrome()
        true
      } else {
        false
      }
    }

    refreshChromeStatus()
  }

  private fun openAddressFieldInChrome() {
    val raw = addressField.text.toString().trim()
    if (raw.isBlank()) {
      updateStatus("Enter a work URL or search term")
      return
    }
    openInChrome(normalizeAddress(raw))
  }

  private fun normalizeAddress(raw: String): String =
    when {
      raw.startsWith("https://", ignoreCase = true) -> raw
      raw.startsWith("http://", ignoreCase = true) -> raw
      raw.contains('.') && !raw.contains(' ') -> "https://$raw"
      else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
    }

  private fun openInChrome(url: String) {
    val chromePackage = installedChromePackage()
    if (chromePackage == null) {
      browserModeText.text = getString(R.string.chrome_missing)
      updateStatus("Chrome is not installed on this headset")
      return
    }

    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) {
      updateStatus("Invalid web address")
      return
    }

    try {
      val customTab =
        CustomTabsIntent.Builder()
          .setShowTitle(true)
          .setUrlBarHidingEnabled(false)
          .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
          .build()
      customTab.intent.setPackage(chromePackage)
      customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      customTab.launchUrl(this, uri)
      updateStatus("Opened in real Chrome — credentials stay in Chrome")
    } catch (_: ActivityNotFoundException) {
      val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(chromePackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      runCatching { startActivity(fallback) }
        .onSuccess { updateStatus("Opened in real Chrome") }
        .onFailure { updateStatus("Chrome could not open this address") }
    }
  }

  private fun installedChromePackage(): String? =
    chromePackages.firstOrNull { packageName ->
      runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      }.isSuccess
    }

  private fun refreshChromeStatus() {
    val packageName = installedChromePackage()
    if (packageName == null) {
      browserModeText.text = getString(R.string.chrome_missing)
      updateStatus("Deploy Chrome as a managed app before browsing")
    } else {
      browserModeText.text = getString(R.string.chrome_ready, packageName)
      updateStatus("Office ready — no site allowlist and no WebView")
    }
  }

  private fun updateStatus(message: String) {
    if (::statusText.isInitialized) {
      statusText.text = "IRI Research Office • $message"
    }
  }

  private fun startAmbience() {
    if (ambiencePlayer == null) {
      ambiencePlayer = MediaPlayer.create(this, R.raw.office_rain)?.apply {
        isLooping = true
        setVolume(0.30f, 0.30f)
      }
    }
    ambiencePlayer?.start()
    ambienceEnabled = true
  }

  private fun toggleAmbience(button: Button) {
    ambienceEnabled = !ambienceEnabled
    if (ambienceEnabled) {
      ambiencePlayer?.start()
      button.setText(R.string.ambience_on)
      updateStatus("Real office ambience on")
    } else {
      ambiencePlayer?.pause()
      button.setText(R.string.ambience_off)
      updateStatus("Office ambience off")
    }
  }

  override fun onResume() {
    super.onResume()
    if (ambienceEnabled) ambiencePlayer?.start()
    if (::browserModeText.isInitialized) refreshChromeStatus()
  }

  override fun onPause() {
    super.onPause()
    ambiencePlayer?.pause()
  }

  override fun onDestroy() {
    ambiencePlayer?.release()
    ambiencePlayer = null
    super.onDestroy()
  }
}
