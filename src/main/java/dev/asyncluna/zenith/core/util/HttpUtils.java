package dev.asyncluna.zenith.core.util;

import java.net.URI;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class HttpUtils {
    public static final String USER_AGENT = "zenith-discord-bot [github:asyncLuna]";
    public static final String PARAM_CONTEXT_KEY = "API_QUERY_PARAMS_MAP";

    public static final WebClient WEB_CLIENT = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .filter(genericQueryParamFilter())
            .build();

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ExchangeFilterFunction genericQueryParamFilter() {
        return (request, next) -> Mono.deferContextual(context -> {
            if (!context.hasKey(PARAM_CONTEXT_KEY)) return next.exchange(request);

            Object contextData = context.get(PARAM_CONTEXT_KEY);
            if (!(contextData instanceof Map<?, ?> rawMap)) return next.exchange(request);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(request.url());

            rawMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    uriBuilder.queryParam(key.toString(), value.toString());
                }
            });

            URI newUri = uriBuilder.build(true).toUri();
            ClientRequest filteredRequest =
                    ClientRequest.from(request).url(newUri).build();

            return next.exchange(filteredRequest);
        });
    }
}
