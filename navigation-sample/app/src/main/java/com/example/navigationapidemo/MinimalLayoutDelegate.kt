/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.navigationapidemo

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.libraries.navigation.layoutcustomization.NavigationLayoutDelegate
import com.google.android.libraries.navigation.layoutcustomization.NavigationReadyUiState
import com.google.android.libraries.navigation.layoutcustomization.NavigationUiParent

/**
 * Minimal [NavigationLayoutDelegate] implementation that arranges only the viewport
 * and the Google logo in a FrameLayout.
 */
class MinimalLayoutDelegate : NavigationLayoutDelegate() {

  private var layout: FrameLayout? = null

  override fun onEnterNavigationReady(
    navigationUiParent: NavigationUiParent,
    newState: NavigationReadyUiState,
  ) {
    val currentLayout = layout ?: FrameLayout(navigationUiParent.viewContext).also {
      layout = it
    }

    detachFromParent(newState.viewport)
    detachFromParent(newState.googleLogo)

    val viewportParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
    currentLayout.addView(newState.viewport, viewportParams)

    val logoParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM or Gravity.START,
    )
    currentLayout.addView(newState.googleLogo, logoParams)

    navigationUiParent.removeNavigationLayout(currentLayout)
    navigationUiParent.setNavigationLayout(currentLayout)
  }

  override fun onLeaveNavigationReady(
    navigationUiParent: NavigationUiParent,
    oldState: NavigationReadyUiState,
  ) {
    layout?.let { currentLayout ->
      currentLayout.removeAllViews()
      navigationUiParent.removeNavigationLayout(currentLayout)
    }
  }

  private fun detachFromParent(view: View?) {
    (view?.parent as? ViewGroup)?.removeView(view)
  }
}
