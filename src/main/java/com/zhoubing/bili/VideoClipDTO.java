package com.zhoubing.bili;

import java.util.List;

public class VideoClipDTO {
    private String imageUrl;

    private String brief;

    private String videoName;

    private String title;

    private List<String> qualityList;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getQualityList() {
        return qualityList;
    }

    public void setQualityList(List<String> qualityList) {
        this.qualityList = qualityList;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

}
