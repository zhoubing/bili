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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

@RestController
@RequestMapping("/api/dataservice")
public class IndexController {
    RestTemplate restTemplate = new RestTemplate();
    private static Logger logger = LoggerFactory.getLogger(IndexController.class);

    {
        // 读取配置文件
        ConfigUtil.initConfigs();

        if (Global.saveToRepo) {
            RepoUtil.init(false);
        }
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

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        //参数
        ResponseEntity<LoginDTO> result = restTemplate.getForEntity("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-web",
                LoginDTO.class, genLoginHeader());

        return result.getBody().getData().getQrcode_key();
    }
    private VideoInfo avInfo;

    @RequestMapping(value = "/download", method = RequestMethod.POST)
    public Result<?> download(@RequestBody String param) {
        JSONObject jsonObject = new JSONObject(param);
        String url = jsonObject.getString("url");
        INeedAV iNeedAV = new INeedAV();
        String avId = iNeedAV.getValidID(url);
        if (iNeedAV.getInputParser(avId).selectParser(avId) instanceof AbstractPageQueryParser) {
            Result<String> data = new Result<>();
            data.setCode(-2);
            return data;
        }
        if (Global.isLogin || !Global.needToLogin) {
            Result<String> data = new Result<>();
            data.setCode(-1);
            return data;
        }
//        assert (!(iNeedAV.getInputParser(avId).selectParser(avId) instanceof AbstractPageQueryParser));
        avInfo = iNeedAV.getVideoDetail(avId, Global.downloadFormat, false);
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
        try {
            DownloadRunnable downThread = new DownloadRunnable(avInfo, clip, 80);
            Global.queryThreadPool.execute(downThread);
        } catch (Exception e) {
            Result<String> data = new Result<>();
            data.setCode(-2);
            data.setMsg(e.getMessage());
            return data;
        }
        Result<String> data = new Result<>();
        data.setCode(0);
        return data;
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