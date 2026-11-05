package com.wxcode.studio;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C1N open API (https://www.c1n.cn/helpCenter/apihelp/...):
 *  POST https://c1n.cn/link/short        form: url, key, remark, expiryDate, domainName, groupName
 *  POST https://c1n.cn/link/miniProgram  form: appId, path, secret, remark, expiryDate, domainName, groupName
 *  header "token" from 控制台-个人中心-短链配置. Response {code:0,data:"https://...",msg}.
 */
final class C1nClient {
    private static final String BASE = "https://c1n.cn";

    static String shortUrl(String token, String url) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("url", url);
        f.put("remark", "葵花码工坊");
        return post("/link/short", token, f);
    }

    static String miniProgram(String token, String appId, String path, String secret) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("appId", appId);
        f.put("path", path);
        if (secret != null && !secret.isEmpty()) f.put("secret", secret);
        f.put("remark", "葵花码工坊");
        return post("/link/miniProgram", token, f);
    }

    private static String post(String api, String token, Map<String, String> form) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("请先填写 C1N token（控制台 - 个人中心 - 短链配置）");
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + api).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("token", token.trim());
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int http = c.getResponseCode();
        InputStream in = http >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        }
        c.disconnect();
        JSONObject j;
        try { j = new JSONObject(text); } catch (Exception e) { throw new IllegalStateException("C1N 返回异常（HTTP " + http + "）"); }
        int code = j.optInt("code", -1);
        String data = j.optString("data", "");
        if (code != 0 || !data.matches("(?i)^https?://.+")) {
            String msg = j.optString("msg", "未知错误");
            throw new IllegalStateException("C1N 转链失败：" + msg + "（code " + code + "）");
        }
        return data.trim();
    }

    private C1nClient() {}
}
