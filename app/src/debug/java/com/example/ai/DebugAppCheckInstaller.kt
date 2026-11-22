package com.example.ai

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug-only installer. This class is absent from release source sets. */
internal object DebugAppCheckInstaller {
  fun install() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
      DebugAppCheckProviderFactory.getInstance(),
    )
    Log.d("AppCheckInit", "DebugAppCheckProviderFactory installed.")
  }
}
