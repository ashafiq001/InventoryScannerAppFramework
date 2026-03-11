package com.mavis.scanner.utils;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Wait and element interaction utilities for mobile automation.
 */
public class WaitHelper {

    /**
     * Wait for an element to be present in the DOM.
     */
    public static WebElement waitForElement(WebDriverWait wait, By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * Wait for an element to be visible on screen.
     */
    public static WebElement waitForVisible(WebDriverWait wait, By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Wait for an element to be clickable and click it.
     */
    public static void waitAndClick(WebDriverWait wait, By locator) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        element.click();
    }

    /**
     * Wait for an element and enter text (clears existing text first).
     */
    public static void waitAndType(WebDriverWait wait, By locator, String text) {
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        element.clear();
        element.sendKeys(text);
    }

    /**
     * Wait for element text to contain expected value.
     */
    public static boolean waitForTextContains(WebDriverWait wait, By locator, String expectedText) {
        try {
            return wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, expectedText));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wait for any of the given elements to be present.
     */
    public static WebElement waitForAny(AndroidDriver driver, int timeoutSeconds, By... locators) {
        FluentWait<AndroidDriver> fluentWait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(timeoutSeconds))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        return fluentWait.until(d -> {
            for (By locator : locators) {
                List<WebElement> elements = d.findElements(locator);
                if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                    return elements.get(0);
                }
            }
            return null;
        });
    }

    /**
     * Check if an element is present (non-blocking).
     */
    public static boolean isElementPresent(AndroidDriver driver, By locator) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            return !elements.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get text from an element, returning empty string if not found.
     */
    public static String getTextSafe(AndroidDriver driver, By locator) {
        try {
            WebElement element = driver.findElement(locator);
            return element.getText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Wait for a toast message or transient text to appear.
     */
    public static String waitForToast(AndroidDriver driver, int timeoutSeconds) {
        try {
            FluentWait<AndroidDriver> fluentWait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(timeoutSeconds))
                    .pollingEvery(Duration.ofMillis(200));

            return fluentWait.until(d -> {
                WebElement toast = d.findElement(By.xpath("//android.widget.Toast[1]"));
                String text = toast.getText();
                return (text != null && !text.isEmpty()) ? text : null;
            });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wait for a specific activity to be in the foreground.
     */
    public static boolean waitForActivity(AndroidDriver driver, String activityName, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            String current = driver.currentActivity();
            if (current != null && current.contains(activityName)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
