package io.github.chinalhr.httpclientoptimization.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import io.github.chinalhr.httpclientoptimization.schedule.FixedRateSchedule;
import io.github.chinalhr.httpclientoptimization.schedule.impl.FixedRateScheduleImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author : ChinaLHR
 * @Date : Create in 22:28 2018/10/16
 * @Email : 13435500980@163.com
 */
public class HttpClientUtils {

    private static HttpClient retryHttpClient;

    public static <T> T doGet(String url, TypeReference<T> typeReference) {
        HttpGet httpGet = newJSONGetRequest(url,null);
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(300).setConnectionRequestTimeout(200)
                    .setSocketTimeout(200).build();
            httpGet.setConfig(requestConfig);
            HttpResponse execute = getRetryHttpClient().execute(httpGet);
            String json = EntityUtils.toString(execute.getEntity(), Charsets.UTF_8);
            //需要进行json序列化
            return (T) json;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static HttpClient getRetryHttpClient() {
        if (Objects.isNull(retryHttpClient)) {
            retryHttpClient = getCloseableHttpClient(true);
        }
        return retryHttpClient;
    }


    public static HttpGet newJSONGetRequest(String url, String userAgent) {
        HttpGet httpGet = new HttpGet(encodeUrl(url));
        httpGet.setHeader("Accept", "application/json;charset=UTF-8");
        if (StringUtils.isNotEmpty(userAgent)) {
            httpGet.setHeader("User-Agent", userAgent);
        }
        return httpGet;
    }

    private static final String URL_SPLIT = "?";

    public static String encodeUrl(String url) {

        int i = StringUtils.indexOf(url, URL_SPLIT);
        if (i == -1) {
            return url;
        }
        String urlSubject = url.substring(0, i);
        String urlParam = url.substring(i + 1);
        return urlSubject + URL_SPLIT + encodeParam(urlParam, Charsets.UTF_8);

    }

    private static final String PARAM_SPLIT = "&";

    private static String encodeParam(String urlParam, Charset charset) {
        if (StringUtils.isBlank(urlParam)) {
            return StringUtils.EMPTY;
        }
        Iterable<String> split = Splitter.on(PARAM_SPLIT).trimResults().omitEmptyStrings().split(urlParam);
        List<NameValuePair> nameValuePairs = Lists.newArrayList(split).stream()
                .map(HttpClientUtils::toValuePair)
                .collect(Collectors.toList());

        String paramString = URLEncodedUtils.format(nameValuePairs, charset);
        return paramString;
    }

    private static final String EQUAL_STRING = "=";

    private static NameValuePair toValuePair(String param) {
        if (StringUtils.isBlank(param)) {
            return null;
        }
        int i = param.indexOf(EQUAL_STRING);
        if (i == -1) {
            return null;
        }

        Iterable<String> pair = Splitter.on(EQUAL_STRING).omitEmptyStrings().trimResults().split(param);
        ArrayList<String> splitRes = Lists.newArrayList(pair);
        if (CollectionUtils.isEmpty(splitRes)) {
            return null;
        }

        String key = splitRes.get(0);
        String value = CollectionUtils.size(splitRes) == 2 ? splitRes.get(1) : StringUtils.EMPTY;
        return new BasicNameValuePair(key, value);
    }

    public static CloseableHttpClient getCloseableHttpClient(boolean isRetry) {
        // 长连接保持30秒
        PoolingHttpClientConnectionManager pollingConnectionManager = new PoolingHttpClientConnectionManager(30, TimeUnit.SECONDS);
        // 总连接数
        pollingConnectionManager.setMaxTotal(200);
        // 默认同路由的并发数
        pollingConnectionManager.setDefaultMaxPerRoute(100);
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

        CloseableHttpClient httpClient = httpClientBuilder.build();

        runIdleConnectionMonitor(pollingConnectionManager);
        return httpClient;
    }

    private static final Long IDLE_INITIALDELAY = 1000L;
    private static final Long IDLE_PERIOD = 1000L;



    /**
     * 如果连接在服务器端关闭，则客户端连接无法检测到连接状态的变化（并通过关闭socket 来做出适当的反应）,
     * 需要使用定时监控清理实现关闭
     * 参考：https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html  2.5 Connection eviction policy
     *
     * @param clientConnectionManager
     */
    private static void runIdleConnectionMonitor(HttpClientConnectionManager clientConnectionManager) {
        FixedRateSchedule schedule = new FixedRateScheduleImpl();
        schedule.setPoolTag("IDLE_CONNECTION_MONITOR_POOL");
        schedule.init();
        schedule.schedule(() -> {
            //关闭过期的链接
            clientConnectionManager.closeExpiredConnections();
            //关闭闲置超过30s的链接
            clientConnectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
        }, IDLE_INITIALDELAY, IDLE_PERIOD, TimeUnit.MILLISECONDS);
    }

    /**
     * 配置keep-alive
     * @return
     */
    private static ConnectionKeepAliveStrategy getConnectionKeepAliveStrategy(){
        return (response, context) -> {
            HeaderElementIterator it = new BasicHeaderElementIterator
                    (response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && param.equalsIgnoreCase
                        ("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return 60 * 1000;//如果没有约定，则默认定义时长为60s
        };
    }
}
