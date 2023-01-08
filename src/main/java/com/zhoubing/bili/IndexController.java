package com.zhoubing.bili;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.CharacterSetECI;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import nicelee.bilibili.INeedAV;
import nicelee.bilibili.INeedLogin;
import nicelee.bilibili.enums.VideoQualityEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.FavList;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.parsers.impl.AbstractPageQueryParser;
import nicelee.bilibili.util.*;
import nicelee.ui.DialogSMSLogin;
import nicelee.ui.Global;
import nicelee.ui.thread.DownloadRunnable;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

@RestController
@RequestMapping("/api/dataservice")
public class IndexController {
    RestTemplate restTemplate = new RestTemplate();
    private static Logger logger = LoggerFactory.getLogger(IndexController.class);
    private static final String YOUDAO_URL = "https://openapi.youdao.com/api";

    private static final String APP_KEY = "7ae29ee68b869af8";

    private static final String APP_SECRET = "BfhXtWH8EkNYEDyyWSQimHnO04VjcmOH";

    {
        // 初始化 - 登录
        INeedLogin inl = new INeedLogin();
        if (inl.readCookies() != null) {
            Global.needToLogin = true;
        }
    }
    private INeedLogin inl;
    private String authKey;

    @RequestMapping(value = "/login_info", method = RequestMethod.GET)
    public String getLoginInfo() {
        try {
            Global.isLogin = inl.getAuthStatus(authKey);
            if (Global.isLogin) {

                // 保存cookie到本地
                inl.saveCookiesAndToken();
                // 设置全局Cookie
                HttpCookies.setGlobalCookies(inl.iCookies);
                // 获取用户信息
                inl.getLoginStatus(inl.iCookies);
                // 初始化用户数据显示
//                initUserInfo(inl);
                System.out.println("成功登录...");
                return inl.user.getPoster();
            } else {
                Global.needToLogin = true;
                return "";
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    @RequestMapping(value = "/qrcode", method = RequestMethod.GET)
    public String getQRcode() {
        Global.needToLogin = true;
        Global.loginType = "qr";
        inl = new INeedLogin();

        System.out.println("登录线程被调用...");
        if (Global.isLogin || !Global.needToLogin) {
            // Global.index.jlHeader.addMouseListener(Global.index);
            System.out.println("已经登录,或没有发起登录请求");
            return "1";
        }
        String cookiesStr = inl.readCookies();
        // 检查有没有本地cookie配置
        if (cookiesStr != null) {
            System.out.println("检查到存在本地Cookies...");
            List<HttpCookie> cookies = HttpCookies.convertCookies(cookiesStr);
            // 成功登录后即返回,不再进行二维码扫码工作
            if (inl.getLoginStatus(cookies)) {
                System.out.println("本地Cookies验证有效...");
                // 设置全局Cookie
                HttpCookies.setGlobalCookies(cookies);
                // 初始化用户数据显示
//                initUserInfo(inl);
                System.out.println("成功登录...");
                Global.isLogin = true;
                return inl.user.getPoster();
            } else {
                System.out.println("本地Cookies验证无效...");
                // 置空全局Cookie
                HttpCookies.setGlobalCookies(null);
            }
        }
        System.out.println("没有检查到本地Cookies...");
        //QRLogin(inl);
        switch (Global.loginType) {
            case "pwd":
                //PwdLogin(inl);
                break;
            case "qr":
                return QRLogin(inl);
//                break;
            case "sms":
                DialogSMSLogin dialog = new DialogSMSLogin(inl);
                dialog.init();
                break;
            default:
                return QRLogin(inl);
//                break;
        }
        nicelee.bilibili.util.Logger.println("线程即将结束，当前登录状态： " + Global.isLogin);
        if (Global.isLogin) {
            // 保存cookie到本地
            inl.saveCookiesAndToken();
            // 设置全局Cookie
            HttpCookies.setGlobalCookies(inl.iCookies);
            // 获取用户信息
            inl.getLoginStatus(inl.iCookies);
            // 初始化用户数据显示
            initUserInfo(inl);
            System.out.println("成功登录...");
        } else {
            // Global.index.jlHeader.addMouseListener(Global.index);
            Global.needToLogin = true;
        }
        return "3";
    }

    private String QRLogin(INeedLogin inl) {
        /**
         * 1. 访问 Get 访问 https://passport.bilibili.com/qrcode/getLoginUrl 获取 oauthKey ==>
         * 链接 ==> 二维码
         */
        System.out.println("正在获取验证AuthKey以生成二维码...");
        authKey = inl.getAuthKey();
        System.out.println("authKey: " + authKey);

        try {
            // 设置二维码纠错级别ＭＡＰ
            Hashtable<EncodeHintType, Object> hintMap = new Hashtable<EncodeHintType, Object>();
            hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // 矫错级别
            hintMap.put(EncodeHintType.CHARACTER_SET, CharacterSetECI.UTF8);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // 创建比特矩阵(位矩阵)的QR码编码的字符串
            BitMatrix byteMatrix = qrCodeWriter.encode(inl.qrCodeStr, BarcodeFormat.QR_CODE, 900, 900, hintMap);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(byteMatrix, "png", outputStream);
            return Base64.encodeBase64String(outputStream.toByteArray());
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }

        // 显示二维码图片
//        FrameQRCode qr = new FrameQRCode(inl.qrCodeStr);
//        qr.initUI();

        /**
         * 2. 周期性Post 访问 https://passport.bilibili.com/qrcode/getLoginInfo 直至扫码成功
         * 成功后保存Cookie
         */
//        long start = System.currentTimeMillis();
//        while (!Global.isLogin && Global.needToLogin && System.currentTimeMillis() - start < 60 * 1000) {
//            try {
//                Global.isLogin = inl.getAuthStatus(authKey);
//                System.out.println("------------");
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//            }
//        }
        // 销毁图片
//        System.out.println("登录线程结束...");
//        qr.dispose();
//        return "4";
    }

    public void initUserInfo(INeedLogin inl) {
        // 设置当前头像
        try {
             System.out.println(inl.user.getPoster());
            URL fileURL = new URL(inl.user.getPoster());
            ImageIcon imag1 = new ImageIcon(fileURL);
            imag1 = new ImageIcon(imag1.getImage().getScaledInstance(80, 80, Image.SCALE_DEFAULT));
            Global.index.jlHeader.setToolTipText("当前用户为: " + inl.user.getName());
            Global.index.jlHeader.setIcon(imag1);
             Global.index.jlHeader.removeMouseListener(Global.index);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        // 设置收藏夹
        try {
            String favUrl = "https://api.bilibili.com/medialist/gateway/base/created?pn=1&ps=100&is_space=0&jsonp=jsonp&up_mid="
                    + inl.user.getUid();
            HttpRequestUtil util = new HttpRequestUtil();
            String jsonStr = util.getContent(favUrl, new HttpHeaders().getAllFavListHeaders(inl.user.getUid()),
                    HttpCookies.getGlobalCookies());

            JSONArray list = new JSONObject(jsonStr).getJSONObject("data").getJSONArray("list");
            if (Global.index.cmbFavList.getItemCount() == 1) {
                Global.index.cmbFavList.addItem("稍后再看");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject favlist = list.getJSONObject(i);
                    FavList fav = new FavList(favlist.getLong("mid"), favlist.getLong("id"),
                            favlist.getInt("media_count"), favlist.getString("title"));
                    Global.index.cmbFavList.addItem(fav);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/translate", method = RequestMethod.POST)
    public String getTranslate(@RequestBody String param) {
        System.out.println("param: " + param);
        String[] strings = param.split("&");

        Map<String, String> params = new HashMap<>();
        String q = strings[0].split("=")[1].replaceAll("\"", "");
        String salt = String.valueOf(System.currentTimeMillis());
        params.put("from", strings[1].split("=")[1].replaceAll("\"", ""));
        params.put("to", strings[2].split("=")[1].replaceAll("\"", ""));
        params.put("signType", "v3");
        String curtime = String.valueOf(System.currentTimeMillis() / 1000);
        params.put("curtime", curtime);
        String signStr = APP_KEY + truncate(q) + salt + curtime + APP_SECRET;
        String sign = getDigest(signStr);
        params.put("appKey", APP_KEY);
        params.put("q", q);
        params.put("salt", salt);
        params.put("sign", sign);
        params.put("vocabId", "您的用户词表ID");
        /** 处理结果 */
        return requestForHttp(YOUDAO_URL, params);
    }

    public static String requestForHttp(String url, Map<String, String> params) {

        /** 创建HttpClient */
        CloseableHttpClient httpClient = HttpClients.createDefault();

        /** httpPost */
        HttpPost httpPost = new HttpPost(url);
        List<NameValuePair> paramsList = new ArrayList<>();
        Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> en = it.next();
            String key = en.getKey();
            String value = en.getValue();
            paramsList.add(new BasicNameValuePair(key, value));
        }
        CloseableHttpResponse httpResponse = null;
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(paramsList, "UTF-8"));
            httpResponse = httpClient.execute(httpPost);
            Header[] contentType = httpResponse.getHeaders("Content-Type");
            logger.info("Content-Type:" + contentType[0].getValue());
            if ("audio/mp3".equals(contentType[0].getValue())) {
                //如果响应是wav
                HttpEntity httpEntity = httpResponse.getEntity();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                httpResponse.getEntity().writeTo(baos);
                byte[] result = baos.toByteArray();
                EntityUtils.consume(httpEntity);
                if (result != null) {//合成成功
                    String file = "合成的音频存储路径" + System.currentTimeMillis() + ".mp3";
                    byte2File(result, file);
                }
                return "";
            } else {
                /** 响应不是音频流，直接显示结果 */
                HttpEntity httpEntity = httpResponse.getEntity();
                String json = EntityUtils.toString(httpEntity, "UTF-8");
                EntityUtils.consume(httpEntity);
                logger.info(json);
                System.out.println(json);
                JSONObject jsonObject = new JSONObject(json);
                JSONObject newJsonObject = new JSONObject();
                newJsonObject.put("translation", jsonObject.getJSONArray("translation").getString(0));
                if (jsonObject.has("basic")) {
                    newJsonObject.put("explains", jsonObject.getJSONObject("basic").getJSONArray("explains").getString(0));
                }
                return newJsonObject.toString();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (httpResponse != null) {
                    httpResponse.close();
                }
            } catch (IOException e) {
                logger.info("## release resouce error ##" + e);
            }
        }
    }

    /**
     * 生成加密字段
     */
    public static String getDigest(String string) {
        if (string == null) {
            return null;
        }
        char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        byte[] btInput = string.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest mdInst = MessageDigest.getInstance("SHA-256");
            mdInst.update(btInput);
            byte[] md = mdInst.digest();
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) {
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * @param result 音频字节流
     * @param file   存储路径
     */
    private static void byte2File(byte[] result, String file) {
        File audioFile = new File(file);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(audioFile);
            fos.write(result);

        } catch (Exception e) {
            logger.info(e.toString());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static String truncate(String q) {
        if (q == null) {
            return null;
        }
        int len = q.length();
        String result;
        return len <= 20 ? q : (q.substring(0, 10) + len + q.substring(len - 10, len));
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        //参数
        ResponseEntity<LoginDTO> result = restTemplate.getForEntity("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-web",
                LoginDTO.class, genLoginHeader());

        return result.getBody().getData().getQrcode_key();
    }

    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    public VideoClipDTO detail() {
        INeedAV iNeedAV = new INeedAV();
//		String avId = iNeedAV.getValidID("https://www.bilibili.com/video/BV1Ce411c7w7/?spm_id_from=333.999.top_right_bar_window_default_collection.content.click");
        String avId = iNeedAV.getValidID("https://www.bilibili.com/video/BV1MG4y1L7yy/?spm_id_from=333.999.0.0");
        assert (!(iNeedAV.getInputParser(avId).selectParser(avId) instanceof AbstractPageQueryParser));
        VideoInfo avInfo = iNeedAV.getVideoDetail(avId, Global.downloadFormat, false);
        System.out.println(avInfo);
        List<String> qnList = new ArrayList<>();

        for (ClipInfo cInfo : avInfo.getClips().values()) {
            for (final int qn : cInfo.getLinks().keySet()) {
                System.out.println("qnName: " + qn);

                // JButton btn = new JButton("清晰度: " + qn);
                String qnName = VideoQualityEnum.getQualityDescript(qn);
                if (qnName != null) {
                    qnList.add(qnName);
                } else {
                    qnList.add("清晰度: " + qn);
                }
            }
        }
        VideoClipDTO videoClipDTO = new VideoClipDTO();
        videoClipDTO.setImageUrl(avInfo.getVideoPreview());
        videoClipDTO.setBrief(avInfo.getBrief());
        videoClipDTO.setVideoName(avInfo.getVideoName());
        videoClipDTO.setQualityList(qnList);

        ClipInfo clip = (ClipInfo) avInfo.getClips().values().toArray()[0];
        DownloadRunnable downThread = new DownloadRunnable(avInfo, clip, 80);
        Global.queryThreadPool.execute(downThread);
        return videoClipDTO;
    }

    public HashMap<String, String> genLoginHeader() {
        HashMap<String, String> headers = new HttpHeaders().getBiliLoginAuthHeaders();
        String cookie = null;
        File fingerprint = new File("./config/fingerprint.config");
        if (fingerprint.exists()) {
            cookie = ResourcesUtil.readAll(fingerprint);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("_uuid=")
                    .append(ResourcesUtil.randomUpper(8)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(18)).append("infoc")
                    .append("; ");
            sb.append("b_lsid=")
                    .append(ResourcesUtil.randomUpper(8)).append("_")
                    .append(ResourcesUtil.randomUpper(11))
                    .append("; ");
            sb.append("b_nut=")
                    .append(System.currentTimeMillis() / 1000)
                    .append("; ");
            sb.append("b_timer=")
                    .append("%7B%22ffp%22%3A%7B%22333.130.fp.risk_")
                    .append(ResourcesUtil.randomUpper(8))
                    .append("%22%3A%22")
                    .append(ResourcesUtil.randomInt(10))
                    .append("A%22%7D%7D; ");
            sb.append("buvid3=")
                    .append(ResourcesUtil.randomUpper(8)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(17)).append("infoc")
                    .append("; ");
            sb.append("buvid4=")
                    .append(ResourcesUtil.randomInt(8)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(17)).append("-")
                    .append(ResourcesUtil.randomInt(9)).append("-")
                    .append(ResourcesUtil.randomLower(4)).append("/")
                    .append(ResourcesUtil.randomUpper(4)).append("+")
                    .append(ResourcesUtil.randomUpper(12)).append("%3D%3D")
                    .append("; ");
            sb.append("buvid_fp=")
                    .append(ResourcesUtil.randomUpper(8)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(4)).append("-")
                    .append(ResourcesUtil.randomUpper(17)).append("infoc")
                    .append("; ");
            sb.append("fingerprint=").append(ResourcesUtil.randomLower(32));
            cookie = sb.toString();
            ResourcesUtil.write(fingerprint, cookie);
        }
        cookie = cookie.replaceFirst("b_nut=[0-9]+", "b_nut=" + System.currentTimeMillis() / 1000);
        headers.put("Cookie", cookie);
        return headers;
    }
}