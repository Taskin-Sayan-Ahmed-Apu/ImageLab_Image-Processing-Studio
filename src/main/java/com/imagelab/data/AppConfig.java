package com.imagelab.data;

/**
 * Mirrors src/main/resources/config.json.
 * Gson deserializes the file into this class at startup.
 */
public class AppConfig {

    private String appName;
    private String version;
    private Defaults defaults;
    private Api api;
    private Database database;

    public String getAppName() { return appName; }
    public String getVersion() { return version; }
    public Defaults getDefaults() { return defaults; }
    public Api getApi() { return api; }
    public Database getDatabase() { return database; }

    public static class Defaults {
        private double logC;
        private double gamma;
        private int brightnessDelta;
        private double contrastFactor;
        private int bitPlane;
        private int meanKernel;
        private int medianKernel;
        private double highBoostA;
        private Piecewise piecewise;

        public double getLogC() { return logC; }
        public double getGamma() { return gamma; }
        public int getBrightnessDelta() { return brightnessDelta; }
        public double getContrastFactor() { return contrastFactor; }
        public int getBitPlane() { return bitPlane; }
        public int getMeanKernel() { return meanKernel; }
        public int getMedianKernel() { return medianKernel; }
        public double getHighBoostA() { return highBoostA; }
        public Piecewise getPiecewise() { return piecewise; }
    }

    public static class Piecewise {
        
        private int r1, s1, r2, s2;
        public int getR1() { return r1; }
        public int getS1() { return s1; }
        public int getR2() { return r2; }
        public int getS2() { return s2; }
    }

    public static class Api {
        private String randomImageEndpoint;
        private int timeoutSeconds;
        public String getRandomImageEndpoint() { return randomImageEndpoint; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
    }

    public static class Database {
        private String url;
        
        public String getUrl() { 
            return url; }
    }
}
