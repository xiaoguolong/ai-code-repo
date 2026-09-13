package com.aicode.patient.domain;

import com.aicode.patient.domain.model.ReActTurn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReAct 输出解析器单元测试。纯函数，不依赖网络。
 */
class ReActOutputParserTest {

    private final ReActOutputParser parser = new ReActOutputParser();

    @Test
    void parsesFinalAnswerWithAllFields() {
        String output = "Thought: 需要先收集患者信息\n"
                + "Action: 整理已知指标\n"
                + "Observation: 已获得血压与血糖\n"
                + "Final Answer: 建议定期随访";

        ReActTurn turn = parser.parse(output);

        assertThat(turn.finished()).isTrue();
        assertThat(turn.thought()).isEqualTo("需要先收集患者信息");
        assertThat(turn.action()).isEqualTo("整理已知指标");
        assertThat(turn.observation()).isEqualTo("已获得血压与血糖");
        assertThat(turn.answer()).isEqualTo("建议定期随访");
    }

    @Test
    void parsesNonFinalTurn() {
        String output = "Thought: 继续分析\nAction: 计算风险\nObservation: 风险中等";

        ReActTurn turn = parser.parse(output);

        assertThat(turn.finished()).isFalse();
        assertThat(turn.answer()).isNull();
        assertThat(turn.thought()).isEqualTo("继续分析");
        assertThat(turn.action()).isEqualTo("计算风险");
        assertThat(turn.observation()).isEqualTo("风险中等");
    }

    @Test
    void fillsEmptyForMissingFields() {
        ReActTurn turn = parser.parse("Final Answer: 只有结论");

        assertThat(turn.finished()).isTrue();
        assertThat(turn.thought()).isEmpty();
        assertThat(turn.action()).isEmpty();
        assertThat(turn.observation()).isEmpty();
        assertThat(turn.answer()).isEqualTo("只有结论");
    }

    @Test
    void treatsBlankInputAsUnfinished() {
        ReActTurn turn = parser.parse("   ");

        assertThat(turn.finished()).isFalse();
        assertThat(turn.answer()).isNull();
        assertThat(turn.thought()).isEmpty();
    }

    @Test
    void capturesMultiLineFinalAnswerUntilEnd() {
        String output = "Thought: t\nAction: a\nObservation: o\nFinal Answer: 第一行\n第二行\n第三行";

        ReActTurn turn = parser.parse(output);

        assertThat(turn.finished()).isTrue();
        assertThat(turn.answer()).isEqualTo("第一行\n第二行\n第三行");
    }
}
