package dev.asyncluna.zenith.core.integration.animalapi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum AnimalApiEndpoint {
    GET_RANDOM_IMAGE("/img/{animal}", 2),
    GET_RANDOM_FACT("/fact/{animal}", 2),
    GET_IMAGE_AND_FACT("/all/{animal}", 2);

    public static final String BASE_URL = "https://api.animality.xyz";

    private final String path;
    private final long ttlSeconds;

    public String getPath(Object... args) {
        return String.format(path, args);
    }

    public String getUrl(Object... args) {
        return BASE_URL + getPath(args);
    }
}
