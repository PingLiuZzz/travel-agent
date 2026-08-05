package com.travel.agent.config;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.tools.FlightTool;
import com.travel.agent.infrastructure.tools.HotelTool;
import com.travel.agent.infrastructure.tools.WeatherTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 智能体装配（核心）。
 *
 * <p>使用 AiServices 把 LLM、Tools、Memory 组装为面向接口的 TravelAgent。 这是从"传统聊天机器人"跨向"自主智能体"的关键一步。
 *
 * <p>演进：
 *
 * <ul>
 *   <li>M1：chatModel + memory（基础对话）
 *   <li>M2：加入 tools（自主调用天气/航班/酒店）—— 当前阶段
 *   <li>M3：加入 contentRetriever（知识检索，需配置有效 Embedding Key 后启用）
 * </ul>
 */
@Configuration
public class AgentConfig {

  private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

  @Autowired private ChatModel chatModel;

  @Autowired private ChatMemoryProvider chatMemoryProvider;

  @Autowired private WeatherTool weatherTool;

  @Autowired private FlightTool flightTool;

  @Autowired private HotelTool hotelTool;

  @Autowired private EmbeddingStoreContentRetriever contentRetriever;

  // 构造器注入 + @Autowired 注解：依赖一目了然，便于测试
  /*@Autowired
  public AgentConfig(ChatModel chatModel,
                     ChatMemoryProvider chatMemoryProvider,
                     WeatherTool weatherTool,
                     FlightTool flightTool,
                     HotelTool hotelTool) {
      this.chatModel = chatModel;
      this.chatMemoryProvider = chatMemoryProvider;
      this.weatherTool = weatherTool;
      this.flightTool = flightTool;
      this.hotelTool = hotelTool;
  }*/

  @Bean
  public TravelAgent travelAgent() {
    String systemPrompt = loadSystemPrompt();
    return AiServices.builder(TravelAgent.class)
        .chatModel(chatModel)
        .chatMemoryProvider(chatMemoryProvider)
        .tools(weatherTool, flightTool, hotelTool)
        // M3 RAG 检索器：配置有效 Embedding API Key 后，
        // 注入 EmbeddingStoreContentRetriever 并取消下行注释即可启用知识检索
        .contentRetriever(contentRetriever)
        .systemMessageProvider(memoryId -> systemPrompt)
        .build();
  }

  /** 从 classpath 读取 System Prompt，便于不重启即可调整人设与约束。 */
  private String loadSystemPrompt() {
    try {
      return new ClassPathResource("prompts/travel-agent-system.txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("读取 System Prompt 失败，使用兜底提示词", e);
      return "你是一个专业的旅游出行助手，请基于工具与知识库为用户规划行程。";
    }
  }
}
