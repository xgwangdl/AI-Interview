package com.guangge.Interview.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 记录模型调用元信息的顾问。
 *
 * 只输出消息条数、字符数与结果状态：请求里包含简历、回答转写等个人数据，
 * 整段 prompt / response 不应该进入日志。
 */
public class LoggingAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);

	@Override
	public String getName() {
		return "LoggingAdvisor";
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		logger.info("chat request messages={} chars={}", messageCount(request), contentLength(request));
		ChatClientResponse response = chain.nextCall(request);
		logger.info("chat response resultChars={}", resultLength(response));
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		logger.info("stream request messages={} chars={}", messageCount(request), contentLength(request));
		return chain.nextStream(request)
				.doOnComplete(() -> logger.info("stream response completed"));
	}

	private static int messageCount(ChatClientRequest request) {
		try {
			return request.prompt().getInstructions().size();
		} catch (RuntimeException exception) {
			return -1;
		}
	}

	private static long contentLength(ChatClientRequest request) {
		try {
			String contents = request.prompt().getContents();
			return contents == null ? 0 : contents.length();
		} catch (RuntimeException exception) {
			return -1;
		}
	}

	private static long resultLength(ChatClientResponse response) {
		try {
			if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
				return 0;
			}
			String text = response.chatResponse().getResult().getOutput().getText();
			return text == null ? 0 : text.length();
		} catch (RuntimeException exception) {
			return -1;
		}
	}
}
