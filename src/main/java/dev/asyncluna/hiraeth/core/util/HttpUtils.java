package dev.asyncluna.hiraeth.core.util;

import dev.asyncluna.hiraeth.core.integration.overfastapi.OverfastApiEndpoint;
import dev.asyncluna.hiraeth.core.integration.overfastapi.OverfastApiQueryParams;
import java.net.URI;
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
  public static final String USER_AGENT = "hiraeth-discord-bot [github:asyncLuna]";
  public static final String PARAM_CONTEXT_KEY = "OVERFAST_API_PARAMS";
  public static final WebClient WEB_CLIENT =
      WebClient.builder()
          .baseUrl(OverfastApiEndpoint.BASE_URL)
          .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
          .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
          .filter(overfastApiQueryParamFilter())
          .build();
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static ExchangeFilterFunction overfastApiQueryParamFilter() {
    return (request, next) ->
        Mono.deferContextual(
            context -> {
              if (!context.hasKey(PARAM_CONTEXT_KEY)) return next.exchange(request);

              OverfastApiQueryParams params = context.get(PARAM_CONTEXT_KEY);

              UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(request.url());
              params.asMap().forEach(uriBuilder::queryParam);
              URI newUri = uriBuilder.build(true).toUri();

              ClientRequest filteredRequest = ClientRequest.from(request).url(newUri).build();

              return next.exchange(filteredRequest);
            });
  }
}
