package cn.tengyuan.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 外部能耗平台或中天接口不可达时，必须及时释放定时任务线程，
        // 避免一次网络阻塞与下一次调度重叠并持续堆积。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(30_000);

        configureSslTrustAll();

        return new RestTemplate(requestFactory);
    }

    /**
     * 中天接口服务器的 SSL 证书不在 JVM 默认信任库中（PKIX path building failed），
     * 在集成对接阶段无法要求对方更换受信任证书，因此对本应用的 HTTPS 连接跳过证书校验。
     */
    private void configureSslTrustAll() {
        try {
            TrustManager[] trustAllCerts = {new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("配置 SSL 信任失败，HTTPS 请求可能无法正常工作", e);
        }
    }
}
