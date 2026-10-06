package com.projectAI.aiassistant.config;

import com.projectAI.aiassistant.tools.AssistantTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfiguration {
    @Bean
    ToolCallbackProvider assistantToolCallbacks(AssistantTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
