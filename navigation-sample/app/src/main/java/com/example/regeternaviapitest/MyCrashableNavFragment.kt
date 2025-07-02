// app/src/main/java/com/example/regeternaviapitest/MyCrashableNavFragment.kt
package com.example.regeternaviapitest

import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.libraries.navigation.SupportNavigationFragment

class MyCrashableNavFragment : SupportNavigationFragment() {

    /**
     * This onCreate() is called by the Android Framework as part of the
     * fragment's own lifecycle initialization. It runs BEFORE onCreateView().
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("MyCrashableNavFragment", "onCreate() called. View does not exist yet.")
        super.onCreate(savedInstanceState)

        // The view is null, and setTrafficPromptsEnabled() will throw the NPE.
        Log.i("MyCrashableNavFragment", "Calling setTrafficPromptsEnabled(false)... this will crash.")
        setTrafficPromptsEnabled(false)
    }

    /**
     * The fix for for the crash is to call setTrafficPromptsEnabled() the call here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("MyNavFragment", "Disable RTD via setTrafficPromptsEnabled, setTrafficIncidentCardsEnabled and setReportIncidentButtonEnabled from onViewCreated.")
        setTrafficPromptsEnabled(false)
        setTrafficIncidentCardsEnabled(false)
        setReportIncidentButtonEnabled(false)
    }
}