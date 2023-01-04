package com.zhoubing.bili;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.enums.VideoQualityEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.parsers.impl.AbstractPageQueryParser;
import nicelee.bilibili.util.HttpHeaders;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.ui.Global;
import nicelee.ui.item.ClipInfoPanel;
import nicelee.ui.thread.DownloadRunnable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/dataservice")
public class IndexController {
    RestTemplate restTemplate = new RestTemplate();

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
		String avId = iNeedAV.getValidID("https://www.bilibili.com/video/BV1hV4y1c74T/?spm_id_from=333.999.0.0&vd_source=51b0fedb987055bc000c8fbbc2021d5f");
		assert(!(iNeedAV.getInputParser(avId).selectParser(avId) instanceof AbstractPageQueryParser));
		VideoInfo avInfo = iNeedAV.getVideoDetail(avId, Global.downloadFormat, false);
		System.out.println(avInfo);
		List<String> qnList = new ArrayList<>();

		for(ClipInfo cInfo:  avInfo.getClips().values()) {
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

    public HashMap<String, String> genLoginHeader(){
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
			.append(System.currentTimeMillis()/1000)
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
		cookie = cookie.replaceFirst("b_nut=[0-9]+", "b_nut=" + System.currentTimeMillis()/1000);
		headers.put("Cookie", cookie);
		return headers;
	}
}