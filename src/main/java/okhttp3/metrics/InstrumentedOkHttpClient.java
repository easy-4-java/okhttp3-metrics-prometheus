/*
 * Copyright 2015 Ras Kasa Williams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.metrics;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.okhttp3.DefaultOkHttpObservationConvention;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationConvention;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Builds an instrumented {@link OkHttpClient} with metrics interceptors and event listeners.
 * Uses composition (builder pattern) instead of inheritance to avoid Kotlin final method issues on JDK 9+.
 */
final class InstrumentedOkHttpClient {

  private InstrumentedOkHttpClient() {
    // utility class
  }

  static OkHttpClient create(MeterRegistry registry,
                             OkHttpClient rawClient,
                             Map<String, String> extraTagMap,
                             List<String> requestTagKeys,
                             List<BiFunction<Request, Response, KeyValue>> contextSpecificTags,
                             UrlMapperEnum urlMapper,
                             boolean includeHostTag) {

    Collection<Tag> extraTags = (extraTagMap == null || extraTagMap.isEmpty())
        ? new ArrayList<>()
        : extraTagMap.entrySet().stream()
            .map(e -> Tag.of(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

    Collection<KeyValue> kvTags = (extraTagMap == null || extraTagMap.isEmpty())
        ? new ArrayList<>()
        : extraTagMap.entrySet().stream()
            .map(e -> KeyValue.of(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

    List<String> safeRequestTagKeys = (requestTagKeys == null || requestTagKeys.isEmpty())
        ? new ArrayList<>() : requestTagKeys;

    OkHttpClient.Builder builder = rawClient.newBuilder();

    // 1. Add network interceptor for metrics counters
    InstrumentedInterceptor metricsInterceptor = new InstrumentedInterceptor(registry, extraTags);
    builder.networkInterceptors().add(metricsInterceptor);

    // 2. Add network interceptor for observation
    OkHttpObservationConvention observationConvention =
        new DefaultOkHttpObservationConvention(OkHttp3Metrics.OKHTTP3_METRIC_NAME_PREFIX);
    OkHttpObservationInterceptor observationInterceptor = new OkHttpObservationInterceptor(
        ObservationRegistry.create(),
        observationConvention,
        OkHttp3Metrics.OKHTTP3_REQUEST_METRIC_NAME_PREFIX,
        urlMapper.get(),
        kvTags,
        contextSpecificTags,
        safeRequestTagKeys,
        includeHostTag
    );
    builder.networkInterceptors().add(observationInterceptor);

    // 3. Add event listener for detailed metrics
    OkHttpMetricsEventListener metricsEventListener = OkHttpMetricsEventListener
        .builder(registry, OkHttp3Metrics.OKHTTP3_REQUEST_METRIC_NAME_PREFIX)
        .tags(extraTags)
        .requestTagKeys(safeRequestTagKeys)
        .includeHostTag(includeHostTag)
        .uriMapper(urlMapper.get())
        .build();
    builder.eventListener(new InstrumentedEventListener(registry, metricsEventListener));

    return builder.build();
  }

}
