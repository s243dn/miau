package com.mediatracker.scraping.domain.model;

/**
 * Modelo principal que representa un videojuego con toda la información
 * necesaria para mostrarlo en la interfaz de usuario. Contiene datos
 * desde el título básico hasta métricas de calidad como rating.
 */
public class Game {
    // Identificación básica del juego
    private String title;           // Título completo del juego
    private String steamUrl;        // Enlace directo a la página de Steam
    
    // Información visual y comercial
    private String imageUrl;        // URL de la imagen destacada o carátula
    private String price;           // Precio actual en formato texto
    private String description;     // Descripción breve del juego
    
    // Metadatos del juego
    private String releaseDate;     // Fecha de lanzamiento
    private String developer;       // Desarrollador o estudio creador
    private String platforms;       // Plataformas disponibles (Windows, Linux, etc.)
    
    // Métricas de calidad (procedentes de RAWG)
    private Double rating;          // Puntuación media (normalmente sobre 5)
    private Integer metacriticScore; // Puntuación de Metacritic (sobre 100)
    
    // Clasificación y ordenación
    private GameType gameType;      // Tipo de contenido (GAME, DLC, EDITION, etc.)
    private int relevance;          // Puntuación de relevancia para ordenar resultados
    
    // Getters y setters para cada campo
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getSteamUrl() { return steamUrl; }
    public void setSteamUrl(String steamUrl) { this.steamUrl = steamUrl; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    
    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }
    
    public String getPlatforms() { return platforms; }
    public void setPlatforms(String platforms) { this.platforms = platforms; }
    
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    
    public Integer getMetacriticScore() { return metacriticScore; }
    public void setMetacriticScore(Integer metacriticScore) { this.metacriticScore = metacriticScore; }
    
    public GameType getGameType() { return gameType; }
    public void setGameType(GameType gameType) { this.gameType = gameType; }
    
    public int getRelevance() { return relevance; }
    public void setRelevance(int relevance) { this.relevance = relevance; }
}