package io.github.chinalhr.httpclientoptimization.utils;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @Author : lhr
 * @Date : 14:43 2018/9/29
 * <p>
 * Http请求
 */
public class RestTemplateUtils {

    private static RestTemplate restRetryTemplate;

    private static RestTemplate restTemplate;

    /**
     * 获取请求重试的RestTemplate
     * @return
     */
    public static RestTemplate getRestRetryTemplate() {
        if (Objects.isNull(restRetryTemplate)) {
            ClientHttpRequestFactory factory = clientHttpRequestFactory(true);
            restRetryTemplate = new RestTemplate(factory);
        }
        return restRetryTemplate;
    }

    /**
     * 获取请求不重试的RestTemplate
     * @return
     */
    public static RestTemplate getRestTemplate() {
        if (Objects.isNull(restTemplate)) {
            ClientHttpRequestFactory factory = clientHttpRequestFactory(false);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }


    public static ClientHttpRequestFactory clientHttpRequestFactory(boolean isRetry) {
        // 长连接保持30秒
        PoolingHttpClientConnectionManager pollingConnectionManager = new PoolingHttpClientConnectionManager(30000, TimeUnit.MILLISECONDS);
        // 总连接数
        pollingConnectionManager.setMaxTotal(200);
        // 默认同路由的并发数
        pollingConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setConnectionManager(pollingConnectionManager);
        // 重试次数，默认是3次，设置为2次，没有开启
        if (isRetry) {
            httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(2, true));
        } else {
            httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
        }
        // 保持长连接配置，需要在头添加Keep-Alive
        httpClientBuilder.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE);

        HttpClient httpClient = httpClientBuilder.build();

        // httpClient连接配置，底层是配置RequestConfig
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        // 连接超时
        clientHttpRequestFactory.setConnectTimeout(300);
        // 数据读取超时时间，即SocketTimeout
        // (本地压测 用户平均请求等待时间：5.959/ms,测试环境Cat Avg(ms) 0.4,Cat Max(ms) 105.3 设置为100)
        clientHttpRequestFactory.setReadTimeout(100);
        // 连接不够用的等待时间，不宜过长，必须设置，比如连接不够用时，时间过长将是灾难性的
        clientHttpRequestFactory.setConnectionRequestTimeout(200);
        // 缓冲请求数据，默认值是true。通过POST或者PUT大量发送数据时，建议将此属性更改为false，以免耗尽内存。
        // clientHttpRequestFactory.setBufferRequestBody(false);
        return clientHttpRequestFactory;
    }

}
