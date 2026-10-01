package app.hubhelper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OcrInitializationTest {
    @Test
    fun bundledTextRecognizerIsRegisteredAtStartup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.applicationContext)

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).close()
    }
}
