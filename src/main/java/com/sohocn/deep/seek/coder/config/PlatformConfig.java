package com.sohocn.deep.seek.coder.config;

import java.util.HashMap;
import java.util.Map;

import com.sohocn.deep.seek.coder.constant.AppConstant;

public class PlatformConfig {
    public Map<String, String> platformMap() {
        Map<String, String> map = new HashMap<>();

        map.put(AppConstant.DEEP_SEEK, "DeepSeek");
        map.put(AppConstant.SILICON_FLOW, "SiliconFlow(硅基流动)");

        return map;
    }

    public String applyUrlMap(String platform) {
        Map<String, String> map = new HashMap<>();

        map.put(AppConstant.DEEP_SEEK, "https://www.deepseek.com?from=DeepSeekCoder");
        map.put(AppConstant.SILICON_FLOW, "https://www.siliconflow.com?from=DeepSeekCoder");

        return map.get(platform);
    }

    public String apiUrlMap(String platform) {
        Map<String, String> map = new HashMap<>();

        map.put(AppConstant.DEEP_SEEK, "https://api.deepseek.com/v1/chat/completions");
        map.put(AppConstant.SILICON_FLOW, "https://api.siliconflow.cn/v1/chat/completions");

        return map.get(platform);
    }

    public String siliconFlowModelMap(String model) {
        Map<String, String> map = new HashMap<>();

        map.put("deepseek-chat", "deepseek-ai/DeepSeek-V3");
        map.put("deepseek-reasoner", "deepseek-ai/DeepSeek-R1");

        return map.get(model);
    }

    public Map<String, String> deepSeekModelMap() {
        Map<String, String> map = new HashMap<>();

        map.put("deepseek-chat", "DeepSeek-V3");
        map.put("deepseek-reasoner", "DeepSeek-R1");

        return map;
    }
}
