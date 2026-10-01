package app.hubhelper

import android.app.Application
import com.google.mlkit.common.internal.CommonComponentRegistrar
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.internal.VisionCommonRegistrar
import com.google.mlkit.vision.text.internal.TextRegistrar

class HubHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MlKitContext.initializeIfNeeded(
            this,
            listOf(
                CommonComponentRegistrar(),
                VisionCommonRegistrar(),
                TextRegistrar(),
            ),
        )
    }
}
