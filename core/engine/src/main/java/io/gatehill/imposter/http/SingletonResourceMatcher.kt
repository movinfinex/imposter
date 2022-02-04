/*
 * Copyright (c) 2016-2022.
 *
 * This file is part of Imposter.
 *
 * "Commons Clause" License Condition v1.0
 *
 * The Software is provided to you by the Licensor under the License, as
 * defined below, subject to the following condition.
 *
 * Without limiting other conditions in the License, the grant of rights
 * under the License will not include, and the License does not grant to
 * you, the right to Sell the Software.
 *
 * For purposes of the foregoing, "Sell" means practicing any or all of
 * the rights granted to you under the License to provide to third parties,
 * for a fee or other consideration (including without limitation fees for
 * hosting or consulting/support services related to the Software), a
 * product or service whose value derives, entirely or substantially, from
 * the functionality of the Software. Any license notice or attribution
 * required by the License must also include this Commons Clause License
 * Condition notice.
 *
 * Software: Imposter
 *
 * License: GNU Lesser General Public License version 3
 *
 * Licensor: Peter Cornish
 *
 * Imposter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Imposter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Imposter.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.gatehill.imposter.http

import com.google.common.base.Strings
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import io.gatehill.imposter.config.ResolvedResourceConfig
import io.gatehill.imposter.plugin.config.resource.BasicResourceConfig
import io.gatehill.imposter.plugin.config.resource.MethodResourceConfig
import io.gatehill.imposter.plugin.config.resource.reqbody.RequestBodyResourceConfig
import io.gatehill.imposter.util.CollectionUtil.convertKeysToLowerCase
import io.gatehill.imposter.util.StringUtil.safeEquals
import org.apache.logging.log4j.LogManager
import java.util.Locale
import java.util.Objects
import java.util.function.Function
import java.util.function.Supplier

/**
 * Matches resources using elements of the HTTP request.
 *
 * @author Pete Cornish
 */
open class SingletonResourceMatcher : ResourceMatcher {
    /**
     * {@inheritDoc}
     */
    override fun matchResourceConfig(
        resources: List<ResolvedResourceConfig>,
        httpExchange: HttpExchange,
    ): BasicResourceConfig? {
        val resourceConfigs = filterResourceConfigs(resources, httpExchange)
        if (resourceConfigs.isEmpty()) {
            return null
        }

        val request = httpExchange.request()
        if (resourceConfigs.size == 1) {
            LOGGER.debug("Matched response config for {} {}", request.method(), request.path())
        } else {
            LOGGER.warn(
                "More than one response config found for {} {} - this is probably a configuration error. Choosing first response configuration.",
                request.method(),
                request.path()
            )
        }
        return resourceConfigs[0].config
    }

    protected open fun filterResourceConfigs(
        resources: List<ResolvedResourceConfig>,
        httpExchange: HttpExchange,
    ): List<ResolvedResourceConfig> {
        var resourceConfigs = resources.filter { res -> isRequestMatch(res, httpExchange) }

        // find the most specific, by filtering those that match by those that specify parameters
        resourceConfigs = filterByPairs(resourceConfigs, ResolvedResourceConfig::pathParams)
        resourceConfigs = filterByPairs(resourceConfigs, ResolvedResourceConfig::queryParams)
        resourceConfigs = filterByPairs(resourceConfigs, ResolvedResourceConfig::requestHeaders)

        return resourceConfigs
    }

    /**
     * Determine if the resource configuration matches the current request.
     *
     * @param resource     the resource configuration
     * @param httpExchange the current exchange
     * @return `true` if the resource matches the request, otherwise `false`
     */
    private fun isRequestMatch(
        resource: ResolvedResourceConfig,
        httpExchange: HttpExchange,
    ): Boolean {
        val resourceConfig = resource.config
        val request = httpExchange.request()

        // path template can be null when a regex route is used
        val pathTemplate = httpExchange.currentRoutePath
        val pathMatch = request.path() == resourceConfig.path || (pathTemplate?.let { it == resourceConfig.path } == true)

        val methodMatch = if (resourceConfig is MethodResourceConfig) {
            request.method() == resourceConfig.method
        } else {
            // unspecified implies any match
            true
        }

        return pathMatch && methodMatch &&
            matchPairs(httpExchange.pathParams(), resource.pathParams, true) &&
            matchPairs(httpExchange.queryParams(), resource.queryParams, true) &&
            matchPairs(request.headers(), resource.requestHeaders, false) &&
            matchRequestBody({ httpExchange.bodyAsString }, resource.config)
    }

    private fun filterByPairs(
        resourceConfigs: List<ResolvedResourceConfig>,
        pairsSupplier: Function<ResolvedResourceConfig, Map<String, String>>
    ): List<ResolvedResourceConfig> {
        val configsWithPairs = resourceConfigs.filter { res -> pairsSupplier.apply(res).isNotEmpty() }

        return configsWithPairs.ifEmpty {
            // no resource configs specified params - don't filter
            resourceConfigs
        }
    }

    /**
     * If the resource contains parameter configuration, check they are all present.
     * If the configuration contains no parameters, then this evaluates to true.
     * Additional parameters not in the configuration are ignored.
     *
     * @param resourceMap           the configured parameters to match
     * @param requestMap            the parameters from the request (e.g. query or path)
     * @param caseSensitiveKeyMatch whether to match keys case-sensitively
     * @return `true` if the configured parameters match the request, otherwise `false`
     */
    private fun matchPairs(
        requestMap: Map<String, String>,
        resourceMap: Map<String, String>,
        caseSensitiveKeyMatch: Boolean
    ): Boolean {
        // none configured - implies any match
        if (resourceMap.isEmpty()) {
            return true
        }
        val comparisonMap = if (caseSensitiveKeyMatch) requestMap else convertKeysToLowerCase(requestMap)
        return resourceMap.entries.any { (key, value) ->
            val configKey: String = if (caseSensitiveKeyMatch) key else key.lowercase(Locale.getDefault())
            safeEquals(comparisonMap[configKey], value)
        }
    }

    /**
     * Match the request body against the supplied configuration.
     *
     * @param bodySupplier         supplies the request body
     * @param resourceConfig the match configuration
     * @return `true` if the configuration is empty, or the request body matches the configuration, otherwise `false`
     */
    protected fun matchRequestBody(bodySupplier: Supplier<String?>, resourceConfig: BasicResourceConfig): Boolean {
        if (resourceConfig !is RequestBodyResourceConfig ||
            Objects.isNull(resourceConfig.requestBody) ||
            Strings.isNullOrEmpty(resourceConfig.requestBody!!.jsonPath)
        ) {
            // none configured - implies any match
            return true
        }

        val requestBodyConfig = resourceConfig.requestBody!!
        val body = bodySupplier.get()
        val bodyValue = if (Strings.isNullOrEmpty(body)) {
            null
        } else {
            try {
                JsonPath.read<Any>(body, requestBodyConfig.jsonPath)
            } catch (ignored: PathNotFoundException) {
                null
            }
        }
        return safeEquals(requestBodyConfig.value, bodyValue)
    }

    companion object {
        private val LOGGER = LogManager.getLogger(SingletonResourceMatcher::class.java)
        val instance = SingletonResourceMatcher()
    }
}
