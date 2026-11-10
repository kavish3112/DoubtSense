package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DoubtItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DoubtSense", appName)
  }

  @Test
  fun `test DoubtItem model creation`() {
    val item = DoubtItem(
      id = 1L,
      doubtSnippet = "Why does warm air rise?",
      topic = "Physics: Thermodynamics",
      quickAnswer = "Warm air is less dense than cooler air, causing it to float upwards.",
      detailedAnswer = "When air warms up, its molecules move faster and spread apart, lowering its density. The surrounding denser cool air pushes underneath, buoying the warm air upward.",
      notificationSummary = "Physics: Warm air is less dense and rises due to buoyant forces."
    )
    assertNotNull(item)
    assertEquals("Physics: Thermodynamics", item.topic)
    assertEquals(false, item.isAmbiguous)
  }
}
