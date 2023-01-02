package com.zhoubing.bili;

public class LoginDTO {
    private int code;
    private String message;
    private Data data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public class Data {
        private String qrcode_key;
        private String url;

        public String getQrcode_key() {
            return qrcode_key;
        }

        public void setQrcode_key(String qrcode_key) {
            this.qrcode_key = qrcode_key;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
