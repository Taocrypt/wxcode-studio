package com.wxcode.studio;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Same rules as app/validation.py: http(s) only, no credentials, IDN host, percent-encode, <= 512 bytes. */
final class UrlNormalizer {
    private static final String SAFE = ":/?#[]@!$&'()*+,;=-._~%";

    static String normalize(String value) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("网址不能为空");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c < 32 || c == 127 || Character.isSpaceChar(c)) {
                throw new IllegalArgumentException("网址不能包含空白或控制字符");
            }
        }
        if (isMiniProgram(value)) {
            throw new IllegalArgumentException("微信扫码只把 #小程序:// 当文字，需要先转成 https 链接（填好 C1N token 后会自动转）");
        }
        int sep = value.indexOf("://");
        if (sep <= 0) throw new IllegalArgumentException("请输入 http(s):// 网址或 #小程序:// 链接");
        String scheme = value.substring(0, sep).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("只支持 http(s):// 网址或 #小程序:// 链接");
        }
        int start = sep + 3;
        int end = value.length();
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') { end = i; break; }
        }
        String authority = value.substring(start, end);
        if (authority.contains("@")) throw new IllegalArgumentException("不接受带用户名或密码的网址");
        String host = authority;
        String port = "";
        if (!authority.startsWith("[")) {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) { host = authority.substring(0, colon); port = authority.substring(colon + 1); }
        } else {
            int close = authority.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("网址主机格式不正确");
            host = authority.substring(0, close + 1);
            String rest = authority.substring(close + 1);
            if (rest.startsWith(":")) port = rest.substring(1);
            else if (!rest.isEmpty()) throw new IllegalArgumentException("网址主机格式不正确");
        }
        if (host.isEmpty()) throw new IllegalArgumentException("网址缺少域名");
        if (!port.isEmpty()) {
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65535) {
                throw new IllegalArgumentException("端口号不正确");
            }
        }
        boolean ascii = true;
        for (int i = 0; i < host.length(); i++) if (host.charAt(i) > 127) { ascii = false; break; }
        if (!ascii) {
            try {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("域名格式不正确");
            }
            authority = host + (authority.lastIndexOf(':') >= 0 && !port.isEmpty() ? ":" + port : "");
        }
        String rebuilt = value.substring(0, start) + authority + value.substring(end);
        StringBuilder out = new StringBuilder();
        for (byte b : rebuilt.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || (c < 128 && SAFE.indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        String normalized = out.toString();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("链接过长，请缩短链接；葵花码实际容量取决于内容");
        }
        return normalized;
    }

    /** WeChat mini program short link as copied from the mini program menu: #小程序://名称/xxxx. Encoded as-is. */
    static boolean isMiniProgram(String v) {
        return v != null && (v.startsWith("#小程序://") || v.startsWith("小程序://"));
    }

    private UrlNormalizer() {}
}
