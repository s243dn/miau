package com.mediatracker.scraping.infrastructure.scraper;

import com.mediatracker.scraping.domain.model.ScrapedGame;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HumbleBundleScraper {

    private static final String BASE_URL =
            "https://www.humblebundle.com/store/search?search=";

    public List<ScrapedGame> searchGames(String gameTitle) {

        List<ScrapedGame> games = new ArrayList<>();
        WebDriver driver = null;

        try {

            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");

            driver = new ChromeDriver(options);

            String url =
                    BASE_URL + gameTitle.replace(" ", "%20");

            driver.get(url);

            Thread.sleep(12000);

            JavascriptExecutor js =
                    (JavascriptExecutor) driver;

            String script =
                    """
                    const products = [];

                    const links =
                        [...document.querySelectorAll('a[href*="/store/"]')];

                    for (const link of links) {

                        const text =
                            link.innerText
                                ?.replace(/\\s+/g, ' ')
                                ?.trim();

                        if (!text || text.length < 3) {
                            continue;
                        }

                        const priceMatch =
                            text.match(/\\$\\s*\\d+[\\.,]?\\d*/);

                        if (!priceMatch) {
                            continue;
                        }

                        const price = priceMatch[0];

                        const title =
                            text.replace(price, "")
                                .replace(/\\n/g, " ")
                                .trim();

                        if (!title) {
                            continue;
                        }

                        products.push({
                            title: title,
                            price: price
                        });
                    }

                    return products;
                    """;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) js.executeScript(script);

            System.out.println(
                    "DEBUG HUMBLE -> resultados encontrados: "
                            + results.size());

            for (Map<String, Object> item : results) {

                try {

                    String title =
                            String.valueOf(item.get("title"));

                    String priceText =
                            String.valueOf(item.get("price"));

                    System.out.println(
                            title + " | " + priceText);

                    String cleanPrice =
                            priceText
                                    .replaceAll("[^0-9,.]", "")
                                    .replace(",", ".");

                    if (cleanPrice.isBlank()) {
                        continue;
                    }

                    ScrapedGame game =
                            new ScrapedGame();

                    game.setTitle(title);

                    game.setPrice(
                            new BigDecimal(cleanPrice));

                    games.add(game);

                } catch (Exception ignored) {
                }
            }

            if (games.isEmpty()) {

                System.out.println(
                        "DEBUG HUMBLE -> No se encontraron juegos");
            }

        } catch (Exception e) {

            System.out.println(
                    "❌ Error HumbleBundleScraper: "
                            + e.getMessage());

            e.printStackTrace();

        } finally {

            if (driver != null) {

                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }

        return games;
    }
}