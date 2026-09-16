import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ZipCodeTest {

    private static final String SITE = "https://sharelane.com/cgi-bin/";
    private static final String PASSWORD = "1111";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private WebDriver driver;
    private WebDriverWait wait;
    private String accountEmail;

    @BeforeClass
    public void openBrowser() {
        driver = new ChromeDriver(new ChromeOptions().addArguments("--window-size=1400,900"));
        wait = new WebDriverWait(driver, TIMEOUT);
    }

    @Test
    public void customerCanOrderABook() {
        registerAccount();
        report("New account email: " + accountEmail);

        signInIfNeeded();

        String cardNumber = generateTestCard();
        report("Test card number: " + cardNumber);

        signInIfNeeded();
        addBookToCart();

        checkoutAndPay(cardNumber);
    }

    private void registerAccount() {
        driver.get(SITE + "register.py");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("zip_code"))).sendKeys("11201");
        clickWhenReady(By.xpath("//input[@value='Continue']"));

        typeInto(By.name("first_name"), "Ivan");
        typeInto(By.name("last_name"), "Petrov");
        typeInto(By.name("email"), "ivan.petrov" + new Random().nextInt(999999) + "@gmail.com");
        typeInto(By.name("password1"), PASSWORD);
        typeInto(By.name("password2"), PASSWORD);
        clickWhenReady(By.xpath("//input[@value='Register']"));

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "sharelane.com"));
        Matcher email = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.sharelane\\.com")
                .matcher(driver.findElement(By.tagName("body")).getText());
        assertTrue(email.find(), "Generated email address is not shown after registration");
        accountEmail = email.group();
        saveScreenshot("step1_new_account");
    }

    private void signInIfNeeded() {
        driver.get(SITE + "main.py");
        if (driver.findElements(By.name("email")).isEmpty()) {
            return;
        }
        typeInto(By.name("email"), accountEmail);
        typeInto(By.name("password"), PASSWORD);
        clickWhenReady(By.xpath("//input[@value='Login']"));
    }

    private String generateTestCard() {
        driver.get(SITE + "get_credit_card.py");
        clickWhenReady(By.xpath("//input[@value='Generate Credit Card']"));

        String cardNumber = wait.until(d -> {
            try {
                Matcher card = Pattern.compile("\\d{16}")
                        .matcher(d.findElement(By.tagName("body")).getText());
                return card.find() ? card.group() : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        });
        assertNotNull(cardNumber, "Fake credit card number was not generated");
        saveScreenshot("step2_credit_card");
        return cardNumber;
    }

    private void addBookToCart() {
        driver.get(SITE + "add_to_cart.py?book_id=4");
        String confirmation = driver.findElement(By.tagName("body")).getText();
        assertTrue(confirmation.contains("Book was added to the Shopping Cart"),
                "Add-to-cart confirmation is missing");

        driver.get(SITE + "shopping_cart.py");
        assertTrue(driver.findElement(By.tagName("body")).getText().contains("War and Peace"),
                "War and Peace is not shown in the shopping cart");
        saveScreenshot("step3_shopping_cart");
    }

    private void checkoutAndPay(String cardNumber) {
        clickWhenReady(By.xpath("//input[@value='Proceed to Checkout']"));

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("card_number")))
                .sendKeys(cardNumber);
        saveScreenshot("step4_checkout");
        clickWhenReady(By.xpath("//input[@value='Make Payment']"));

        wait.until(ExpectedConditions.urlContains("card_number"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "Thank you for your order"));
        saveScreenshot("step5_order_confirmation");
        report("Order finished successfully");
    }

    private void typeInto(By locator, String value) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        field.clear();
        field.sendKeys(value);
    }

    private void clickWhenReady(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private void saveScreenshot(String name) {
        try {
            File page = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path folder = Path.of("screenshots");
            Files.createDirectories(folder);
            Files.copy(page.toPath(), folder.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save screenshot " + name, e);
        }
    }

    private void report(String message) {
        System.out.println(">>> " + message);
    }

    @AfterClass
    public void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }
}
