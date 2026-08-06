package okhttp3.metrics;

import io.micrometer.common.KeyValue;
import okhttp3.Request;
import okhttp3.Response;

import java.util.function.BiFunction;

/**
 * @author [@Loong Wan](https://github.com/loong10k)
 */
public interface OKhttp3MetricsSpecificTagHandler {

    BiFunction<Request, Response, KeyValue> getHandler();

}
