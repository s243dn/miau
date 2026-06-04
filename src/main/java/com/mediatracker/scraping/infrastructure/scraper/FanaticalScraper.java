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
public class FanaticalScraper {

    private static final String BASE_URL =
            "https://www.fanatical.com/en/search?search=";

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

            driver = new ChromeDriver(options);

            String url =
                    BASE_URL + gameTitle.replace(" ", "%20");

            driver.get(url);

            Thread.sleep(8000);

            JavascriptExecutor js =
                    (JavascriptExecutor) driver;

            String script =
                    """
                    return [...document.querySelectorAll('.HitCard')]
                        .map(card => {

                            const title =
                                card.querySelector('.hitCardStripe__seoName')
                                    ?.innerText
                                    ?.trim() || '';

                            const price =
                                card.querySelector('.card-price')
                                    ?.innerText
                                    ?.trim() || '';

                            return {
                                title,
                                price
                            };
                        });
                    """;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) js.executeScript(script);

            System.out.println(
                    "DEBUG FANATICAL -> resultados encontrados: "
                            + results.size());

            for (Map<String, Object> item : results) {

                String title =
                        String.valueOf(item.get("title"));

                String price =
                        String.valueOf(item.get("price"));

                System.out.println(
                        title + " | " + price);

                if (title.isBlank() || price.isBlank()) {
                    continue;
                }

                try {

                    String cleanPrice =
                            price.replaceAll("[^0-9,.]", "")
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

        } catch (Exception e) {

            System.out.println(
                    "❌ Error FanaticalScraper: "
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