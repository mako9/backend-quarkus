package testUtils

import io.quarkus.test.junit.QuarkusTestProfile

/**
 * For simple unit testing without authentication we can deactivate the start of Keycloak dev server
 */
class UnitTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): MutableMap<String, String> {
        return mutableMapOf(
            "quarkus.oidc.auth-server-url" to "http://localhost:8180/realms/quarkus",
            "quarkus.keycloak.policy-enforcer.enable" to "false",
            "quarkus.keycloak.devservices.enabled" to "false"
        )
    }
}