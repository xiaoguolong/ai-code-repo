package com.aicode.patient.domain;

import com.aicode.patient.domain.model.ReActTurn;

import java.util.List;

/**
 * ReAct 输出解析器（纯函数）。从模型文本输出中提取 Thought / Action / Observation / Final Answer。
 * 不依赖任何基础设施，可离线单测。
 */
public class ReActOutputParser {

    /** 识别顺序：值从当前标记开始，到下一个最早出现的标记结束。 */
    private static final List<String> MARKERS = List.of("Thought:", "Action:", "Observation:", "Final Answer:");

    /**
     * 解析模型输出。缺省字段返回空串；无 Final Answer 时 finished=false、answer=null。
     */
    public ReActTurn parse(String modelOutput) {
        String text = modelOutput == null ? "" : modelOutput.trim();
        String thought = extract(text, "Thought:");
        String action = extract(text, "Action:");
        String observation = extract(text, "Observation:");
        String answer = extract(text, "Final Answer:");
        boolean finished = text.contains("Final Answer:");
        return new ReActTurn(finished, thought, action, observation, finished ? answer : null);
    }

    private String extract(String text, String marker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int end = text.length();
        for (String candidate : MARKERS) {
            int pos = text.indexOf(candidate, valueStart);
            if (pos >= 0 && pos < end) {
                end = pos;
            }
        }
        return text.substring(valueStart, end).trim();
    }
}
