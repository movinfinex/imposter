package io.gatehill.imposter.service;

import io.gatehill.imposter.ImposterConfig;
import io.gatehill.imposter.config.ResolvedResourceConfig;
import io.gatehill.imposter.plugin.config.PluginConfig;
import io.gatehill.imposter.plugin.config.resource.ResponseConfigHolder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
public interface ResourceService {
    /**
     * Extract the resource configurations from the plugin configuration, if present.
     *
     * @param pluginConfig the plugin configuration
     * @return the resource configurations
     */
    List<ResolvedResourceConfig> resolveResourceConfigs(PluginConfig pluginConfig);

    /**
     * Search for a resource configuration matching the current request.
     *
     * @param resources    the resources from the response configuration
     * @param method       the HTTP method of the current request
     * @param pathTemplate request path template
     * @param path         the path of the current request
     * @param pathParams   the path parameters of the current request
     * @param queryParams  the query parameters of the current request
     * @return a matching resource configuration or else empty
     */
    Optional<ResponseConfigHolder> matchResourceConfig(
            List<ResolvedResourceConfig> resources,
            HttpMethod method,
            String pathTemplate,
            String path,
            Map<String, String> pathParams,
            Map<String, String> queryParams
    );

    /**
     * Builds a {@link Handler} that processes a request.
     * <p>
     * If {@code requestHandlingMode} is {@link io.gatehill.imposter.server.RequestHandlingMode#SYNC}, then the {@code routingContextConsumer}
     * is invoked on the calling thread.
     * <p>
     * If it is {@link io.gatehill.imposter.server.RequestHandlingMode#ASYNC}, then upon receiving a request,
     * the {@code routingContextConsumer} is invoked on a worker thread, passing the {@code routingContext}.
     * <p>
     * Example:
     * <pre>
     * router.get("/example").handler(handleRoute(imposterConfig, allPluginConfigs, vertx, routingContext -> {
     *     // use routingContext
     * });
     * </pre>
     *
     * @param imposterConfig         the Imposter configuration
     * @param allPluginConfigs       all plugin configurations
     * @param vertx                  the current Vert.x instance
     * @param routingContextConsumer the consumer of the {@link RoutingContext}
     * @return the handler
     */
    Handler<RoutingContext> handleRoute(
            ImposterConfig imposterConfig,
            List<? extends PluginConfig> allPluginConfigs,
            Vertx vertx,
            Consumer<RoutingContext> routingContextConsumer
    );

    /**
     * Builds a {@link Handler} that processes a request.
     * <p>
     * If {@code requestHandlingMode} is {@link io.gatehill.imposter.server.RequestHandlingMode#SYNC}, then the {@code routingContextConsumer}
     * is invoked on the calling thread.
     * <p>
     * If it is {@link io.gatehill.imposter.server.RequestHandlingMode#ASYNC}, then upon receiving a request,
     * the {@code routingContextConsumer} is invoked on a worker thread, passing the {@code routingContext}.
     * <p>
     * Example:
     * <pre>
     * router.get("/example").handler(handleRoute(imposterConfig, pluginConfig, vertx, routingContext -> {
     *     // use routingContext
     * });
     * </pre>
     *
     * @param imposterConfig         the Imposter configuration
     * @param pluginConfig           the plugin configuration
     * @param vertx                  the current Vert.x instance
     * @param routingContextConsumer the consumer of the {@link RoutingContext}
     * @return the handler
     */
    Handler<RoutingContext> handleRoute(
            ImposterConfig imposterConfig,
            PluginConfig pluginConfig,
            Vertx vertx,
            Consumer<RoutingContext> routingContextConsumer
    );
}