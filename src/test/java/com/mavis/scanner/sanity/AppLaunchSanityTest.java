package com.mavis.scanner.sanity;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.pages.LoginPage;
import com.mavis.scanner.pages.StartHomePage;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Sanity tests - verify the app launches and basic UI elements render.
 */
public class AppLaunchSanityTest extends BaseTest {

    @Test(priority = 0, description = "App launches and StartHome screen renders correctly")
    public void testAppLaunchesSuccessfully() {
        setup("Sanity - App Launch");

        try {
            StartHomePage startHome = new StartHomePage(driver, wait);

            // Verify StartHome screen loads
            Assert.assertTrue(startHome.isDisplayed(), "StartHome screen should be displayed");
            logStep("StartHome screen displayed");

            // Verify title is present
            String title = startHome.getTitle();
            Assert.assertNotNull(title, "Title should be present");
            Assert.assertFalse(title.isEmpty(), "Title should not be empty");
            logStep("Title: " + title);

            // Verify app logo
            Assert.assertTrue(startHome.isLogoDisplayed(), "App logo should be displayed");
            logStep("App logo displayed");

            // Verify version text
            String version = startHome.getAppVersion();
            Assert.assertNotNull(version, "Version text should be present");
            logStep("App version: " + version);

            // Verify Start Inventory button
            Assert.assertTrue(startHome.isInventoryButtonDisplayed(),
                    "Start Inventory button should be displayed");
            logStep("Start Inventory button displayed: " + startHome.getInventoryButtonText());

            pass();

        } catch (Exception e) {
            fail("App launch sanity failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 1, description = "Navigation from StartHome to Login screen")
    public void testNavigateToLogin() {
        setup("Sanity - Navigate to Login");

        try {
            StartHomePage startHome = new StartHomePage(driver, wait);
            Assert.assertTrue(startHome.isDisplayed(), "StartHome should be displayed");

            // Tap Start Inventory
            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(1000);
            logStep("Tapped Start Inventory");

            // Verify Login screen loads
            Assert.assertTrue(loginPage.isDisplayed(), "Login screen should be displayed");
            logStep("Login screen displayed");

            // Verify all input fields present
            Assert.assertTrue(loginPage.isStoreFieldDisplayed(), "Store field should be present");
            Assert.assertTrue(loginPage.isEmployeeFieldDisplayed(), "Employee field should be present");
            Assert.assertTrue(loginPage.isInvCodeFieldDisplayed(), "Inventory Code field should be present");
            Assert.assertTrue(loginPage.isLoginButtonDisplayed(), "Login button should be present");
            logStep("All login fields verified");

            pass();

        } catch (Exception e) {
            fail("Navigate to login failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 2, description = "Login Home button returns to StartHome")
    public void testLoginHomeButton() {
        setup("Sanity - Login Home Button");

        try {
            StartHomePage startHome = new StartHomePage(driver, wait);
            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(1000);
            logStep("Navigated to Login screen");

            // Tap Home button
            StartHomePage returnedHome = loginPage.tapHome();
            Thread.sleep(1000);
            logStep("Tapped Home button");

            // Verify back on StartHome
            Assert.assertTrue(returnedHome.isDisplayed(), "Should return to StartHome screen");
            logStep("Returned to StartHome successfully");

            pass();

        } catch (Exception e) {
            fail("Login home button test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
