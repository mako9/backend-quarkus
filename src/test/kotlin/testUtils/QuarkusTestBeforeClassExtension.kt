package testUtils

import io.quarkus.test.junit.callback.QuarkusTestBeforeClassCallback
import jakarta.enterprise.inject.spi.CDI
import org.junit.platform.commons.support.ModifierSupport


/**
 * The QuarkusTestBeforeClassExtension is a Quarkus extension used for providing actions on the QuarkusTestBeforeClassCallback
 * in JUnit lifecycle. In our case we use the callback to empty the database before running each
 * test class. For inner classes the data deletion is skipped.
 * @see EntityUtil.deleteAllData
 */
class QuarkusTestBeforeClassExtension : QuarkusTestBeforeClassCallback {

    override fun beforeClass(testClass: Class<*>?) {
        if (isTestClassInnerClass(testClass)) {
            print("Skip deletion of data in BeforeAllCallback for inner class.")
            return
        }
        val entityUtil = CDI.current().select(EntityUtil::class.java).get()
        entityUtil.deleteAllData()
    }

    private fun isTestClassInnerClass(testClass: Class<*>?): Boolean {
        return !ModifierSupport.isStatic(testClass) && testClass?.isMemberClass == true
    }
}
