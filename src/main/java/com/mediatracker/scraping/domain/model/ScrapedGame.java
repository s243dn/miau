package com.mediatracker.scraping.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ScrapedGame {
    private String title;
    private String coverArtUrl;
    private String headerImageUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer discount;
    private String url;
    private String steamId;
    private String developer;
    private String publisher;
    private String releaseDate;
    private String description;
    private List<String> genres;
    private GameType gameType;
    private List<ScrapedGame> editions;
    private List<ScrapedGame> dlcs;
    private String source;
    private int relevance;

    public ScrapedGame() {
        this.genres = new ArrayList<>();
        this.editions = new ArrayList<>();
        this.dlcs = new ArrayList<>();
        this.gameType = GameType.GAME;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getCoverArtUrl() { return coverArtUrl; }
    public void setCoverArtUrl(String coverArtUrl) { this.coverArtUrl = coverArtUrl; }
    
    public String getHeaderImageUrl() { return headerImageUrl; }
    public void setHeaderImageUrl(String headerImageUrl) { this.headerImageUrl = headerImageUrl; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    
    public Integer getDiscount() { return discount; }
    public void setDiscount(Integer discount) { this.discount = discount; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getSteamId() { return steamId; }
    public void setSteamId(String steamId) { this.steamId = steamId; }
    
    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }
    
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    
    public GameType getGameType() { return gameType; }
    public void setGameType(GameType gameType) { this.gameType = gameType; }
    
    public List<ScrapedGame> getEditions() { return editions; }
    public void setEditions(List<ScrapedGame> editions) { this.editions = editions; }
    
    public List<ScrapedGame> getDlcs() { return dlcs; }
    public void setDlcs(List<ScrapedGame> dlcs) { this.dlcs = dlcs; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public int getRelevance() { return relevance; }
    public void setRelevance(int relevance) { this.relevance = relevance; }
}